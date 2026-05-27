"""
T2 오픈액세스 학술 자료 수집.

OpenAlex /works 로 category 별 상위 피인용 OA 논문을 검색하고,
Europe PMC fulltext XML 우선, 없으면 Unpaywall PDF 로 본문을 확보해
`corpus/t2-oa/{doi-slug}.md` + `{doi-slug}.json` 로 저장한다.

CLI: python -m rag_indexer.fetch_oa [--per-category 5]
"""

from __future__ import annotations

import argparse
import io
import json
import logging
import os
import re
from pathlib import Path
from typing import Iterable

import httpx
from dotenv import load_dotenv
from lxml import etree
from pypdf import PdfReader

log = logging.getLogger("rag_indexer.fetch_oa")

OPENALEX_BASE = "https://api.openalex.org"
EUROPEPMC_BASE = "https://www.ebi.ac.uk/europepmc/webservices/rest"
UNPAYWALL_BASE = "https://api.unpaywall.org/v2"

# 설계 문서 §4.1.2 의 OpenAlex 쿼리 정의.
CATEGORY_QUERIES: dict[str, str] = {
    "trunk_lean": 'running "trunk lean"',
    "initial_knee_flexion": 'running "initial knee flexion" landing',
    "foot_strike_pattern": 'running "foot strike" (rearfoot OR midfoot OR forefoot)',
}

# OpenAlex 표준 필터만 사용. PMCID 보유 여부는 코드에서 응답의 ids.pmcid 로 확인.
# (Unpaywall 폴백은 publisher 사이트가 403/HTML 반환하는 경우가 많아 신뢰도 낮음)
OPENALEX_COMMON_FILTER = "open_access.is_oa:true,type:article,language:en"

# 본문 확보율이 낮으므로 후보 풀을 넉넉히 받음 (per_category * 후보 배수).
OPENALEX_CANDIDATE_MULTIPLIER = 8

OUT_DIR = Path(__file__).resolve().parent.parent / "corpus" / "t2-oa"


def slugify_doi(doi: str) -> str:
    s = doi.lower().replace("https://doi.org/", "")
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def search_openalex(client: httpx.Client, query: str, per_page: int) -> list[dict]:
    params = {
        "search": query,
        "filter": OPENALEX_COMMON_FILTER,
        "sort": "cited_by_count:desc",
        "per-page": per_page,
    }
    r = client.get(f"{OPENALEX_BASE}/works", params=params, timeout=30)
    r.raise_for_status()
    return r.json().get("results", [])


def fetch_europepmc_xml(client: httpx.Client, pmcid: str) -> str | None:
    """Europe PMC 의 PMC ID 로 fulltext XML 을 직접 받는다."""
    xml_url = f"{EUROPEPMC_BASE}/{pmcid}/fullTextXML"
    xr = client.get(xml_url, timeout=60)
    if xr.status_code == 200 and xr.text.strip().startswith("<"):
        return xr.text
    return None


def _extract_pmcid(work: dict) -> str | None:
    """OpenAlex Work 의 ids.pmcid (URL 또는 PMCxxxxx) 에서 PMC ID 만 뽑아낸다."""
    raw = (work.get("ids") or {}).get("pmcid")
    if not raw:
        return None
    raw = raw.rsplit("/", 1)[-1]
    return raw if raw.startswith("PMC") else None


def fetch_unpaywall_pdf(client: httpx.Client, doi: str, email: str) -> bytes | None:
    r = client.get(f"{UNPAYWALL_BASE}/{doi}", params={"email": email}, timeout=30)
    if r.status_code != 200:
        return None
    loc = r.json().get("best_oa_location") or {}
    pdf_url = loc.get("url_for_pdf")
    if not pdf_url:
        return None
    pr = client.get(pdf_url, timeout=120, follow_redirects=True)
    if pr.status_code == 200 and pr.headers.get("content-type", "").startswith("application/pdf"):
        return pr.content
    return None


def extract_text_from_xml(xml: str) -> str:
    try:
        root = etree.fromstring(xml.encode("utf-8") if isinstance(xml, str) else xml)
    except etree.XMLSyntaxError:
        return ""
    body = root.find(".//{*}body")
    if body is None:
        body = root.find(".//body")
    target = body if body is not None else root
    parts: list[str] = []
    # sec/abstract 는 컨테이너라 itertext() 가 자식 텍스트를 중복 산출한다.
    # 리프성 노드인 p/title 만 채집.
    for el in target.iter():
        if not isinstance(el.tag, str):
            continue  # Comment, ProcessingInstruction 등
        tag = etree.QName(el).localname
        if tag in {"p", "title"}:
            text = "".join(el.itertext()).strip()
            if text:
                parts.append(text)
    return "\n\n".join(parts)


def extract_text_from_pdf(data: bytes) -> str:
    reader = PdfReader(io.BytesIO(data))
    parts = []
    for page in reader.pages:
        try:
            parts.append(page.extract_text() or "")
        except Exception as e:  # noqa: BLE001
            log.warning("PDF page extract failed: %s", e)
    return "\n\n".join(p.strip() for p in parts if p.strip())


def write_doc(category: str, work: dict, body_text: str, source_kind: str) -> Path:
    doi = (work.get("doi") or "").replace("https://doi.org/", "")
    slug = slugify_doi(doi) if doi else f"openalex-{work['id'].rsplit('/', 1)[-1]}"
    md_path = OUT_DIR / f"{slug}.md"
    json_path = OUT_DIR / f"{slug}.json"

    title = work.get("title") or "(untitled)"
    md_path.write_text(f"# {title}\n\n{body_text}\n", encoding="utf-8")

    license_id = (work.get("primary_location") or {}).get("license") or (
        work.get("best_oa_location") or {}
    ).get("license")
    meta = {
        "doc_id": slug,
        "category": category,
        "tier": "t2",
        "title": title,
        "doi": doi or None,
        "source": "openalex",
        "source_url": work.get("doi") or work.get("id"),
        "license": license_id,
        "oa_status": (work.get("open_access") or {}).get("oa_status"),
        "cited_by_count": work.get("cited_by_count"),
        "body_source": source_kind,  # "europepmc-xml" | "unpaywall-pdf"
        "lang": "en",
    }
    json_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    return md_path


def collect(per_category: int) -> Iterable[Path]:
    load_dotenv()
    unpaywall_email = os.environ.get("UNPAYWALL_EMAIL", "").strip()
    if not unpaywall_email:
        log.warning("UNPAYWALL_EMAIL 미설정 — Unpaywall 폴백 비활성. Europe PMC XML 만 사용.")
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    saved_dois: set[str] = set()
    with httpx.Client(headers={"User-Agent": "rag-indexer/0.1 (capstone)"}) as client:
        for category, query in CATEGORY_QUERIES.items():
            log.info("OpenAlex search: category=%s query=%r", category, query)
            works = search_openalex(
                client, query, per_page=per_category * OPENALEX_CANDIDATE_MULTIPLIER
            )
            saved = 0
            for work in works:
                if saved >= per_category:
                    break
                doi = (work.get("doi") or "").replace("https://doi.org/", "")
                if not doi or doi in saved_dois:
                    continue
                body, kind = "", ""
                pmcid = _extract_pmcid(work)
                if pmcid:
                    xml = fetch_europepmc_xml(client, pmcid)
                    if xml:
                        body = extract_text_from_xml(xml)
                        kind = "europepmc-xml"
                if not body and unpaywall_email:
                    pdf = fetch_unpaywall_pdf(client, doi, unpaywall_email)
                    if pdf:
                        body = extract_text_from_pdf(pdf)
                        kind = "unpaywall-pdf"
                if not body:
                    log.info("본문 미확보, skip: doi=%s pmcid=%s", doi, pmcid)
                    continue
                path = write_doc(category, work, body, kind)
                saved_dois.add(doi)
                saved += 1
                log.info("saved %s (%s, %d chars, %s)", path.name, category, len(body), kind)
                yield path
            if saved < per_category:
                log.warning("category=%s 목표 %d편 중 %d편만 수집", category, per_category, saved)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--per-category", type=int, default=5)
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    for _ in collect(args.per_category):
        pass


if __name__ == "__main__":
    main()

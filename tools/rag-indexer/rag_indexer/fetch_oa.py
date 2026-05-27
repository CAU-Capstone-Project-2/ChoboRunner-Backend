"""
T2 오픈액세스 학술 자료 수집.

OpenAlex /works (cursor 페이지네이션) 로 category 별 상위 피인용 OA 논문을 검색하고,
Europe PMC fulltext XML 우선, 없으면 Unpaywall PDF 로 본문을 확보해
`corpus/t2-oa/{doi-slug}.md` + `{doi-slug}.json` 로 저장한다.

본문 추출 단계에서 페이지 번호/footer 같은 짧은 줄과 References 이후 단락을 제거한다.

CLI: python -m rag_indexer.fetch_oa [--per-category 25]
"""

from __future__ import annotations

import argparse
import io
import json
import logging
import os
import re
from pathlib import Path
from typing import Iterable, Iterator

import httpx
from dotenv import load_dotenv
from lxml import etree
from pypdf import PdfReader

log = logging.getLogger("rag_indexer.fetch_oa")

OPENALEX_BASE = "https://api.openalex.org"
EUROPEPMC_BASE = "https://www.ebi.ac.uk/europepmc/webservices/rest"
UNPAYWALL_BASE = "https://api.unpaywall.org/v2"

# 설계 문서 §4.1.2 의 OpenAlex 쿼리 정의.
# - initial_knee_flexion / trunk_lean 은 phrase 단독으로 OA 본문 확보가 적어 동의어 OR 로 풀을 넓힌다.
# - trunk_lean 은 cited 정렬이 change-of-direction lean 같은 인접 주제를 상위로 끌어올려
#   relevance_score:desc 로 정렬을 바꾼다 (CATEGORY_SORTS).
CATEGORY_QUERIES: dict[str, str] = {
    # trunk_lean: 러닝 한정 영문 OA paper 가 매우 적어, running 한정자를 풀고
    # 자세 일반 phrase OR 로 풀을 확장한다 (보행/일상 자세 자료도 trunk lean 일반 원리에 기여).
    # relevance_score:desc 정렬(CATEGORY_SORTS)이 매칭 점수 높은 paper 위주로 추려준다.
    "trunk_lean": (
        '"trunk lean" OR "trunk forward lean" OR "forward trunk lean" '
        'OR "anterior trunk tilt" OR "trunk inclination" OR "forward leaning posture" '
        'OR "trunk lean angle"'
    ),
    "initial_knee_flexion": (
        'running ("initial knee flexion" OR "knee flexion at initial contact" '
        'OR "knee flexion at landing")'
    ),
    "foot_strike_pattern": 'running "foot strike" (rearfoot OR midfoot OR forefoot)',
}

DEFAULT_SORT = "cited_by_count:desc"

# 카테고리별 정렬 오버라이드. trunk_lean 은 cited 정렬이 무관 분야의 cited 상위 paper 를
# 끌어올려 relevance_score:desc 로 좁힌다 (수량 ↓, 정확도 ↑).
CATEGORY_SORTS: dict[str, str] = {
    "trunk_lean": "relevance_score:desc",
}

# OpenAlex 표준 필터만 사용. PMCID 보유 여부는 코드에서 응답의 ids.pmcid 로 확인.
OPENALEX_COMMON_FILTER = "open_access.is_oa:true,type:article,language:en"

# 본문 확보율이 낮으므로 후보 풀을 넉넉히 받음 (per_category * 후보 배수).
OPENALEX_CANDIDATE_MULTIPLIER = 8

# cursor 페이지네이션에서 per-page 최대 200.
OPENALEX_PER_PAGE_MAX = 200

# 본문 끝에서 잘라낼 후속 섹션 헤딩 패턴.
REFERENCES_HEADING_RE = re.compile(
    r"^\s*(References|REFERENCES|Reference\s+List|Literature\s+Cited|Bibliography|참고문헌)\s*$",
    re.MULTILINE,
)

# XML 본문에서 통째로 들어내는 노드 (참고문헌/부록/푸트노트/표).
XML_DROP_TAGS = {"ref-list", "back", "fn-group", "table-wrap"}

OUT_DIR = Path(__file__).resolve().parent.parent / "corpus" / "t2-oa"


def slugify_doi(doi: str) -> str:
    s = doi.lower().replace("https://doi.org/", "")
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def iter_openalex_works(
    client: httpx.Client,
    query: str,
    max_candidates: int,
    sort: str = DEFAULT_SORT,
) -> Iterator[dict]:
    """cursor 페이지네이션으로 OpenAlex works 를 max_candidates 까지 순회."""
    cursor: str | None = "*"
    received = 0
    while cursor and received < max_candidates:
        per_page = min(OPENALEX_PER_PAGE_MAX, max_candidates - received)
        params = {
            "search": query,
            "filter": OPENALEX_COMMON_FILTER,
            "sort": sort,
            "per-page": per_page,
            "cursor": cursor,
        }
        r = client.get(f"{OPENALEX_BASE}/works", params=params, timeout=30)
        r.raise_for_status()
        data = r.json()
        results = data.get("results") or []
        if not results:
            return
        for work in results:
            received += 1
            yield work
            if received >= max_candidates:
                return
        cursor = (data.get("meta") or {}).get("next_cursor")


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


def _trim_references(text: str) -> str:
    """본문 끝의 References/Bibliography 헤딩 이후를 잘라낸다 (검색 노이즈 감소)."""
    m = REFERENCES_HEADING_RE.search(text)
    if m and m.start() > len(text) * 0.4:  # 본문 앞쪽의 우연한 매칭 방지
        return text[: m.start()].rstrip()
    return text


def _clean_pdf_lines(raw: str) -> list[str]:
    """페이지 번호·짧은 footer/header 줄 제거."""
    out: list[str] = []
    for line in raw.splitlines():
        s = line.strip()
        if not s:
            continue
        if re.fullmatch(r"\d+", s):
            continue
        # 짧고 숫자/기호 위주의 줄 (페이지번호, "Page X of Y", 구분선 등)
        if len(s) < 30 and re.fullmatch(r"[\d\s\-/·.|·•]+", s):
            continue
        if re.fullmatch(r"(?i)page\s+\d+(\s+of\s+\d+)?", s):
            continue
        out.append(s)
    return out


def extract_text_from_xml(xml: str) -> str:
    try:
        root = etree.fromstring(xml.encode("utf-8") if isinstance(xml, str) else xml)
    except etree.XMLSyntaxError:
        return ""
    # ref-list/back/fn-group/table-wrap 등 노이즈성 노드를 통째로 들어낸다.
    to_remove = []
    for el in root.iter():
        if not isinstance(el.tag, str):
            continue
        if etree.QName(el).localname in XML_DROP_TAGS:
            to_remove.append(el)
    for el in to_remove:
        parent = el.getparent()
        if parent is not None:
            parent.remove(el)

    body = root.find(".//{*}body")
    if body is None:
        body = root.find(".//body")
    target = body if body is not None else root
    parts: list[str] = []
    # sec/abstract 는 컨테이너라 itertext() 가 자식 텍스트를 중복 산출한다.
    # 리프성 노드인 p/title 만 채집.
    for el in target.iter():
        if not isinstance(el.tag, str):
            continue
        tag = etree.QName(el).localname
        if tag in {"p", "title"}:
            text = "".join(el.itertext()).strip()
            if text:
                parts.append(text)
    return "\n\n".join(parts)


def extract_text_from_pdf(data: bytes) -> str:
    reader = PdfReader(io.BytesIO(data))
    pages_text: list[str] = []
    for page in reader.pages:
        try:
            raw = page.extract_text() or ""
        except Exception as e:  # noqa: BLE001
            log.warning("PDF page extract failed: %s", e)
            continue
        lines = _clean_pdf_lines(raw)
        if lines:
            pages_text.append("\n".join(lines))
    full = "\n\n".join(pages_text)
    return _trim_references(full)


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


def collect(per_category: int, categories: list[str] | None = None) -> Iterable[Path]:
    load_dotenv()
    unpaywall_email = os.environ.get("UNPAYWALL_EMAIL", "").strip()
    if not unpaywall_email:
        log.warning("UNPAYWALL_EMAIL 미설정 — Unpaywall 폴백 비활성. Europe PMC XML 만 사용.")
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    selected = (
        {k: CATEGORY_QUERIES[k] for k in categories if k in CATEGORY_QUERIES}
        if categories
        else CATEGORY_QUERIES
    )
    if not selected:
        log.warning("선택된 카테고리가 없습니다. 입력: %s", categories)
        return

    saved_dois: set[str] = set()
    candidate_target = per_category * OPENALEX_CANDIDATE_MULTIPLIER
    with httpx.Client(headers={"User-Agent": "rag-indexer/0.1 (capstone)"}) as client:
        for category, query in selected.items():
            sort = CATEGORY_SORTS.get(category, DEFAULT_SORT)
            log.info(
                "OpenAlex search: category=%s sort=%s query=%r target=%d candidates=%d",
                category, sort, query, per_category, candidate_target,
            )
            saved = 0
            for work in iter_openalex_works(client, query, candidate_target, sort=sort):
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
    parser.add_argument("--per-category", type=int, default=25)
    parser.add_argument(
        "--categories",
        default=None,
        help="콤마 구분 카테고리 (기본: 전체). 예: trunk_lean,foot_strike_pattern",
    )
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    categories = [c.strip() for c in args.categories.split(",")] if args.categories else None
    for _ in collect(args.per_category, categories):
        pass


if __name__ == "__main__":
    main()

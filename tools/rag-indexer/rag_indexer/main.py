"""
end-to-end pipeline: T1 PDF + T2 OA md/meta -> chunk -> embed -> Pinecone upsert.

CLI:
  python -m rag_indexer.main --tier all --namespace v1
  python -m rag_indexer.main --tier t1
  python -m rag_indexer.main --tier t2
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
from pathlib import Path
from typing import Iterable

from pypdf import PdfReader

from .chunk import Chunk, chunk_text
from .embed import embed_batch
from .upsert import DEFAULT_NAMESPACE, reset_namespace, upsert

log = logging.getLogger("rag_indexer.main")

BASE_DIR = Path(__file__).resolve().parent.parent
T1_DIR = BASE_DIR / "corpus" / "t1-public"
T2_DIR = BASE_DIR / "corpus" / "t2-oa"

# T1 PDF 파일 -> 어떤 category 로 분류할지. 단일 PDF 가 3 metric 모두를 다루므로 general.
T1_DEFAULT_META = {
    "category": "general",
    "tier": "t1",
    "source": "kor-running-guideline",
    "lang": "ko",
    "license": "internal",
}


def _vector_id(doc_id: str, chunk_index: int) -> str:
    raw = f"{doc_id}#{chunk_index}".encode("utf-8")
    return hashlib.sha1(raw).hexdigest()


def _build_vector(doc_id: str, chunk: Chunk, base_meta: dict, embedding: list[float]) -> dict:
    meta = {**base_meta, "tone": chunk.tone, "text": chunk.text}
    meta = {k: v for k, v in meta.items() if v is not None}
    return {
        "id": _vector_id(doc_id, chunk.index),
        "values": embedding,
        "metadata": meta,
    }


def _read_pdf(path: Path) -> str:
    reader = PdfReader(str(path))
    parts = []
    for page in reader.pages:
        try:
            parts.append(page.extract_text() or "")
        except Exception as e:  # noqa: BLE001
            log.warning("PDF page extract failed (%s): %s", path.name, e)
    return "\n\n".join(p.strip() for p in parts if p.strip())


def iter_t1_docs() -> Iterable[tuple[str, str, dict]]:
    if not T1_DIR.exists():
        return
    for pdf in sorted(T1_DIR.glob("*.pdf")):
        text = _read_pdf(pdf)
        if not text:
            log.warning("T1 PDF 비어있음, skip: %s", pdf.name)
            continue
        doc_id = pdf.stem
        meta = {**T1_DEFAULT_META, "doc_id": doc_id, "title": pdf.stem}
        yield doc_id, text, meta


def iter_t2_docs() -> Iterable[tuple[str, str, dict]]:
    if not T2_DIR.exists():
        return
    for md in sorted(T2_DIR.glob("*.md")):
        json_path = md.with_suffix(".json")
        if not json_path.exists():
            log.warning("T2 메타 누락, skip: %s", md.name)
            continue
        meta = json.loads(json_path.read_text(encoding="utf-8"))
        text = md.read_text(encoding="utf-8")
        if not text.strip():
            continue
        yield meta["doc_id"], text, meta


def process(docs: Iterable[tuple[str, str, dict]], namespace: str) -> int:
    total_chunks = 0
    for doc_id, text, base_meta in docs:
        chunks = chunk_text(text)
        if not chunks:
            log.warning("chunk 0 — skip doc=%s", doc_id)
            continue
        embeddings = embed_batch([c.text for c in chunks])
        vectors = [
            _build_vector(doc_id, c, base_meta, emb) for c, emb in zip(chunks, embeddings)
        ]
        upserted = upsert(vectors, namespace=namespace)
        log.info("doc=%s chunks=%d upserted=%d", doc_id, len(chunks), upserted)
        total_chunks += upserted
    return total_chunks


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tier", choices=["t1", "t2", "all"], default="all")
    parser.add_argument("--namespace", default=DEFAULT_NAMESPACE)
    parser.add_argument("--reset", action="store_true",
                        help="upsert 전에 namespace 의 기존 벡터를 모두 삭제")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    if args.reset:
        log.info("=== namespace '%s' reset ===", args.namespace)
        reset_namespace(args.namespace)

    total = 0
    if args.tier in {"t1", "all"}:
        log.info("=== T1 처리 시작 ===")
        total += process(iter_t1_docs(), args.namespace)
    if args.tier in {"t2", "all"}:
        log.info("=== T2 처리 시작 ===")
        total += process(iter_t2_docs(), args.namespace)
    log.info("완료. 총 upsert 벡터 수=%d", total)


if __name__ == "__main__":
    main()

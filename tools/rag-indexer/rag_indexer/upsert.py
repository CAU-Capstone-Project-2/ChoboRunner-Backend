"""
Pinecone upsert.

설계 §4.3 / §8: 인덱스 capstone2-posture-analyze-rag, namespace=v1,
메타데이터에 text/category/tier/tone/title/doi/license/source/source_url/lang 동봉.
"""

from __future__ import annotations

import logging
import os
from typing import Iterable

from dotenv import load_dotenv
from pinecone import Pinecone

log = logging.getLogger("rag_indexer.upsert")

BATCH = 100
DEFAULT_INDEX_NAME = "capstone2-posture-analyze-rag"
DEFAULT_NAMESPACE = "v1"


_index = None


def _get_index():
    global _index
    if _index is not None:
        return _index
    load_dotenv()
    api_key = os.environ.get("PINECONE_API_KEY")
    if not api_key:
        raise RuntimeError("PINECONE_API_KEY 가 설정되지 않았습니다.")
    pc = Pinecone(api_key=api_key)
    host = os.environ.get("PINECONE_INDEX_HOST", "").strip()
    name = os.environ.get("PINECONE_INDEX_NAME", DEFAULT_INDEX_NAME)
    _index = pc.Index(host=host) if host else pc.Index(name)
    return _index


def reset_namespace(namespace: str = DEFAULT_NAMESPACE) -> None:
    """namespace 의 모든 벡터를 삭제. 인덱스/네임스페이스 미존재는 무시."""
    index = _get_index()
    try:
        index.delete(delete_all=True, namespace=namespace)
        log.info("namespace '%s' 전체 삭제 완료", namespace)
    except Exception as e:  # noqa: BLE001
        log.warning("namespace '%s' 삭제 실패 (없는 namespace 일 수 있음): %s", namespace, e)


def upsert(vectors: Iterable[dict], namespace: str = DEFAULT_NAMESPACE) -> int:
    """vectors: [{id, values, metadata}, ...]. 반환: upsert 한 벡터 수."""
    index = _get_index()
    buf: list[dict] = []
    total = 0
    for vec in vectors:
        buf.append(vec)
        if len(buf) >= BATCH:
            index.upsert(vectors=buf, namespace=namespace)
            total += len(buf)
            log.info("upserted batch (total=%d)", total)
            buf.clear()
    if buf:
        index.upsert(vectors=buf, namespace=namespace)
        total += len(buf)
        log.info("upserted final batch (total=%d)", total)
    return total

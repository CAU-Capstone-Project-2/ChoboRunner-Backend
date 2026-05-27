"""
OpenAI embeddings 배치 호출.

설계 §7: text-embedding-3-large (3072 dim).
"""

from __future__ import annotations

import logging
import os
from typing import Iterable

from dotenv import load_dotenv
from openai import OpenAI

log = logging.getLogger("rag_indexer.embed")

MODEL = "text-embedding-3-large"
BATCH = 100


_client: OpenAI | None = None


def _get_client() -> OpenAI:
    global _client
    if _client is None:
        load_dotenv()
        api_key = os.environ.get("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("OPENAI_API_KEY 가 설정되지 않았습니다.")
        _client = OpenAI(api_key=api_key)
    return _client


def embed_batch(texts: list[str], model: str = MODEL) -> list[list[float]]:
    """texts 순서를 보존하며 OpenAI embeddings 호출. 빈 입력은 빈 리스트."""
    if not texts:
        return []
    client = _get_client()
    out: list[list[float]] = []
    for start in range(0, len(texts), BATCH):
        sub = texts[start : start + BATCH]
        log.info("embedding batch %d..%d (size=%d)", start, start + len(sub), len(sub))
        resp = client.embeddings.create(model=model, input=sub)
        # OpenAI 응답은 input 순서와 동일
        out.extend(d.embedding for d in resp.data)
    return out


def embed_iter(texts: Iterable[str], model: str = MODEL) -> list[list[float]]:
    return embed_batch(list(texts), model=model)

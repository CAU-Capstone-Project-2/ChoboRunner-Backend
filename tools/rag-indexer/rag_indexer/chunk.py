"""
문서 -> chunk[] + tone 라벨링.

설계 §4.1.3: 임상 단락은 키워드 휴리스틱으로 `clinical` 라벨,
나머지는 `coaching`. Retriever 에서 `coaching` 만 필터한다.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

import tiktoken

TARGET_TOKENS = 400
OVERLAP_TOKENS = 80

# 휴리스틱 키워드 — 의심되면 clinical 로 분류 (보수적 라벨링).
CLINICAL_PATTERNS = [
    r"\binjur(?:y|ies|ed)\b",
    r"\bpatient(?:s)?\b",
    r"\btreatment\b",
    r"\btherap(?:y|ies|eutic)\b",
    r"\bclinical\b",
    r"\brehabilitat(?:e|ion)\b",
    r"\bdiagnos(?:e|is|tic)\b",
    r"\bsymptom(?:s)?\b",
    r"\bsyndrome\b",
    r"\bpain\b",
    r"\bsurg(?:ery|ical)\b",
    r"\bpatellofemoral\b",
    r"\btendinopath(?:y|ies)\b",
]
CLINICAL_RE = re.compile("|".join(CLINICAL_PATTERNS), flags=re.IGNORECASE)

_ENC = tiktoken.get_encoding("cl100k_base")


@dataclass
class Chunk:
    text: str
    tone: str  # "coaching" | "clinical"
    index: int


def _split_paragraphs(text: str) -> list[str]:
    paras = re.split(r"\n{2,}", text)
    return [p.strip() for p in paras if p.strip()]


def _pack(paragraphs: list[str], target: int, overlap: int) -> list[str]:
    """문단 단위로 모아 target 토큰 근처에서 자르고, overlap 토큰만큼 슬라이딩."""
    chunks: list[str] = []
    buf: list[str] = []
    buf_tokens = 0
    for para in paragraphs:
        ptoks = len(_ENC.encode(para))
        if buf and buf_tokens + ptoks > target:
            chunks.append("\n\n".join(buf))
            # overlap: 마지막 문단을 다음 chunk 시작으로
            if overlap > 0 and buf:
                tail = buf[-1]
                tail_toks = len(_ENC.encode(tail))
                if tail_toks <= overlap * 2:
                    buf = [tail]
                    buf_tokens = tail_toks
                else:
                    buf, buf_tokens = [], 0
            else:
                buf, buf_tokens = [], 0
        buf.append(para)
        buf_tokens += ptoks
    if buf:
        chunks.append("\n\n".join(buf))
    return chunks


def label_tone(chunk_text: str) -> str:
    return "clinical" if CLINICAL_RE.search(chunk_text) else "coaching"


def chunk_text(
    text: str,
    target_tokens: int = TARGET_TOKENS,
    overlap_tokens: int = OVERLAP_TOKENS,
) -> list[Chunk]:
    paragraphs = _split_paragraphs(text)
    raw = _pack(paragraphs, target_tokens, overlap_tokens)
    return [Chunk(text=t, tone=label_tone(t), index=i) for i, t in enumerate(raw)]

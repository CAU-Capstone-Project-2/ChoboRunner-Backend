"""
청크 본문 -> metric category 분류.

paper-level 카테고리(`base_meta.category`)는 OpenAlex 검색 쿼리 기준이라
한 페이퍼 내 청크가 모두 같은 라벨을 받는다. 그러나 실제로는 페이퍼 내
methodology / discussion / 무관 단락이 섞여 있어 잘못된 카테고리로
끌려와 검색 정확도를 떨어뜨린다. 청크별 LLM 분류로 이를 보정한다.

반환 라벨:
- trunk_lean / initial_knee_flexion / foot_strike_pattern: metric 특정
- general: 러닝 폼/바이오메카닉 일반 (세 metric 어디나 공통 적용)
- drop: 러닝 자세와 무관 (예: 통계 방법, 참고문헌, 뇌성마비/축구 점프
        등 러닝 외 분야). 인덱싱 단계에서 제외.
"""

from __future__ import annotations

import logging
import os

from dotenv import load_dotenv
from openai import OpenAI

log = logging.getLogger("rag_indexer.classify")

MODEL = "gpt-5.4-mini-2026-03-17"

VALID_LABELS = {
    "trunk_lean",
    "initial_knee_flexion",
    "foot_strike_pattern",
    "general",
    "drop",
}

SYSTEM_PROMPT = """You classify a text chunk from a sports-biomechanics paper for use as RAG context in a distance-running posture-coaching app.

Output exactly one token, lowercase, snake_case, no punctuation:
- trunk_lean : chunk discusses trunk forward lean / trunk inclination / forward trunk posture (running or other dynamic lower-body movement; principles transfer)
- initial_knee_flexion : chunk discusses knee flexion at landing / initial contact (running, hop landing, jump landing, drop landing all qualify — they share landing-impact mechanics)
- foot_strike_pattern : chunk discusses foot strike patterns (rearfoot/midfoot/forefoot, heel/toe strike), foot contact mechanics
- general : chunk discusses lower-body gait/running biomechanics, kinematics, kinetics, ground reaction forces, muscle activation, or coaching principles that are useful background even if not metric-specific. Walking-gait principles applicable to running posture also fit here.
- drop : ONLY if chunk has no usable biomechanical content for running posture coaching. Examples: bare page-number/journal-header bleed-through from PDF extraction, conflict-of-interest / ethics-approval boilerplate, pure statistics description (ANOVA, p-values without biomechanical interpretation), reference-list entries, equipment-only descriptions, IRB consent text, paper-title duplications, completely unrelated topics (e.g., back-shape static measurement, surgery outcomes without movement data).

Lean toward 'general' for borderline cases that contain ANY biomechanical signal. Reserve 'drop' for content that is functionally noise."""

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


def classify_chunk(text: str, model: str = MODEL) -> str:
    """단일 청크 분류. 라벨 인식 실패 시 'drop' (보수적 제외)."""
    if not text or not text.strip():
        return "drop"
    client = _get_client()
    # 청크가 너무 길면 앞 3000자만 — 토픽 판별엔 충분.
    snippet = text if len(text) <= 3000 else text[:3000]
    # gpt-5.x: max_tokens 대신 max_completion_tokens, temperature 1.0 만 허용.
    # 분류는 reasoning 필요 없으므로 reasoning_effort='none' 으로 끔 (latency 1초대, 비용 절감).
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": snippet},
        ],
        max_completion_tokens=20,
        reasoning_effort="none",
    )
    raw = (resp.choices[0].message.content or "").strip().lower()
    # 모델이 마침표/공백/따옴표를 붙일 수 있음 — 앞 토큰만 추출.
    label = raw.split()[0].strip(".,'\"`") if raw else "drop"
    if label not in VALID_LABELS:
        log.warning("classify: 알 수 없는 라벨 %r -> drop 처리 (snippet=%.60s)", raw, snippet)
        return "drop"
    return label

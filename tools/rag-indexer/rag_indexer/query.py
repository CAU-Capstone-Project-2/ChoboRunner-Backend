"""
로컬 검색 정성 검증.

CLI: python -m rag_indexer.query "<질의>" [--top-k 4] [--tone coaching]
"""

from __future__ import annotations

import argparse
import logging

from .embed import embed_batch
from .upsert import DEFAULT_NAMESPACE, _get_index


def query(text: str, top_k: int = 4, tone: str | None = "coaching",
          category: str | None = None, namespace: str = DEFAULT_NAMESPACE) -> list[dict]:
    vec = embed_batch([text])[0]
    flt: dict = {}
    if tone:
        flt["tone"] = tone
    if category:
        flt["category"] = category
    index = _get_index()
    res = index.query(
        vector=vec,
        top_k=top_k,
        namespace=namespace,
        include_metadata=True,
        filter=flt or None,
    )
    return res.get("matches", []) if isinstance(res, dict) else res.matches  # type: ignore[union-attr]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("text", help="검색 질의 (한국어/영어 모두 가능)")
    parser.add_argument("--top-k", type=int, default=4)
    parser.add_argument("--tone", default="coaching",
                        help="metadata.tone 필터 (기본 coaching, 비활성: '')")
    parser.add_argument("--category", default=None,
                        help="metadata.category 필터 (trunk_lean/initial_knee_flexion/foot_strike_pattern)")
    parser.add_argument("--namespace", default=DEFAULT_NAMESPACE)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(message)s")

    matches = query(
        text=args.text,
        top_k=args.top_k,
        tone=args.tone or None,
        category=args.category,
        namespace=args.namespace,
    )
    for i, m in enumerate(matches, 1):
        md = m.get("metadata") if isinstance(m, dict) else m.metadata  # type: ignore[union-attr]
        score = m.get("score") if isinstance(m, dict) else m.score  # type: ignore[union-attr]
        mid = m.get("id") if isinstance(m, dict) else m.id  # type: ignore[union-attr]
        text = (md or {}).get("text", "")[:300].replace("\n", " ")
        print(f"\n[{i}] score={score:.3f} id={mid}")
        print(f"  category={md.get('category')} tone={md.get('tone')} tier={md.get('tier')}")
        print(f"  title={md.get('title')}")
        print(f"  text={text}...")


if __name__ == "__main__":
    main()

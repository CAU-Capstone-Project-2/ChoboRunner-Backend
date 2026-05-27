# rag-indexer

자세 분석 RAG 코퍼스 인덱서. 설계 문서: `../../docs/posture-analysis-rag-design.md`.

## 구성

- T1 공공 가이드: `corpus/t1-public/kor-running-guideline.pdf` (수동 배치)
- T2 오픈액세스 학술: `fetch_oa.py` 가 OpenAlex -> Europe PMC/Unpaywall 경로로 수집해 `corpus/t2-oa/` 에 저장
- 임베딩 모델: `text-embedding-3-large` (3072 dim)
- Pinecone 인덱스: `capstone2-posture-analyze-rag` (aws/us-east-1, cosine)

## 사전 준비

1. Python 3.11+ 와 `uv` 또는 `pip` 설치
2. 의존성: `uv pip install -e .` 또는 `pip install -e .`
3. `.env.example` 을 `.env` 로 복사 후 키 채우기
   - `OPENAI_API_KEY`
   - `PINECONE_API_KEY`
   - `PINECONE_INDEX_HOST` (Pinecone 콘솔에서 인덱스 host 복사)
   - `UNPAYWALL_EMAIL` (Unpaywall 사용 시 필수, 본인 이메일)
4. T1 PDF 를 `corpus/t1-public/kor-running-guideline.pdf` 로 복사

## 실행 순서

```bash
# 1) T2 자료 자동 수집 (OpenAlex 검색 -> 본문 추출 -> corpus/t2-oa/)
python -m rag_indexer.fetch_oa

# 2) 전 tier chunking -> embedding -> Pinecone upsert
python -m rag_indexer.main --tier all --namespace v1

# 3) 로컬 검색 정성 검증
python -m rag_indexer.query "상체가 많이 기울어진 러너 개선"
```

## 패키지 모듈

| 모듈 | 역할 |
| --- | --- |
| `fetch_oa` | OpenAlex 검색 + Europe PMC XML / Unpaywall PDF 본문 수집 |
| `chunk` | 문서 -> chunk[] + tone 라벨링 (`coaching`/`clinical`) |
| `embed` | OpenAI embeddings 배치 호출 |
| `upsert` | Pinecone upsert |
| `query` | 로컬 검색 검증 CLI |
| `main` | end-to-end pipeline 오케스트레이션 |

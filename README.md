# ChoboRunner Backend (capstone2-server)

초보 러너를 위한 **실시간 러닝 자세 분석 서비스**의 백엔드 서버입니다.
안드로이드 앱이 촬영한 러닝 영상을 WebSocket으로 중계해 AI(Python) 서버의 측면 자세 분석을 받고,
그 결과를 코치 톤 LLM 코멘트 + RAG 도메인 지식으로 가공해 리포트로 영속화합니다.

> Spring Boot 4 · Java 21 · MySQL · WebSocket relay · OpenAI LLM · Pinecone RAG · AWS(EC2/ALB/S3) · Docker · GitHub Actions

---

## 1. 핵심 기능

| 기능 | 설명 |
| --- | --- |
| **실시간 영상 중계 (WS Relay)** | Android ↔ Spring ↔ Python AI 사이를 양방향으로 중계. Android는 Spring에만 연결하고 AI 서버 존재를 모름 |
| **자세 분석 리포트 생성** | AI WS 스트림의 `analysis_result` 메시지를 가로채(tap) LLM 코멘트 생성 후 `REPORT`/`DETAILED_REPORT`로 저장 |
| **RAG 기반 개선 제안** | 러닝 바이오메카닉 오픈액세스 코퍼스를 Pinecone에 색인, HyDE 검색으로 근거 있는 개선안 주입 |
| **하이라이트 캡처** | `analysis_progress`의 자세 경고 구간을 병합·보정해 `HIGHLIGHT`로 저장 (문제 구간 타임라인) |
| **영상 오버레이** | 원본 영상을 S3 업로드 → AI 합성 위임 → 오버레이 영상 S3 key를 세션에 저장 (비동기 `Callable`) |
| **기본 CRUD / 인증** | User·RunSession·Report·Feedback·Highlight CRUD, API Key 기반 단순 인증, S3 presigned URL |

---

## 2. 기술 스택

- **언어/런타임**: Java 21
- **프레임워크**: Spring Boot 4.0.5 (Web MVC, WebSocket, WebFlux/Reactor Netty, Data JPA)
- **DB**: MySQL (운영) / H2 (테스트), Hibernate, HikariCP
- **AI/LLM**: OpenAI Chat Completions(`gpt-5.5`) + Embedding(`text-embedding-3-large`) — `WebClient` 직접 호출
- **Vector DB**: Pinecone Serverless (RAG, cosine, 3072 dim)
- **인프라**: AWS EC2 + Application Load Balancer + S3, Docker, Docker Compose
- **CI/CD**: GitHub Actions → Docker Hub → EC2 무중단 재배포(`scale.sh`)
- **문서화**: springdoc-openapi (Swagger UI)

---

## 3. 아키텍처

```
                      ┌──────────── REST (Authorization: Bearer <API_KEY>) ───────────┐
                      │                                                                │
 ┌───────────┐   wss /ws/chobo-runner?runId=N    ┌──────────────┐   ws    ┌───────────┐
 │  Android  │ ─── binary frame [8B ts][JPEG] ──▶ │ Spring 서버  │ ──────▶ │ Python AI │
 │   앱      │ ◀── text (분석 결과 릴레이) ────── │ VideoRelay   │ ◀────── │  (추론)   │
 └───────────┘                                    │  Handler     │         └───────────┘
                                                  └──────┬───────┘
                                  message tap (단일 스레드 Executor)
                       ┌──────────────────────────┼───────────────────────────┐
                       ▼                           ▼                           ▼
              analysis_progress            analysis_result              (binary/그 외)
              HighlightCaptureService      PostureResultService          단순 릴레이
                       │                    │  ├ RAG 검색(Pinecone)
                       ▼                    │  ├ LLM 코멘트(OpenAI)
                  HIGHLIGHT                 │  └ persist (TX)
                                            ▼
                                   REPORT / DETAILED_REPORT
```

- **WS 릴레이는 절대 블로킹하지 않는다**: 분석/하이라이트/LLM/DB 작업은 모두 별도 단일 스레드 Executor로 위임.
- **LLM·DB 실패는 로그만 남기고 릴레이는 계속** — 분석 부가기능이 영상 스트림을 막지 않도록 격리.

---

## 4. REST API 개요

모든 `/api/**` 요청은 `Authorization: Bearer <APP_API_KEY>` 헤더가 필요합니다.
(`/swagger-ui`, `/v3/api-docs`, `/ws`, `/healthcheck` 등은 화이트리스트)

| 메서드 / 경로 | 설명 |
| --- | --- |
| `POST /api/users` · `POST /api/users/login` | 회원 생성 / 로그인 |
| `GET/POST/PUT/DELETE /api/runs` · `GET /api/runs/by-user/{userId}` | 러닝 세션 CRUD |
| `GET /api/reports` · `GET /api/reports/by-run/{runSessionId}` | 자세 분석 리포트 조회 |
| `GET /api/detailed-reports/by-report/{reportId}` | 지표별 상세 리포트 |
| `GET /api/highlights/by-run/{runId}` | 문제 구간 하이라이트 |
| `GET /api/feedbacks/by-run/{runId}` | 피드백 로그 |
| `POST /api/analyze/overlay` (multipart) | 영상 오버레이 합성 요청 (비동기) |
| `POST /api/s3/upload` · `GET /api/s3/presigned-url?key=` | S3 업로드 / 재생용 presigned URL |
| `GET /healthcheck` | ALB 헬스체크 |

WebSocket: `wss://<host>/ws/chobo-runner?runId=<RunSession.id>` (인증 불필요, 자세한 규약은 [docs/android-websocket-guide.md](docs/android-websocket-guide.md))

---

## 5. 로컬 실행

### 사전 요구사항
- JDK 21
- MySQL (또는 환경변수 없이 테스트용 H2)
- (선택) OpenAI / Pinecone API 키 — RAG·LLM 활성화 시

### 환경변수

| 변수 | 필수 | 설명 |
| --- | --- | --- |
| `DB_HOST` / `DB_USER` / `DB_PASSWORD` | ✅ | MySQL 접속 정보 (`choborunner` 스키마) |
| `APP_API_KEY` | ✅ | REST 인증용 API Key |
| `AI_SERVER_WS_URL` | | Python AI WS URL (기본 `ws://localhost:8000/ws/inference`) |
| `AI_SERVER_OVERLAY_URL` | | AI 오버레이 REST URL |
| `OPENAI_API_KEY` | | LLM/임베딩/HyDE 호출 |
| `POSTURE_RAG_ENABLED` | | RAG 활성화 (기본 `false`) |
| `PINECONE_API_KEY` / `PINECONE_INDEX_HOST` | | RAG 활성화 시 필요 |

### 실행

```powershell
# 빌드 & 테스트
./gradlew build

# 실행
./gradlew bootRun
# → http://localhost:8080 , Swagger UI: http://localhost:8080/swagger-ui.html
```

### Docker

```powershell
docker build -t capstone2-backend .
docker run -p 8080:8080 --env-file .env capstone2-backend
```

---

## 6. 테스트

```powershell
./gradlew test
```

- 단위 테스트: 점수 계산, LLM Sanitizer/Fallback, 프롬프트 빌더, 하이라이트 보정 공식 등
- 통합 테스트: CRUD 플로우, 로그인
- 스모크 IT(`PostureRagSmokeTest`, `PostureLlmSmokeTest`): 환경변수 가드(`POSTURE_RAG_IT=true`) + `rag-it` 프로필로만 실행 — RAG/LLM 종단 회귀 감지

WS 수동 테스트는 `tools/ws-test/mock_android_client.py`(안드로이드 모사 레퍼런스 클라이언트) 사용.

---

## 7. 디렉터리 구조

```
src/main/java/capstone2/server/
├── config/         # ApiKeyFilter, WebSocketConfig, OpenApiConfig, WebConfig
├── controllers/    # REST 컨트롤러 + GlobalExceptionHandler
├── dto/            # 요청/응답 DTO
├── entities/       # JPA 엔티티 (User, RunSession, Report, DetailedReport, Highlight, FeedbackLog)
├── repositories/   # Spring Data JPA 리포지토리
├── services/
│   ├── posture/    # PostureResultService, Persister, Metric/Score
│   ├── llm/        # PostureLlmClient, PromptBuilder, Sanitizer, Fallback
│   ├── rag/        # Pinecone/Embedding/HyDE/Retriever
│   └── ai/         # OverlayAiClient
└── websocket/      # VideoRelayHandler
docs/               # 설계 문서 (v3, RAG, 하이라이트, 오버레이, 성능 리포트, CI/CD)
tools/
├── rag-indexer/    # Python 임베딩 배치 (OpenAlex 수집 → chunk → 분류 → Pinecone upsert)
└── ws-test/        # WS 수동 테스트 클라이언트
```

---

## 8. 설계 문서

| 문서 | 내용 |
| --- | --- |
| [posture-analysis-design-v3.md](docs/posture-analysis-design-v3.md) | 자세 분석 v3 — WS 메시지 탭 방식 |
| [posture-analysis-rag-design.md](docs/posture-analysis-rag-design.md) | RAG 도입 설계 (HyDE 검색 포함) |
| [highlight-analysis-design.md](docs/highlight-analysis-design.md) | 하이라이트 구간 캡처 알고리즘 |
| [video-overlay-design.md](docs/video-overlay-design.md) | 영상 오버레이 비동기 처리 |
| [performance-test-report.md](docs/performance-test-report.md) | 컨테이너 스케일링 부하 테스트 |
| [android-websocket-guide.md](docs/android-websocket-guide.md) | 안드로이드 WS 연동 가이드 |

자세한 핵심 구현 설명은 [PORTFOLIO.md](docs/PORTFOLIO.md)를 참고하세요.

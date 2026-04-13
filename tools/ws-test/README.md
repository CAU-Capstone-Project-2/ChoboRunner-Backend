# WebSocket Relay Quick Test

`VideoRelayHandler`의 Android -> Spring -> Python -> Spring -> Android 릴레이를 로컬에서 검증하는 방법입니다.

## Files

- `mock_inference_ws.py`: Python WebSocket mock server (`ws://127.0.0.1:8000/ws/inference`)
- `mock_android_client.py`: Android 대체 Python client (`ws://127.0.0.1:8090/ws/chobo-runner` 권장)

## Prerequisites

1. Python 3.11+
2. `websockets` 패키지 설치

```powershell
Set-Location "C:\capston_project(2)\server\server"
python -m pip install websockets
```

## Test Steps

### 1) Spring 서버 실행 (터미널 A)

`wstest` 프로필은 ws-test에서만 H2 인메모리 DB를 사용합니다.

```powershell
Set-Location "C:\capston_project(2)\server\server"
.\gradlew.bat bootRun --args="--spring.profiles.active=wstest --server.port=8090"
```

### 2) Python mock 서버 실행 (터미널 B)

```powershell
Set-Location "C:\capston_project(2)\server\server"
python -u .\tools\ws-test\mock_inference_ws.py
```

### 3) Android 대체 클라이언트 실행 (터미널 C)

```powershell
Set-Location "C:\capston_project(2)\server\server"
python -u .\tools\ws-test\mock_android_client.py --uri ws://127.0.0.1:8090/ws/chobo-runner --file "C:\path\to\image.png"
```

대용량 이미지에서 1009 에러가 발생하면 압축 전송 옵션을 사용하세요.

```powershell
Set-Location "C:\capston_project(2)\server\server"
python -u .\tools\ws-test\mock_android_client.py --uri ws://127.0.0.1:8090/ws/chobo-runner --file "C:\path\to\image.png" --compress --jpeg-quality 60 --max-dimension 1024
```

## Success Criteria

- 클라이언트: `[client] received text: {"type": "inference", "status": "ok", ...}` 출력
- Python mock: `[python-ws] binary received` 출력
- Spring 로그: `[ws] Forwarded to Python`, `[ws] Python text received` 출력
- Spring 로그에 `FastAPI 연결 에러`가 없어야 함

## Quick Troubleshooting

- `HTTP 404`가 나오면: Spring 경로(`/ws/chobo-runner`)와 포트 확인
- 응답 타임아웃이면: Python mock 서버가 8000 포트에서 실행 중인지 확인
- 연결 실패면: 방화벽/포트 점유(8000, 8090) 확인
- `1009 message too big`면: `app.websocket.max-message-size-bytes` 값을 늘리거나 `--compress` 옵션으로 전송 크기 축소


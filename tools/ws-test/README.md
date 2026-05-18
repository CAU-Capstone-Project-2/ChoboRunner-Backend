# WebSocket Relay Quick Test

`VideoRelayHandler`의 Android -> Spring -> Python -> Spring -> Android 릴레이를 로컬에서 검증하는 방법입니다.

## Files

- `mock_inference_ws.py`: Python WebSocket mock server (`ws://127.0.0.1:8000/ws/inference`)
- `mock_android_client.py`: Android 대체 Python client. 영상 파일을 디코딩해
  `[8B ts_ms][JPEG]` binary frame을 720p/30fps로 스트리밍한다 (docs/2-4-2 §4 규약).

## Prerequisites

1. Python 3.11+
2. `websockets`, `opencv-python` 패키지 설치 (영상 디코딩에 OpenCV 사용)

```powershell
Set-Location "C:\capston_project(2)\server\server"
python -m pip install websockets opencv-python
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

영상 파일을 720p로 리사이즈해 30fps로 스트리밍한다. 송신이 끝나면
`{"type":"stop"}` 제어 메시지를 보내고 서버 응답을 출력한다.

```powershell
Set-Location "C:\capston_project(2)\server\server"
python -u .\tools\ws-test\mock_android_client.py --uri ws://127.0.0.1:8090/ws/chobo-runner --video "C:\path\to\run.mp4"
```

`--fps`로 송신 프레임레이트를 바꿀 수 있다 (원본 fps가 더 높으면 frame을 솎고,
더 낮으면 원본 그대로 송신). `--height`로 해상도를, `--max-frames`로 송신 길이를 조절한다.

```powershell
# 15fps · 480p · 앞 60 frame만 송신
python -u .\tools\ws-test\mock_android_client.py --uri ws://127.0.0.1:8090/ws/chobo-runner --video "C:\path\to\run.mp4" --fps 15 --height 480 --max-frames 60
```

| 옵션 | 기본값 | 설명 |
| --- | --- | --- |
| `--video` | (필수) | 전송할 영상 파일 (`.mp4`, `.mov`, `.avi`, `.mkv`, `.webm`, `.m4v`) |
| `--fps` | `30` | 송신 fps. 원본보다 높으면 원본 frame을 그대로 전송 |
| `--height` | `720` | 리사이즈 목표 세로 해상도(px) — 가로세로 비율 유지 |
| `--jpeg-quality` | `85` | JPEG 인코딩 품질 (1~100) |
| `--max-frames` | `0` | 송신할 최대 frame 수 (0 = 영상 끝까지) |
| `--result-timeout` | `10` | stop 송신 후 `analysis_result` 수신 대기 시간(초) |

## Success Criteria

- 클라이언트: `[client] -> frame #0 ...` → `[client] N frames 송신 완료` →
  `[client] -> control {"type":"stop"}` 순서로 출력
- 클라이언트: 서버 응답(`[client] <- ...`) 수신 출력
- Python mock: `[python-ws] binary received` 출력
- Spring 로그: `[ws] Forwarded to Python`, `[ws] Python text received` 출력
- Spring 로그에 `AI 연결 에러`가 없어야 함

> `mock_inference_ws.py`는 `analysis_result`를 보내지 않으므로 클라이언트가
> `--result-timeout` 후 미수신 메시지를 출력하는 것은 정상이다. 실제 AI 서버와
> 연동할 때는 `analysis_result` 수신 후 즉시 종료한다.

## Quick Troubleshooting

- `HTTP 404`가 나오면: Spring 경로(`/ws/chobo-runner`)와 포트 확인
- 응답 타임아웃이면: Python mock 서버가 8000 포트에서 실행 중인지 확인
- 연결 실패면: 방화벽/포트 점유(8000, 8090) 확인
- `1009 message too big`면: `--height`를 낮추거나 `--jpeg-quality`를 낮춰 frame 크기 축소 (720p JPEG는 보통 수십 KB이므로 드묾)
- `영상을 열 수 없습니다`면: 코덱 문제 — `opencv-python` 재설치 또는 다른 컨테이너 포맷으로 변환


"""mock_android_client.py — Android 영상 송신을 모사하는 WebSocket 테스트 클라이언트.

영상 파일을 디코딩하고(세로 영상은 rotation 메타데이터를 적용해 똑바로 세운다)
720p로 리사이즈한 뒤, 각 frame을 `[8B BE ts_ms][JPEG]` binary frame으로
설정된 fps에 맞춰 Spring relay(`/ws/chobo-runner`)에 전송한다.
전송이 끝나면 `{"type":"stop"}` 제어 메시지를 보내고, 서버가 돌려주는
응답(text frame)을 출력해 데이터 수신을 검증한다.

규약 출처: docs/2-4-2 "AI ↔ Backend WebSocket 연동 설계"
  - §4 binary frame wire format: [8B BE signed int64 ts_ms][JPEG bytes]
  - §4-2 ts_ms: 밀리초 단위 단조(monotonic) 시계, frame 간 비감소
  - §5-1 종료 신호: text frame {"type":"stop"}

사용 예:
  python -u .\\tools\\ws-test\\mock_android_client.py --video "C:\\path\\to\\run.mp4"
  python -u .\\tools\\ws-test\\mock_android_client.py --video run.mp4 --fps 15 --height 480
"""

import argparse
import asyncio
import json
import struct
import time
from pathlib import Path

import cv2
import websockets

# Android binary frame: [8B BE signed int64 ts_ms (monotonic)][JPEG bytes] — docs/2-4-2 §4
TIMESTAMP_HEADER_BYTES = 8
SUPPORTED_SUFFIXES = {".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v"}

# next()가 generator 소진 시 StopIteration 대신 돌려줄 표식 (to_thread 호환).
_EXHAUSTED = object()

# 컨테이너 rotation 메타데이터(도) → cv2.rotate 코드.
_ROTATE_CODES = {
    90: cv2.ROTATE_90_CLOCKWISE,
    180: cv2.ROTATE_180,
    270: cv2.ROTATE_90_COUNTERCLOCKWISE,
}


def _disable_auto_orientation(capture) -> None:
    """OpenCV 자동 회전을 끈다(빌드별 기본값이 달라 수동 적용으로 통일). 미지원 빌드면 무시."""
    prop = getattr(cv2, "CAP_PROP_ORIENTATION_AUTO", None)
    if prop is not None:
        capture.set(prop, 0)


def _frame_rotation(capture) -> int:
    """컨테이너 rotation 메타데이터(도)를 읽는다. 없거나 미지원이면 0."""
    prop = getattr(cv2, "CAP_PROP_ORIENTATION_META", None)
    if prop is None:
        return 0
    meta = capture.get(prop)
    if not meta:
        return 0
    return int(round(meta)) % 360


def _apply_rotation(frame, rotation: int):
    """세로 영상 등 rotation 메타데이터가 박힌 frame을 '똑바로 선' 상태로 회전한다.

    WS 스트리밍은 호출 측이 똑바로 선 frame을 보내야 AI 서버의 좌표계(수직축 기준
    trunk_lean 등)와 정합한다. file mode 분석은 디코더가 자동 회전하지만,
    cv2.VideoCapture.read()는 rotation 메타데이터를 적용하지 않으므로 직접 회전한다.
    """
    code = _ROTATE_CODES.get(rotation)
    return frame if code is None else cv2.rotate(frame, code)


def iter_video_jpeg_frames(video_path: str, target_height: int, send_fps: float,
                           jpeg_quality: int, max_frames: int):
    """영상 디코딩 → target_height 리사이즈 → send_fps로 데시메이션 → JPEG bytes 생성기.

    원본 fps가 send_fps보다 높으면 비율 누산기로 frame을 솎아 송신 fps에 맞춘다.
    원본 fps가 낮으면 모든 frame을 그대로 송신한다(frame 합성은 하지 않음).
    """
    path = Path(video_path)
    if not path.is_file():
        raise FileNotFoundError(f"영상 파일을 찾을 수 없습니다: {path}")
    if path.suffix.lower() not in SUPPORTED_SUFFIXES:
        raise ValueError(
            f"지원하지 않는 영상 형식입니다: '{path.suffix}'. "
            f"허용: {', '.join(sorted(SUPPORTED_SUFFIXES))}"
        )

    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise RuntimeError(f"영상을 열 수 없습니다(코덱 손상/미지원 확인): {path}")

    # rotation 메타데이터를 수동 적용한다(자동 회전을 끄고 직접 회전 → 결정적 동작).
    _disable_auto_orientation(capture)
    rotation = _frame_rotation(capture)

    source_fps = capture.get(cv2.CAP_PROP_FPS)
    if not source_fps or source_fps <= 0:
        # 메타데이터가 없는 경우 데시메이션 없이 전 frame 송신.
        source_fps = send_fps
    print(
        f"[client] source='{path.name}' source_fps={source_fps:.2f} "
        f"target_fps={send_fps:g} target_height={target_height}p jpeg_q={jpeg_quality}",
        flush=True,
    )
    if rotation:
        print(
            f"[client] rotation metadata={rotation}° → frame에 적용 (세로 영상 보정)",
            flush=True,
        )

    encode_params = [cv2.IMWRITE_JPEG_QUALITY, jpeg_quality]
    keep_ratio = min(1.0, send_fps / source_fps)  # 1.0이면 전 frame 송신
    accumulator = 0.0
    emitted = 0
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break

            if rotation:
                frame = _apply_rotation(frame, rotation)

            # send_fps에 맞춰 frame 솎기: 누산기가 1.0을 넘는 frame만 송신.
            accumulator += keep_ratio
            if accumulator < 1.0:
                continue
            accumulator -= 1.0

            resized = _resize_to_height(frame, target_height)
            ok, buffer = cv2.imencode(".jpg", resized, encode_params)
            if not ok:
                raise RuntimeError("JPEG 인코딩에 실패했습니다.")

            if emitted == 0:
                h, w = resized.shape[:2]
                print(f"[client] frame resized to {w}x{h}", flush=True)

            yield buffer.tobytes()
            emitted += 1
            if max_frames and emitted >= max_frames:
                break
    finally:
        capture.release()

    if emitted == 0:
        raise RuntimeError("전송할 frame이 없습니다(빈 영상이거나 디코딩 실패).")


def _resize_to_height(frame, target_height: int):
    """가로세로 비율을 유지하며 세로 길이를 target_height로 맞춘다."""
    h, w = frame.shape[:2]
    if h == target_height:
        return frame
    scale = target_height / h
    target_width = max(2, round(w * scale) & ~1)  # 짝수 폭 권장
    interpolation = cv2.INTER_AREA if scale < 1.0 else cv2.INTER_LINEAR
    return cv2.resize(frame, (target_width, target_height), interpolation=interpolation)


def _message_type(text: str) -> str:
    """응답 text frame의 'type' 필드를 추출한다 (JSON이 아니면 '?')."""
    try:
        parsed = json.loads(text)
        return parsed.get("type", "?") if isinstance(parsed, dict) else "?"
    except (json.JSONDecodeError, TypeError):
        return "?"


async def receive_messages(websocket) -> dict:
    """서버 응답(text frame)을 수신·출력한다. analysis_result/error 또는 연결 종료 시 반환.

    docs/2-3-7 응답 4종: frame_inference / analysis_progress / analysis_result / error.
    frame_inference는 양이 많아 1초 간격(요약)으로만 찍고, 나머지는 전문을 출력한다.
    """
    counts: dict = {}
    try:
        async for message in websocket:
            if isinstance(message, bytes):
                print(f"[client] <- binary {len(message)}B (예상 밖)", flush=True)
                continue

            msg_type = _message_type(message)
            counts[msg_type] = counts.get(msg_type, 0) + 1

            if msg_type == "frame_inference":
                if counts[msg_type] % 30 == 1:
                    print(f"[client] <- frame_inference (누적 {counts[msg_type]})", flush=True)
            else:
                print(f"[client] <- {message}", flush=True)

            if msg_type in ("analysis_result", "error"):
                break
    except websockets.ConnectionClosed as exc:
        print(f"[client] 연결 종료됨: {exc}", flush=True)
    return counts


async def send_frames(websocket, frame_generator, send_fps: float) -> int:
    """frame을 send_fps 페이싱으로 송신하고, 끝나면 {"type":"stop"}을 보낸다."""
    frame_interval = 1.0 / send_fps
    # ts_ms: 단조 시계 기준. Δt를 송신 fps로 균등하게 부여한다 (docs/2-4-2 §4-2).
    base_ts_ms = time.monotonic_ns() // 1_000_000
    start = time.monotonic()
    index = 0

    while True:
        jpeg_bytes = await asyncio.to_thread(next, frame_generator, _EXHAUSTED)
        if jpeg_bytes is _EXHAUSTED:
            break

        ts_ms = base_ts_ms + round(index * 1000.0 / send_fps)
        framed = struct.pack(">q", ts_ms) + jpeg_bytes
        await websocket.send(framed)

        if index % max(1, round(send_fps)) == 0:
            print(
                f"[client] -> frame #{index}: {len(framed)}B "
                f"(ts_ms={ts_ms}, header={TIMESTAMP_HEADER_BYTES}B, jpeg={len(jpeg_bytes)}B)",
                flush=True,
            )

        index += 1
        # 실시간 페이싱: 다음 frame 송신 예정 시각까지 대기.
        delay = (start + index * frame_interval) - time.monotonic()
        if delay > 0:
            await asyncio.sleep(delay)

    elapsed = time.monotonic() - start
    print(
        f"[client] {index} frames 송신 완료 "
        f"({elapsed:.1f}s, 실측 {index / elapsed:.1f}fps)",
        flush=True,
    )

    await websocket.send(json.dumps({"type": "stop"}))
    print('[client] -> control {"type":"stop"}', flush=True)
    return index


async def run(uri: str, video: str, fps: float, height: int, jpeg_quality: int,
              max_frames: int, result_timeout: float) -> None:
    frame_generator = iter_video_jpeg_frames(video, height, fps, jpeg_quality, max_frames)

    async with websockets.connect(uri, max_size=None) as websocket:
        print(f"[client] connected: {uri}", flush=True)
        receiver = asyncio.create_task(receive_messages(websocket))
        try:
            await send_frames(websocket, frame_generator, fps)
            # stop 송신 후 analysis_result(또는 error) 수신을 기다린다.
            try:
                counts = await asyncio.wait_for(asyncio.shield(receiver), timeout=result_timeout)
                print(f"[client] 수신 요약: {counts}", flush=True)
            except asyncio.TimeoutError:
                print(
                    f"[client] analysis_result 미수신 — {result_timeout:g}s timeout "
                    f"(mock 서버는 analysis_result를 보내지 않을 수 있음)",
                    flush=True,
                )
        finally:
            frame_generator.close()
            receiver.cancel()
            try:
                await receiver
            except asyncio.CancelledError:
                pass


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="영상 파일을 720p binary frame으로 스트리밍하는 Android 모사 클라이언트.",
    )
    parser.add_argument(
        "--uri",
        default="ws://127.0.0.1:8090/ws/chobo-runner",
        help="Spring relay WebSocket URI (기본: ws-test 프로필 8090 포트)",
    )
    parser.add_argument(
        "--video",
        required=True,
        help=f"전송할 영상 파일 경로 ({', '.join(sorted(SUPPORTED_SUFFIXES))})",
    )
    parser.add_argument(
        "--fps",
        type=float,
        default=30.0,
        help="송신 fps (기본: 30). 원본보다 높으면 원본 frame을 그대로 보냄",
    )
    parser.add_argument(
        "--height",
        type=int,
        default=720,
        help="리사이즈 목표 세로 해상도(px, 기본: 720 = 720p)",
    )
    parser.add_argument(
        "--jpeg-quality",
        type=int,
        default=85,
        help="JPEG 인코딩 품질 (1~100, 기본: 85)",
    )
    parser.add_argument(
        "--max-frames",
        type=int,
        default=0,
        help="송신할 최대 frame 수 (0 = 영상 끝까지)",
    )
    parser.add_argument(
        "--result-timeout",
        type=float,
        default=10.0,
        help="stop 송신 후 analysis_result 수신 대기 시간(초, 기본: 10)",
    )
    return parser


if __name__ == "__main__":
    args = _build_parser().parse_args()
    if args.fps <= 0:
        raise SystemExit("--fps는 0보다 커야 합니다.")
    if args.height <= 0:
        raise SystemExit("--height는 0보다 커야 합니다.")
    if not 1 <= args.jpeg_quality <= 100:
        raise SystemExit("--jpeg-quality는 1~100 범위여야 합니다.")
    if args.max_frames < 0:
        raise SystemExit("--max-frames는 0 이상이어야 합니다.")

    try:
        asyncio.run(
            run(
                uri=args.uri,
                video=args.video,
                fps=args.fps,
                height=args.height,
                jpeg_quality=args.jpeg_quality,
                max_frames=args.max_frames,
                result_timeout=args.result_timeout,
            )
        )
    except KeyboardInterrupt:
        print("[client] 사용자 중단", flush=True)
    except Exception as exc:  # noqa: BLE001 - 테스트 클라이언트, 최상위에서 메시지만 출력
        print(f"[client] error: {exc}", flush=True)
        raise SystemExit(1)

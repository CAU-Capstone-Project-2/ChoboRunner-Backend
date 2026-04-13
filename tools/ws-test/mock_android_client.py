import asyncio
import argparse
import io
from pathlib import Path

import websockets

try:
    from PIL import Image
except ImportError:
    Image = None


def image_to_binary_payload(file_path: str, compress: bool, jpeg_quality: int, max_dimension: int | None) -> bytes:
    path = Path(file_path)
    if not path.exists() or not path.is_file():
        raise FileNotFoundError(f"이미지 파일을 찾을 수 없습니다: {path}")

    if path.suffix.lower() not in {".png", ".jpg", ".jpeg"}:
        raise ValueError("지원하지 않는 파일 형식입니다. png, jpg, jpeg만 허용됩니다.")

    payload = path.read_bytes()
    if not payload:
        raise ValueError("빈 파일은 전송할 수 없습니다.")

    # 최소한의 매직 넘버 검사로 이미지 파일 형태를 검증합니다.
    is_png = payload.startswith(b"\x89PNG\r\n\x1a\n")
    is_jpeg = payload.startswith(b"\xff\xd8\xff")
    if not (is_png or is_jpeg):
        raise ValueError("이미지 바이너리 헤더가 유효하지 않습니다. png/jpg 파일인지 확인하세요.")

    if not compress:
        return payload

    if Image is None:
        raise RuntimeError("압축 기능을 사용하려면 Pillow가 필요합니다. `python -m pip install pillow`를 실행하세요.")

    with Image.open(path) as img:
        # 전송량을 줄이기 위해 최대 변 길이를 제한합니다.
        if max_dimension and max_dimension > 0:
            img.thumbnail((max_dimension, max_dimension), Image.Resampling.LANCZOS)

        output = io.BytesIO()
        img = img.convert("RGB")
        img.save(output, format="JPEG", quality=jpeg_quality, optimize=True)
        compressed = output.getvalue()

    if not compressed:
        raise ValueError("압축 결과가 비어 있습니다.")

    print(
        f"[client] compressed image: {len(payload)} -> {len(compressed)} bytes "
        f"(quality={jpeg_quality}, max-dimension={max_dimension})"
    )

    return compressed


async def main(
    uri: str,
    image_file: str,
    timeout: float,
    compress: bool,
    jpeg_quality: int,
    max_dimension: int | None,
):
    payload = image_to_binary_payload(
        image_file,
        compress=compress,
        jpeg_quality=jpeg_quality,
        max_dimension=max_dimension,
    )

    async with websockets.connect(uri) as websocket:
        print(f"[client] sending binary from '{image_file}': {len(payload)} bytes")
        await websocket.send(payload)

        response = await asyncio.wait_for(websocket.recv(), timeout=timeout)
        if isinstance(response, bytes):
            print(f"[client] received binary: {len(response)} bytes")
        else:
            print(f"[client] received text: {response}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--uri",
        default="ws://127.0.0.1:8080/ws/chobo-runner",
        help="Spring relay websocket uri",
    )
    parser.add_argument(
        "--file",
        required=True,
        help="전송할 이미지 파일 경로 (.png, .jpg, .jpeg)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=5.0,
        help="서버 응답 대기 시간(초)",
    )
    parser.add_argument(
        "--compress",
        action="store_true",
        help="이미지를 JPEG로 재인코딩해 전송 크기를 줄입니다.",
    )
    parser.add_argument(
        "--jpeg-quality",
        type=int,
        default=70,
        help="--compress 사용 시 JPEG 품질(1~95, 기본값: 70)",
    )
    parser.add_argument(
        "--max-dimension",
        type=int,
        default=1280,
        help="--compress 사용 시 최대 가로/세로 길이(px, 기본값: 1280)",
    )
    args = parser.parse_args()
    if not 1 <= args.jpeg_quality <= 95:
        raise ValueError("--jpeg-quality는 1~95 범위여야 합니다.")

    try:
        asyncio.run(
            main(
                args.uri,
                args.file,
                args.timeout,
                args.compress,
                args.jpeg_quality,
                args.max_dimension,
            )
        )
    except Exception as exc:
        print(f"[client] error: {exc}")



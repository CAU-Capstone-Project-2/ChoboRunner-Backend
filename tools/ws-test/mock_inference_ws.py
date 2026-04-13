import asyncio
import json
import websockets


async def handler(websocket):
    print("[python-ws] connected", flush=True)
    try:
        async for message in websocket:
            if isinstance(message, bytes):
                payload = {
                    "type": "inference",
                    "status": "ok",
                    "bytes": len(message),
                }
                text = json.dumps(payload)
                print(f"[python-ws] binary received: {len(message)} bytes", flush=True)
                await websocket.send(text)
                print(f"[python-ws] text sent: {text}", flush=True)
            else:
                payload = {
                    "type": "inference",
                    "status": "ignored",
                    "reason": "expected binary frame",
                }
                text = json.dumps(payload)
                print(f"[python-ws] text received: {message}", flush=True)
                await websocket.send(text)
    except websockets.ConnectionClosed:
        print("[python-ws] disconnected", flush=True)


async def main():
    # Spring VideoRelayHandler의 pythonAiUri와 경로를 동일하게 맞춤
    async with websockets.serve(handler, "127.0.0.1", 8000):
        print("[python-ws] listening at ws://127.0.0.1:8000/ws/inference", flush=True)
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())



#!/usr/bin/env python3
import argparse
import base64
import json
import os
import shutil
import socket
import struct
import subprocess
import sys
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse


def log(message):
    print(message, flush=True)


def fail(message):
    print(f"FAIL: {message}", file=sys.stderr, flush=True)
    return 1


def websocket_url(endpoint):
    parsed = urlparse(endpoint)
    if parsed.scheme not in ("http", "https", "ws", "wss"):
        raise ValueError("backend URL must start with http://, https://, ws://, or wss://")

    scheme = "wss" if parsed.scheme in ("https", "wss") else "ws"
    base_path = parsed.path.rstrip("/")
    sockjs_session = uuid.uuid4().hex[:12]
    return f"{scheme}://{parsed.netloc}{base_path}/000/{sockjs_session}/websocket"


def recv_exact(sock, size):
    chunks = []
    remaining = size
    while remaining > 0:
        chunk = sock.recv(remaining)
        if not chunk:
            raise ConnectionError("socket closed")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def send_ws_text(sock, text):
    payload = text.encode("utf-8")
    header = bytearray([0x81])
    length = len(payload)

    if length < 126:
        header.append(0x80 | length)
    elif length < 65536:
        header.append(0x80 | 126)
        header.extend(struct.pack("!H", length))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack("!Q", length))

    mask = os.urandom(4)
    masked = bytes(payload[i] ^ mask[i % 4] for i in range(length))
    sock.sendall(bytes(header) + mask + masked)


def recv_ws_frame(sock):
    first = recv_exact(sock, 2)
    opcode = first[0] & 0x0F
    masked = bool(first[1] & 0x80)
    length = first[1] & 0x7F

    if length == 126:
        length = struct.unpack("!H", recv_exact(sock, 2))[0]
    elif length == 127:
        length = struct.unpack("!Q", recv_exact(sock, 8))[0]

    mask = recv_exact(sock, 4) if masked else b""
    payload = recv_exact(sock, length) if length else b""
    if masked:
        payload = bytes(payload[i] ^ mask[i % 4] for i in range(length))

    if opcode == 0x8:
        raise ConnectionError("websocket closed by server")
    if opcode == 0x9:
        sock.sendall(b"\x8a\x00")
        return None
    if opcode != 0x1:
        return None

    return payload.decode("utf-8")


def read_http_response(sock):
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
    return data.decode("iso-8859-1", errors="replace")


def open_websocket(url, timeout):
    parsed = urlparse(url)
    if parsed.scheme != "ws":
        raise ValueError("this script supports ws:// only; use a non-TLS local backend URL")

    host = parsed.hostname
    port = parsed.port or 80
    path = parsed.path or "/"
    if parsed.query:
        path += f"?{parsed.query}"

    sock = socket.create_connection((host, port), timeout=timeout)
    sock.settimeout(timeout)
    key = base64.b64encode(os.urandom(16)).decode("ascii")
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {parsed.netloc}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "Origin: http://localhost\r\n"
        "\r\n"
    )
    sock.sendall(request.encode("ascii"))
    response = read_http_response(sock)
    if " 101 " not in response.splitlines()[0]:
        raise ConnectionError(response.split("\r\n\r\n", 1)[0])
    return sock


def sockjs_send_stomp(sock, stomp_frame):
    send_ws_text(sock, json.dumps([stomp_frame], separators=(",", ":")))


def decode_sockjs_frame(raw):
    if raw is None or raw in ("o", "h"):
        return []
    if raw.startswith("a"):
        return json.loads(raw[1:])
    if raw.startswith("c"):
        raise ConnectionError(f"sockjs closed: {raw}")
    return []


def read_stomp_frames(sock, deadline):
    while time.time() < deadline:
        raw = recv_ws_frame(sock)
        for frame in decode_sockjs_frame(raw):
            yield frame


def parse_stomp_body(frame):
    frame = frame.rstrip("\x00")
    if "\n\n" not in frame:
        return ""
    return frame.split("\n\n", 1)[1]


def run_websocket_check(args):
    endpoint = args.backend_url.rstrip("/") + "/ws/feedback"
    url = websocket_url(endpoint)
    log(f"WebSocket: connecting to {url}")

    session_id = args.session_id or f"check-{uuid.uuid4().hex[:8]}"
    deadline = time.time() + args.timeout

    with open_websocket(url, args.timeout) as sock:
        saw_open = False
        for raw in iter(lambda: recv_ws_frame(sock), None):
            if raw == "o":
                saw_open = True
                break
            if time.time() > deadline:
                raise TimeoutError("SockJS open frame was not received")
        if not saw_open:
            raise TimeoutError("SockJS open frame was not received")

        connect_frame = "CONNECT\naccept-version:1.2\nheart-beat:0,0\nhost:localhost\n\n\x00"
        sockjs_send_stomp(sock, connect_frame)

        connected = False
        for frame in read_stomp_frames(sock, deadline):
            if frame.startswith("CONNECTED"):
                connected = True
                break
        if not connected:
            raise TimeoutError("STOMP CONNECTED frame was not received")

        log("WebSocket: STOMP connected")

        subscriptions = [
            ("sub-feedback", f"/topic/feedback/{session_id}"),
            ("sub-errors", f"/topic/feedback/{session_id}/errors"),
        ]
        for sub_id, destination in subscriptions:
            frame = f"SUBSCRIBE\nid:{sub_id}\ndestination:{destination}\nack:auto\n\n\x00"
            sockjs_send_stomp(sock, frame)

        body = {
            "sessionId": session_id,
            "userId": "check-user",
            "tensorShape": [1, 3, 2, 2],
            "features": [0.0] * 12,
            "timestamp": int(time.time() * 1000),
            "faceDetected": False,
            "bbox": {
                "x1": 0,
                "y1": 0,
                "x2": 0,
                "y2": 0,
            },
        }
        body_text = json.dumps(body, separators=(",", ":"))
        send_frame = (
            "SEND\n"
            "destination:/app/feedback.frames\n"
            "content-type:application/json\n"
            f"content-length:{len(body_text.encode('utf-8'))}\n\n"
            f"{body_text}\x00"
        )
        sockjs_send_stomp(sock, send_frame)
        log(f"WebSocket: sent feedback frame for session {session_id}")

        for frame in read_stomp_frames(sock, deadline):
            if frame.startswith("ERROR"):
                raise RuntimeError(frame.rstrip("\x00"))
            if not frame.startswith("MESSAGE"):
                continue

            body_text = parse_stomp_body(frame)
            if f"/topic/feedback/{session_id}/errors" in frame:
                raise RuntimeError(f"backend reported stream error: {body_text}")
            if f"/topic/feedback/{session_id}" in frame:
                log(f"WebSocket: received feedback message: {body_text}")
                return True

        raise TimeoutError("feedback message was not received before timeout")


def run_grpc_check(args):
    grpcurl = shutil.which("grpcurl")
    if not grpcurl:
        log("gRPC: skipped because grpcurl is not installed")
        return None

    proto_dir = Path(__file__).resolve().parents[1] / "src/main/proto"
    payload = {
        "sessionId": args.session_id or f"check-{uuid.uuid4().hex[:8]}",
        "userId": "check-user",
        "tensorShape": [1, 3, 2, 2],
        "features": [0.0] * 12,
        "timestamp": int(time.time() * 1000),
        "faceDetected": False,
        "bbox": {
            "x1": 0,
            "y1": 0,
            "x2": 0,
            "y2": 0,
        },
    }
    target, use_plaintext = normalize_grpc_target(args.ai_target, args.grpc_tls)
    command = [
        grpcurl,
        "-max-time",
        str(args.timeout),
        "-import-path",
        str(proto_dir),
        "-proto",
        "emotion_analysis.proto",
        "-d",
        json.dumps(payload),
        target,
        "interview.InterviewAIService/AnalyzeFrameStream",
    ]
    if use_plaintext:
        command.insert(1, "-plaintext")

    log(f"gRPC: calling {target} interview.InterviewAIService/AnalyzeFrameStream")
    result = subprocess.run(command, text=True, capture_output=True, timeout=args.timeout + 2)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())

    log(f"gRPC: received response: {result.stdout.strip()}")
    return True


def normalize_grpc_target(target, force_tls):
    parsed = urlparse(target)
    if parsed.scheme in ("http", "https"):
        host = parsed.hostname
        if not host:
            raise ValueError("AI target URL must include a host")
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        return f"{host}:{port}", parsed.scheme == "http" and not force_tls

    return target, not force_tls


def main():
    parser = argparse.ArgumentParser(description="Check InterviewMirror WebSocket and gRPC realtime paths.")
    parser.add_argument("--backend-url", default="http://localhost:8080/api")
    parser.add_argument("--ai-target", default="localhost:50051")
    parser.add_argument("--grpc-tls", action="store_true")
    parser.add_argument("--session-id")
    parser.add_argument("--timeout", type=int, default=10)
    parser.add_argument("--skip-grpc", action="store_true")
    parser.add_argument("--skip-websocket", action="store_true")
    args = parser.parse_args()

    if args.skip_grpc and args.skip_websocket:
        return fail("nothing to check")

    try:
        grpc_result = None if args.skip_grpc else run_grpc_check(args)
        websocket_result = None if args.skip_websocket else run_websocket_check(args)
    except Exception as exc:
        return fail(str(exc))

    if grpc_result is True:
        log("PASS: gRPC direct check succeeded")
    elif grpc_result is None and not args.skip_grpc:
        log("WARN: gRPC direct check was skipped")

    if websocket_result is True:
        log("PASS: WebSocket -> backend -> gRPC path succeeded")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

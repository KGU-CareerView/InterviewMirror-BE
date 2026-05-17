#!/usr/bin/env python3
import argparse
import base64
import json
import os
import socket
import ssl
import struct
import sys
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def log(message):
    print(message, flush=True)


def fail(message):
    print(f"FAIL: {message}", file=sys.stderr, flush=True)
    return 1


def build_url(base_url, path):
    return f"{base_url.rstrip('/')}/{path.lstrip('/')}"


def auth_headers(token):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def request_json(method, url, token=None, body=None, timeout=10):
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = Request(url, data=data, headers=auth_headers(token), method=method)

    try:
        with urlopen(request, timeout=timeout) as response:
            text = response.read().decode("utf-8")
            return response.status, json.loads(text) if text else {}
    except HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} {url}: {text}") from exc
    except URLError as exc:
        raise RuntimeError(f"HTTP request failed {url}: {exc.reason}") from exc


def extract_data(payload):
    return payload.get("data") if isinstance(payload, dict) else None


def websocket_url(base_url):
    parsed = urlparse(build_url(base_url, "/ws-interview"))
    if parsed.scheme not in ("http", "https", "ws", "wss"):
        raise ValueError("backend URL must start with http://, https://, ws://, or wss://")

    scheme = "wss" if parsed.scheme in ("https", "wss") else "ws"
    sockjs_session = uuid.uuid4().hex[:12]
    return f"{scheme}://{parsed.netloc}{parsed.path.rstrip('/')}/000/{sockjs_session}/websocket"


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


def open_websocket(url, token, timeout):
    parsed = urlparse(url)
    if parsed.scheme not in ("ws", "wss"):
        raise ValueError("websocket URL must start with ws:// or wss://")

    host = parsed.hostname
    port = parsed.port or (443 if parsed.scheme == "wss" else 80)
    path = parsed.path or "/"
    if parsed.query:
        path += f"?{parsed.query}"

    raw_sock = socket.create_connection((host, port), timeout=timeout)
    raw_sock.settimeout(timeout)
    if parsed.scheme == "wss":
        sock = ssl.create_default_context().wrap_socket(raw_sock, server_hostname=host)
    else:
        sock = raw_sock

    key = base64.b64encode(os.urandom(16)).decode("ascii")
    auth_line = f"Authorization: Bearer {token}\r\n" if token else ""
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {parsed.netloc}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "Origin: http://localhost\r\n"
        f"{auth_line}"
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


def connect_and_subscribe(args, session_id):
    url = websocket_url(args.backend_url)
    log(f"WebSocket: connecting to {url}")
    deadline = time.time() + args.timeout

    sock = open_websocket(url, args.token, args.timeout)
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
        ("questions", f"/topic/session/{session_id}/question"),
        ("errors", f"/topic/session/{session_id}/error"),
    ]
    for sub_id, destination in subscriptions:
        frame = f"SUBSCRIBE\nid:{sub_id}\ndestination:{destination}\nack:auto\n\n\x00"
        sockjs_send_stomp(sock, frame)
        log(f"WebSocket: subscribed {destination}")

    return sock


def create_session(args):
    status, payload = request_json(
        "POST", build_url(args.backend_url, "/v1/sessions"), args.token, {}, args.timeout
    )
    data = extract_data(payload) or {}
    session_id = data.get("sessionId")
    if status not in (200, 201) or session_id is None:
        raise RuntimeError(f"unexpected create session response: HTTP {status} {payload}")

    log(f"HTTP: created session {session_id}, state={data.get('sessionState')}")
    return session_id


def start_session(args, session_id):
    body = {
        "category": args.category,
        "interviewType": args.interview_type,
        "difficulty": args.difficulty,
        "questionCount": args.question_count,
        "timePerQuestion": args.time_per_question,
        "resumeContent": args.resume_content,
    }
    status, payload = request_json(
        "POST",
        build_url(args.backend_url, f"/v1/sessions/{session_id}/start"),
        args.token,
        body,
        args.timeout,
    )
    if status != 202:
        raise RuntimeError(f"unexpected start response: HTTP {status} {payload}")

    log(f"HTTP: start accepted for session {session_id}: {payload}")
    return payload


def fetch_state(args, session_id):
    status, payload = request_json(
        "GET",
        build_url(args.backend_url, f"/v1/sessions/{session_id}"),
        args.token,
        None,
        args.timeout,
    )
    if status != 200:
        raise RuntimeError(f"unexpected state response: HTTP {status} {payload}")
    return (extract_data(payload) or {}).get("sessionState")


def wait_for_question_message(sock, session_id, expected_type, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for frame in read_stomp_frames(sock, deadline):
            if frame.startswith("ERROR"):
                raise RuntimeError(frame.rstrip("\x00"))
            if not frame.startswith("MESSAGE"):
                continue

            body_text = parse_stomp_body(frame)
            try:
                body = json.loads(body_text)
            except json.JSONDecodeError:
                body = {"raw": body_text}

            if f"/topic/session/{session_id}/error" in frame:
                raise RuntimeError(f"backend reported session error: {body}")

            if f"/topic/session/{session_id}/question" not in frame:
                continue

            log(f"WebSocket: received question message: {json.dumps(body, ensure_ascii=False)}")
            if body.get("type") == expected_type:
                return body

    raise TimeoutError(f"{expected_type} was not received before timeout")


def submit_answer(sock, args, session_id):
    body = {
        "sessionId": session_id,
        "answer": args.answer,
        "emotionResult": args.emotion_result,
        "responseTimeSeconds": args.response_time_seconds,
    }
    body_text = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    send_frame = (
        "SEND\n"
        "destination:/app/session.answer\n"
        "content-type:application/json\n"
        f"content-length:{len(body_text.encode('utf-8'))}\n\n"
        f"{body_text}\x00"
    )
    sockjs_send_stomp(sock, send_frame)

    log(f"WebSocket: answer submitted for session {session_id}")
    return body


def main():
    parser = argparse.ArgumentParser(
        description="Check session start flow: HTTP start request and WebSocket question-ready signal."
    )
    parser.add_argument("--backend-url", default="http://localhost:8080/api")
    parser.add_argument("--token", default=os.getenv("ACCESS_TOKEN") or os.getenv("JWT_TOKEN"))
    parser.add_argument("--session-id", type=int)
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--category", default="BACKEND")
    parser.add_argument("--interview-type", default="TECH")
    parser.add_argument("--difficulty", default="NORMAL")
    parser.add_argument("--question-count", type=int, default=3)
    parser.add_argument("--time-per-question", type=int, default=30)
    parser.add_argument("--resume-content", default="Spring Boot and Redis project experience.")
    parser.add_argument(
        "--answer",
        default="Spring Boot 프로젝트에서 Redis를 사용해 세션 상태와 질문 생성 락을 관리했습니다.",
    )
    parser.add_argument("--emotion-result", default="NEUTRAL")
    parser.add_argument("--response-time-seconds", type=int, default=15)
    parser.add_argument(
        "--skip-follow-up",
        action="store_true",
        help="Only test initial question generation and skip answer based follow-up generation.",
    )
    args = parser.parse_args()

    try:
        session_id = args.session_id or create_session(args)
        with connect_and_subscribe(args, session_id) as sock:
            start_session(args, session_id)
            initial_message = wait_for_question_message(
                sock, session_id, "INITIAL_QUESTIONS_READY", args.timeout
            )
            follow_up_message = None
            if not args.skip_follow_up:
                submit_answer(sock, args, session_id)
                follow_up_message = wait_for_question_message(
                    sock, session_id, "NEXT_QUESTION", args.timeout
                )
        state = fetch_state(args, session_id)
    except Exception as exc:
        return fail(str(exc))

    log(f"PASS: INITIAL_QUESTIONS_READY received for session {session_id}")
    log(f"PASS: firstQuestion={initial_message.get('firstQuestion')}")
    if follow_up_message:
        log(f"PASS: NEXT_QUESTION received for session {session_id}")
        log(f"PASS: nextQuestion={follow_up_message.get('question')}")
    log(f"PASS: final sessionState={state}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

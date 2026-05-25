#!/usr/bin/env python3
"""
Interactive STOMP client for the Bakbak chat backend.

Supports:
  - SockJS WebSocket transport (default): http://localhost:8080/ws
  - Raw STOMP WebSocket: ws://localhost:8080/ws-native

Usage:
  pip install -r requirements.txt
  python websocket_client.py

Type chat messages at the prompt; enter ENDCHAT to disconnect and exit.
"""

from __future__ import annotations

import json
import random
import string
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Optional
from urllib.parse import urlparse

try:
    import websocket
except ImportError:
    print("Missing dependency. Install with: pip install -r requirements.txt", file=sys.stderr)
    sys.exit(1)

NULL = "\x00"
SOCKJS_WS_SUFFIX = "/websocket"
STOMP_SUBPROTOCOLS = ["v12.stomp", "v11.stomp", "v10.stomp"]


@dataclass
class StompFrame:
    command: str
    headers: dict[str, str]
    body: str = ""

    def __str__(self) -> str:
        header_lines = "\n".join(f"{k}:{v}" for k, v in self.headers.items())
        if self.body:
            return f"{self.command}\n{header_lines}\n\n{self.body}"
        if header_lines:
            return f"{self.command}\n{header_lines}"
        return self.command


def prompt(label: str, default: str = "") -> str:
    suffix = f" [{default}]" if default else ""
    value = input(f"{label}{suffix}: ").strip()
    return value or default


def prompt_required(label: str) -> str:
    while True:
        value = input(f"{label}: ").strip()
        if value:
            return value
        print("  (required)")


def normalize_token(raw: str) -> str:
    value = raw.strip()
    if value.lower().startswith("bearer "):
        return value[7:].strip()
    return value


def build_stomp_frame(command: str, headers: dict[str, str], body: Optional[str] = None) -> str:
    """Build a STOMP 1.2 frame terminated with a null octet."""
    frame_headers = dict(headers)
    body_text = "" if body is None else body

    if body is not None and "content-length" not in frame_headers:
        frame_headers["content-length"] = str(len(body_text.encode("utf-8")))

    header_lines = [command] + [f"{key}:{val}" for key, val in frame_headers.items()]
    header_block = "\n".join(header_lines)
    # STOMP requires a blank line between headers and body (even when body is empty).
    if body is not None:
        return f"{header_block}\n\n{body_text}{NULL}"
    return f"{header_block}\n\n{NULL}"


def parse_stomp_frame(raw: str) -> StompFrame:
    text = raw.rstrip(NULL)
    if "\n\n" in text:
        head, body = text.split("\n\n", 1)
    else:
        head, body = text, ""

    lines = head.split("\n")
    command = lines[0].strip()
    headers: dict[str, str] = {}
    for line in lines[1:]:
        if ":" in line:
            key, value = line.split(":", 1)
            headers[key] = value
    return StompFrame(command=command, headers=headers, body=body)


def unwrap_sockjs(raw: str) -> list[str]:
    """Decode SockJS WebSocket frames into zero or more STOMP payload strings."""
    if not raw:
        return []

    if raw == "o":
        return []

    if raw == "h":
        return []

    if raw.startswith("c"):
        raise ConnectionError(f"SockJS closed: {raw}")

    if raw.startswith("a") or raw.startswith("["):
        try:
            payload = raw[1:] if raw.startswith("a") else raw
            messages = json.loads(payload)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Invalid SockJS array frame: {raw!r}") from exc
        return [m for m in messages if isinstance(m, str)]

    # Some servers may pass through raw STOMP on the websocket transport.
    return [raw]


def wrap_sockjs(stomp_frame: str) -> str:
    """
    Encode a STOMP frame for SockJS WebSocket transport.

    Client → server uses a JSON array only: ["payload"].
    The leading 'a' prefix is server → client only (do not send it).
    """
    return json.dumps([stomp_frame])


def is_sockjs_http_base(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https"):
        return False
    path = parsed.path.rstrip("/") or "/"
    return path == "/ws" or path.endswith("/ws")


def is_native_ws_url(url: str) -> bool:
    parsed = urlparse(url)
    return parsed.scheme in ("ws", "wss") and parsed.path.rstrip("/").endswith("/ws-native")


def is_sockjs_ws_url(url: str) -> bool:
    parsed = urlparse(url)
    return parsed.scheme in ("ws", "wss") and url.rstrip("/").endswith(SOCKJS_WS_SUFFIX)


def resolve_websocket_url(entry_url: str) -> tuple[str, bool]:
    """
    Resolve the URL entered by the user to a WebSocket URL and transport mode.

    Returns (ws_url, use_sockjs).
    """
    entry = entry_url.strip()

    if is_native_ws_url(entry):
        return entry, False

    if is_sockjs_ws_url(entry):
        return entry, True

    if entry.startswith("ws://") or entry.startswith("wss://"):
        # e.g. ws://localhost:8080/ws without SockJS session path — prefer SockJS info dance
        parsed = urlparse(entry)
        if parsed.path.rstrip("/") == "/ws":
            http_base = urlunparse_http_from_ws(parsed)
            return build_sockjs_websocket_url(http_base), True
        return entry, False

    if is_sockjs_http_base(entry):
        return build_sockjs_websocket_url(entry), True

    raise ValueError(
        "Unsupported URL. Use http://host:port/ws (SockJS), "
        "ws://host:port/ws/{server}/{session}/websocket, or ws://host:port/ws-native"
    )


def urlunparse_http_from_ws(parsed) -> str:
    scheme = "https" if parsed.scheme == "wss" else "http"
    port = parsed.port
    netloc = parsed.hostname or "localhost"
    if port:
        netloc = f"{netloc}:{port}"
    path = parsed.path.rstrip("/") or "/ws"
    return f"{scheme}://{netloc}{path}"


def build_sockjs_websocket_url(http_base: str) -> str:
    """
    Perform SockJS /info handshake and build the WebSocket transport URL.

    See https://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html
    """
    base = http_base.rstrip("/")
    info_url = f"{base}/info"

    request = urllib.request.Request(info_url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            info = json.loads(response.read().decode("utf-8"))
    except urllib.error.URLError as exc:
        raise ConnectionError(f"SockJS info request failed for {info_url}: {exc}") from exc

    if not info.get("websocket", True):
        raise ConnectionError("Server info says WebSocket transport is not available")

    parsed = urlparse(base)
    ws_scheme = "wss" if parsed.scheme == "https" else "ws"
    host = parsed.hostname or "localhost"
    port = parsed.port
    if port is None:
        port = 443 if parsed.scheme == "https" else 80
    path = parsed.path or "/ws"

    server_id = f"{random.randint(0, 999):03d}"
    session_id = "".join(random.choices(string.ascii_lowercase + string.digits, k=8))
    return f"{ws_scheme}://{host}:{port}{path}/{server_id}/{session_id}{SOCKJS_WS_SUFFIX}"


class BakbakStompClient:
    def __init__(
        self,
        ws_url: str,
        jwt: str,
        conversation_id: int,
        use_sockjs: bool,
        stomp_host: str = "localhost",
        debug: bool = False,
    ) -> None:
        self.ws_url = ws_url
        self.jwt = jwt
        self.conversation_id = conversation_id
        self.use_sockjs = use_sockjs
        self.stomp_host = stomp_host
        self.debug = debug
        self._ws: Optional[websocket.WebSocket] = None
        self._stop = threading.Event()
        self._receiver: Optional[threading.Thread] = None
        self._send_lock = threading.Lock()
        self._connected = threading.Event()

    def connect(self) -> None:
        print(f"Connecting to {self.ws_url} ({'SockJS' if self.use_sockjs else 'raw STOMP'})...")
        self._ws = websocket.create_connection(
            self.ws_url,
            timeout=15,
            subprotocols=STOMP_SUBPROTOCOLS,
        )

        if self.use_sockjs:
            opening = self._ws.recv()
            if opening != "o":
                raise ConnectionError(f"Expected SockJS open frame 'o', got: {opening!r}")
            print("SockJS session open.")

        connect_frame = build_stomp_frame(
            "CONNECT",
            {
                "accept-version": "1.2,1.1,1.0",
                "heart-beat": "10000,10000",
                "host": self.stomp_host,
                "Authorization": f"Bearer {self.jwt}",
            },
        )
        self._send_raw(connect_frame)
        self._expect_stomp_command("CONNECTED", timeout=15)

        subscribe_frame = build_stomp_frame(
            "SUBSCRIBE",
            {
                "id": "sub-0",
                "destination": f"/topic/conversation/{self.conversation_id}",
            },
        )
        self._send_raw(subscribe_frame)
        # Spring's simple in-memory broker often does not send SUBSCRIBED, but the
        # subscription still works. Brief wait for SUBSCRIBED/ERROR only (main thread).
        self._wait_for_subscribe_ack(timeout=2.0)
        print(f"Listening on /topic/conversation/{self.conversation_id}")

        self._receiver = threading.Thread(target=self._receive_loop, name="stomp-recv", daemon=True)
        self._receiver.start()

    def _send_raw(self, stomp_frame: str) -> None:
        if not self._ws:
            raise RuntimeError("Not connected")
        payload = wrap_sockjs(stomp_frame) if self.use_sockjs else stomp_frame
        if self.debug:
            preview = payload if len(payload) < 200 else payload[:200] + "..."
            print(f"[debug] send ({len(payload)} bytes): {preview!r}")
        with self._send_lock:
            self._ws.send(payload)

    def _recv_stomp_frames(self, timeout: float) -> list[StompFrame]:
        """Read one WebSocket message and decode STOMP frames (handles SockJS wrapping)."""
        assert self._ws is not None
        self._ws.settimeout(timeout)
        try:
            raw = self._ws.recv()
        except websocket.WebSocketTimeoutException:
            return []
        if self.debug and raw:
            preview = raw if len(raw) < 200 else raw[:200] + "..."
            print(f"[debug] recv ({len(raw)} bytes): {preview!r}")
        payloads = unwrap_sockjs(raw) if self.use_sockjs else [raw]
        return [parse_stomp_frame(p) for p in payloads if p]

    def _wait_for_subscribe_ack(self, timeout: float = 2.0) -> None:
        """Optionally consume SUBSCRIBED or ERROR after SUBSCRIBE; do not fail if absent."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            remaining = max(0.1, deadline - time.monotonic())
            try:
                frames = self._recv_stomp_frames(remaining)
            except websocket.WebSocketTimeoutException:
                frames = []
            for frame in frames:
                if frame.command.upper() == "ERROR":
                    raise ConnectionError(
                        f"STOMP ERROR on subscribe: {frame.headers.get('message', frame.body or frame)}"
                    )
                if frame.command.upper() == "SUBSCRIBED":
                    sub = frame.headers.get("subscription") or frame.headers.get("id", "")
                    print(f"Subscribed (ack id={sub})")
                    return
        if self.debug:
            print("[debug] no SUBSCRIBED frame (normal for Spring simple broker)")

    def _expect_stomp_command(self, expected: str, timeout: float = 10) -> StompFrame:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            remaining = max(0.1, deadline - time.monotonic())
            try:
                frames = self._recv_stomp_frames(remaining)
            except websocket.WebSocketTimeoutException:
                frames = []
            for frame in frames:
                if frame.command.upper() == "ERROR":
                    raise ConnectionError(
                        f"STOMP ERROR: {frame.headers.get('message', frame.body or frame)}"
                    )
                if frame.command.upper() == expected:
                    if expected == "CONNECTED":
                        self._connected.set()
                        print(f"STOMP connected (version={frame.headers.get('version', '?')})")
                    return frame
        raise ConnectionError(
            f"Timed out waiting for STOMP {expected} after {timeout:.0f}s. "
            "SockJS opened, but the server did not answer STOMP. "
            "Try: (1) fresh JWT from POST /api/auth/login, (2) ws://localhost:8080/ws-native, "
            "(3) confirm backend is running and check logs for stompSubProtocol CONNECT(0) vs CONNECT(1)."
        )

    def send_chat(self, content: str) -> None:
        body = json.dumps({"content": content})
        send_frame = build_stomp_frame(
            "SEND",
            {
                "destination": f"/app/chat/{self.conversation_id}",
                "content-type": "application/json",
            },
            body=body,
        )
        self._send_raw(send_frame)

    def disconnect(self) -> None:
        self._stop.set()
        if self._ws:
            try:
                if self._connected.is_set():
                    self._send_raw(build_stomp_frame("DISCONNECT", {}))
            except Exception:
                pass
            try:
                self._ws.close()
            except Exception:
                pass
        if self._receiver and self._receiver.is_alive():
            self._receiver.join(timeout=2)

    def _receive_loop(self) -> None:
        assert self._ws is not None
        while not self._stop.is_set():
            try:
                self._ws.settimeout(1.0)
                raw = self._ws.recv()
            except websocket.WebSocketTimeoutException:
                continue
            except Exception as exc:
                if not self._stop.is_set():
                    print(f"\n[connection closed: {exc}]")
                break

            try:
                payloads = unwrap_sockjs(raw) if self.use_sockjs else [raw]
                for payload in payloads:
                    self._handle_stomp(payload)
            except Exception as exc:
                print(f"\n[frame error: {exc}]")

    def _handle_stomp(self, payload: str) -> None:
        if not payload or payload == "\x00":
            return

        frame = parse_stomp_frame(payload)
        command = frame.command.upper()

        if command == "CONNECTED":
            return

        if command == "MESSAGE":
            self._print_incoming_message(frame)
            return

        if command == "ERROR":
            print(f"\n[STOMP ERROR] {frame.headers.get('message', frame.body or frame)}")
            return

        if command in ("RECEIPT", "SUBSCRIBED"):
            dest = frame.headers.get("destination") or frame.headers.get("subscription", "")
            print(f"[{command}] {dest}".strip())
            return

        print(f"[{command}] {frame}")

    def _print_incoming_message(self, frame: StompFrame) -> None:
        destination = frame.headers.get("destination", "")
        body = frame.body.strip()
        try:
            data = json.loads(body)
            sender = data.get("senderUsername") or data.get("senderId", "?")
            text = data.get("content", body)
            print(f"\n<< {sender}: {text}")
        except json.JSONDecodeError:
            print(f"\n<< [{destination}] {body}")
        print("> ", end="", flush=True)


def run_repl(client: BakbakStompClient) -> None:
    print(
        "\nChat ready. Type a message and press Enter to send.\n"
        "Commands: ENDCHAT (quit)\n"
    )
    while True:
        try:
            line = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break

        if not line:
            continue
        if line.upper() == "ENDCHAT":
            break
        try:
            client.send_chat(line)
        except Exception as exc:
            print(f"[send failed: {exc}]")


def main() -> None:
    print("Bakbak STOMP WebSocket client\n")

    default_url = "http://localhost:8080/ws"
    entry_url = prompt("STOMP endpoint (http://host/ws for SockJS, or ws://host/ws-native)", default_url)
    token_raw = prompt_required("JWT (paste token, with or without 'Bearer ' prefix)")
    jwt = normalize_token(token_raw)
    conv_raw = prompt_required("Conversation ID")
    try:
        conversation_id = int(conv_raw)
    except ValueError:
        print("Conversation ID must be a number.", file=sys.stderr)
        sys.exit(1)

    try:
        ws_url, use_sockjs = resolve_websocket_url(entry_url)
    except ValueError as exc:
        print(exc, file=sys.stderr)
        sys.exit(1)

    parsed = urlparse(ws_url)
    stomp_host = parsed.hostname or "localhost"
    debug = prompt("Debug logging (y/N)", "n").lower() in ("y", "yes")

    client = BakbakStompClient(
        ws_url, jwt, conversation_id, use_sockjs, stomp_host=stomp_host, debug=debug
    )

    try:
        client.connect()
        run_repl(client)
    except Exception as exc:
        print(f"Failed: {exc}", file=sys.stderr)
        sys.exit(1)
    finally:
        print("Disconnecting...")
        client.disconnect()
        print("Goodbye.")


if __name__ == "__main__":
    main()

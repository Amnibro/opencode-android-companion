#!/usr/bin/env python3
"""Print a one-time pairing QR for an OpenCode server on this machine.

The phone scans the code, redeems the secret once, stores the credential,
and opens the session list. The secret is not written to the log.

Windows:  py -3 tools\\pair.py
macOS:    python3 tools/pair.py
Linux:    python3 tools/pair.py

Put the server password in OPENCODE_PASSWORD. Do not pass it as an argument.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import secrets
import socket
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


TTL = 300


def lan_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def basic(password: str) -> str:
    token = base64.b64encode(f"opencode:{password}".encode()).decode()
    return f"Basic {token}"


class Broker:
    def __init__(self, password: str) -> None:
        self.password = password
        self.pending: dict[str, float] = {}
        self.used: set[str] = set()

    def issue(self) -> tuple[str, int]:
        secret = secrets.token_urlsafe(18).replace("-", "a").replace("_", "b")
        exp = time.time() + TTL
        self.pending[secret] = exp
        return secret, int(exp)

    def redeem(self, secret: str) -> int:
        if secret in self.used:
            return 410
        exp = self.pending.pop(secret, None)
        if exp is None:
            return 404
        self.used.add(secret)
        if time.time() > exp:
            return 410
        return 200


def payload(url: str, secret: str, exp: int, redeem: str) -> str:
    body = {"v": 1, "url": url, "secret": secret, "exp": exp}
    if redeem:
        body["redeem"] = redeem
    return json.dumps(body, separators=(",", ":"))


def official_payload(url: str, code: str) -> str:
    return json.dumps({"code": code, "urls": [url]}, separators=(",", ":"))


def write_qr(text: str, png: str) -> None:
    import segno

    segno.make(text, error="m").save(png, scale=8, border=4)


def print_qr(text: str) -> None:
    import segno

    segno.make(text, error="m").terminal(compact=True)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    broker: Broker
    fixture: bool

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s %s\n" % (self.command, urlsplit(self.path).path))

    def _send(self, status: int, body: bytes, content: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, status: int, obj: dict) -> None:
        self._send(status, json.dumps(obj, separators=(",", ":")).encode(), "application/json")

    def _authed(self) -> bool:
        return self.headers.get("Authorization", "") == basic(self.broker.password)

    def do_GET(self) -> None:
        path = urlsplit(self.path).path
        if path.startswith("/auth/connect/"):
            code = path[len("/auth/connect/") :]
            status = self.broker.redeem(code)
            if status != 200:
                self._json(status, {})
                return
            self._json(200, {"token": self.broker.password})
            return
        if not self.fixture:
            self._json(404, {})
            return
        if not self._authed():
            self._json(401, {})
            return
        if path == "/api/info":
            self._json(200, {"version": "pair-fixture"})
            return
        if path == "/api/project":
            self._json(200, [{"id": "p1", "name": "Pair fixture", "canonical": "/tmp/pair", "sandboxes": [], "time": {"created": 1}}])
            return
        if path == "/api/session":
            self._json(200, {"data": [{"id": "ses_pair", "projectID": "p1", "title": "Paired from the QR"}], "cursor": {}})
            return
        self._json(404, {})

    def do_POST(self) -> None:
        path = urlsplit(self.path).path
        n = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(n) if n else b""
        if path != "/pair/redeem":
            self._json(404, {})
            return
        try:
            secret = json.loads(raw.decode() or "{}").get("secret", "")
        except json.JSONDecodeError:
            secret = ""
        status = self.broker.redeem(str(secret))
        if status != 200:
            self._json(status, {})
            return
        self._json(200, {"token": self.broker.password})


def main() -> int:
    ap = argparse.ArgumentParser(description="Print a one-time OpenCode pairing QR.")
    ap.add_argument("--port", type=int, default=8767)
    ap.add_argument("--advertise", default="", help="Host written into the QR. Defaults to this machine's LAN address.")
    ap.add_argument("--alongside", default="", help="Existing server URL, for example http://127.0.0.1:4096")
    ap.add_argument("--tunnel", default="", help="OpenTunnel or other HTTPS URL to put in the QR instead of a LAN address.")
    ap.add_argument("--official", action="store_true", help="Print the {code,urls} QR shape described for opencode pair.")
    ap.add_argument("--png", default="")
    ap.add_argument("--payload-out", default="", help="Write the QR text to this file. It contains the secret.")
    ap.add_argument("--seconds", type=int, default=0, help="Exit after this many seconds. 0 keeps serving.")
    args = ap.parse_args()
    password = os.environ.get("OPENCODE_PASSWORD", "")
    if not password:
        sys.stderr.write("Set OPENCODE_PASSWORD. It is not read from arguments and is not logged.\n")
        return 2
    if args.official and (args.alongside or args.tunnel):
        sys.stderr.write("--official is served by this script. Leave off --alongside and --tunnel.\n")
        return 2
    seen = args.advertise or lan_ip()
    if args.tunnel:
        server_url = args.tunnel.rstrip("/")
    elif args.alongside:
        parts = urlsplit(args.alongside)
        port = parts.port or (443 if parts.scheme == "https" else 80)
        server_url = f"{parts.scheme}://{seen}:{port}"
    else:
        server_url = f"http://{seen}:{args.port}"
    broker = Broker(password)
    secret, exp = broker.issue()
    redeem = ""
    if args.alongside or args.tunnel:
        redeem = f"http://{seen}:{args.port}/pair/redeem"
    text = official_payload(server_url, secret) if args.official else payload(server_url, secret, exp, redeem)
    if args.official:
        # The same secret is the connect code. A second issue would be a different code.
        pass
    print("Pairing code expires in 5 minutes. Scan it with Companion.")
    print(f"Server {server_url}")
    try:
        print_qr(text)
    except Exception:
        sys.stderr.write("Install the Python segno package to draw the terminal QR.\n")
    if args.png:
        write_qr(text, args.png)
        print(f"Wrote {args.png}")
    if args.payload_out:
        with open(args.payload_out, "w", encoding="utf-8") as fh:
            fh.write(text)
        try:
            os.chmod(args.payload_out, 0o600)
        except OSError:
            pass
    Handler.broker = broker
    Handler.fixture = not (args.alongside or args.tunnel)
    httpd = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    if args.seconds > 0:
        httpd.timeout = 0.5
        deadline = time.time() + args.seconds
        while time.time() < deadline:
            httpd.handle_request()
        return 0
    print(f"Listening on 0.0.0.0:{args.port}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

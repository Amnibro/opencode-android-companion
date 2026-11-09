#!/usr/bin/env python3
"""Local mock of the documented OpenCode HTTP slice.

Speaks only the routes this companion calls. It never contacts a model
provider. The fixture password is checked and then discarded; it is not
written to the log.
"""

from __future__ import annotations

import base64
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


PASSWORD = os.environ.get("COMPANION_PASSWORD", "")
FORBIDDEN = {"hit": False, "paths": []}


def expected_basic() -> str:
    token = base64.b64encode(f"opencode:{PASSWORD}".encode()).decode()
    return f"Basic {token}"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:
        # Method and path only. Never the Authorization header.
        sys.stderr.write("%s %s\n" % (self.command, urlsplit(self.path).path))

    def _auth_ok(self) -> bool:
        got = self.headers.get("Authorization", "")
        return bool(PASSWORD) and got == expected_basic()

    def _read_json(self) -> dict:
        n = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(n) if n else b""
        if not raw:
            return {}
        return json.loads(raw.decode())

    def _send(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _json(self, status: int, obj) -> None:
        raw = json.dumps(obj, separators=(",", ":")).encode()
        self._send(status, raw, "application/json")

    def _unauthorized(self) -> None:
        self._json(401, {"_tag": "UnauthorizedError", "message": "unauthorized"})

    def _guard(self) -> bool:
        path = urlsplit(self.path).path
        if path.startswith("/api/shell") or path.endswith("/shell"):
            FORBIDDEN["hit"] = True
            FORBIDDEN["paths"].append(path)
        if not self._auth_ok():
            self._unauthorized()
            return False
        return True

    def do_GET(self) -> None:  # noqa: N802
        if not self._guard():
            return
        path = urlsplit(self.path).path
        if path == "/api/info":
            self._json(
                200,
                {
                    "version": "9.9.9-contract",
                    "pid": 1,
                    "hostname": "127.0.0.1",
                    "urls": ["http://127.0.0.1/"],
                    "paths": {"config": "/tmp/opencode-contract"},
                },
            )
            return
        if path == "/api/project":
            self._json(
                200,
                [
                    {
                        "id": "proj_contract",
                        "canonical": "/contract/notes",
                        "name": "Contract notes",
                        "sandboxes": [],
                        "time": {"created": 1, "updated": 1, "active": 1},
                    }
                ],
            )
            return
        if path == "/api/session":
            self._json(
                200,
                {
                    "data": [
                        {
                            "id": "ses_contract",
                            "projectID": "proj_contract",
                            "title": "Contract session",
                            "cost": 0,
                            "tokens": {"input": 0, "output": 0, "reasoning": 0, "cache": {}},
                            "time": {"created": 1, "updated": 1},
                            "location": {"directory": "/contract/notes"},
                        }
                    ],
                    "cursor": {},
                },
            )
            return
        if path == "/api/session/ses_contract/message":
            self._json(
                200,
                {
                    "data": [
                        {
                            "id": "msg_user",
                            "type": "user",
                            "text": "contract hello",
                            "time": {"created": 1},
                        }
                    ],
                    "cursor": {},
                },
            )
            return
        if path == "/api/event":
            event = {
                "id": "evt_1",
                "type": "server.connected",
                "data": {"ok": True},
            }
            perm = {
                "id": "evt_2",
                "created": 1,
                "type": "permission.asked",
                "data": {
                    "id": "perm_contract",
                    "sessionID": "ses_contract",
                    "action": "bash",
                    "resources": ["echo contract"],
                    "message": "Contract permission. Do not run it from the mock.",
                },
            }
            body = (
                "event: server.connected\n"
                f"data: {json.dumps(event)}\n\n"
                "event: permission.asked\n"
                f"data: {json.dumps(perm)}\n\n"
            ).encode()
            self._send(200, body, "text/event-stream")
            return
        self._json(404, {"_tag": "UnknownError", "message": "not in the contract slice"})

    def do_POST(self) -> None:  # noqa: N802
        if not self._guard():
            return
        path = urlsplit(self.path).path
        if path == "/api/session/ses_contract/prompt":
            payload = self._read_json()
            if set(payload) - {"text", "id", "files", "agents", "skills", "metadata", "delivery", "resume"}:
                self._json(400, {"_tag": "InvalidRequestError", "message": "unexpected field"})
                return
            if "text" not in payload:
                self._json(400, {"_tag": "InvalidRequestError", "message": "text required"})
                return
            self._json(
                200,
                {
                    "data": {
                        "id": "in_1",
                        "sessionID": "ses_contract",
                        "type": "user",
                        "time": {"created": 1},
                        "delivery": "queue",
                        "payload": {"text": payload["text"]},
                    }
                },
            )
            return
        if path == "/api/session/ses_contract/permission/perm_contract/reply":
            payload = self._read_json()
            if payload.get("decision") not in {"once", "always", "reject"}:
                self._json(400, {"_tag": "InvalidRequestError", "message": "bad decision"})
                return
            if set(payload) - {"decision", "message"}:
                self._json(400, {"_tag": "InvalidRequestError", "message": "unexpected field"})
                return
            self._send(204, b"", "application/json")
            return
        if path == "/api/session/ses_contract/interrupt":
            self._json(200, {"interrupted": True})
            return
        if path.startswith("/api/shell") or path.endswith("/shell"):
            self._json(403, {"_tag": "Forbidden", "message": "shell is not called by this client"})
            return
        self._json(404, {"_tag": "UnknownError", "message": "not in the contract slice"})


def main() -> None:
    if not PASSWORD:
        print("COMPANION_PASSWORD is required", file=sys.stderr)
        sys.exit(2)
    host = "127.0.0.1"
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    server = ThreadingHTTPServer((host, port), Handler)
    print(f"{server.server_address[0]} {server.server_address[1]}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()

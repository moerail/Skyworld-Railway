"""STA Remote/1 TLS client. No HTTP transport or third-party packages."""
from __future__ import annotations

import hashlib
import hmac
import json
import socket
import ssl
import uuid


class RemoteError(Exception):
    pass


class Remote:
    def __init__(self, host: str, port: int, fingerprint: str, admin: str, token: str):
        expected = fingerprint.lower().replace(":", "").replace(" ", "")
        if len(expected) != 64 or any(ch not in "0123456789abcdef" for ch in expected):
            raise RemoteError("Expected a SHA-256 server certificate fingerprint")
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.minimum_version = ssl.TLSVersion.TLSv1_3
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        raw = socket.create_connection((host, port), timeout=10)
        try:
            self.socket = context.wrap_socket(raw, server_hostname=host)
            certificate = self.socket.getpeercert(binary_form=True)
            if not certificate:
                raise RemoteError("Server did not provide a certificate")
            actual = hashlib.sha256(certificate).hexdigest()
            if not hmac.compare_digest(actual, expected):
                raise RemoteError("Server certificate fingerprint mismatch")
            self.socket.settimeout(60)
            self.file = self.socket.makefile("rb")
            self._send({"type": "auth", "admin": admin, "token": token})
            answer = self._receive()
            if answer.get("status") != "OK" or answer.get("protocol") != "STA_REMOTE/1":
                raise RemoteError("Authentication rejected")
        except Exception:
            if hasattr(self, "socket"):
                self.socket.close()
            raw.close()
            raise

    def _send(self, value: dict) -> None:
        data = (json.dumps(value, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8")
        if len(data) > 4096:
            raise RemoteError("Request too large")
        self.socket.sendall(data)

    def _receive(self) -> dict:
        line = self.file.readline(10_000_000)
        if not line or not line.endswith(b"\n"):
            raise RemoteError("Connection closed or response too large")
        message = json.loads(line)
        if message.get("protocol") != "STA_REMOTE/1":
            raise RemoteError("Unexpected protocol")
        return message

    def call(self, operation: str, **fields) -> dict:
        request_id = str(uuid.uuid4())
        self._send({"id": request_id, "type": operation, **fields})
        answer = self._receive()
        if answer.get("id") != request_id:
            raise RemoteError("Response ID mismatch")
        return answer

    def close(self) -> None:
        try:
            self.file.close()
        finally:
            self.socket.close()

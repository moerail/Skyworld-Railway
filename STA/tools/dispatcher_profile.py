"""Public connection settings for the STA dispatcher; never store an admin token."""
from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


PROFILE_NAME = "SkyRail-Dispatcher-profile.json"
_FIELDS = {"schemaVersion", "host", "port", "fingerprint", "admin"}


class ProfileError(ValueError):
    pass


@dataclass(frozen=True)
class ConnectionProfile:
    host: str
    port: int
    fingerprint: str
    admin: str


def default_profile_path() -> Path:
    executable = sys.executable if getattr(sys, "frozen", False) else sys.argv[0]
    return Path(executable).resolve().parent / PROFILE_NAME


def load_profile(path: Path, *, required: bool = False) -> ConnectionProfile | None:
    path = Path(path)
    if not path.is_file():
        if required:
            raise ProfileError(f"Connection profile not found: {path}")
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ProfileError(f"Cannot read connection profile: {path}") from exc
    if not isinstance(value, dict) or set(value) != _FIELDS or type(value.get("schemaVersion")) is not int \
            or value["schemaVersion"] != 1:
        raise ProfileError("Connection profile must contain only schemaVersion, host, port, fingerprint and admin")
    host = value["host"]
    port = value["port"]
    fingerprint = value["fingerprint"]
    admin = value["admin"]
    if not isinstance(host, str) or not host or len(host) > 253 or re.search(r"\s|[\x00-\x1f]", host):
        raise ProfileError("Invalid server host in connection profile")
    if type(port) is not int or not 1 <= port <= 65535:
        raise ProfileError("Invalid server port in connection profile")
    if not isinstance(fingerprint, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", fingerprint):
        raise ProfileError("Invalid SHA-256 certificate fingerprint in connection profile")
    if not isinstance(admin, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", admin):
        raise ProfileError("Invalid administrator ID in connection profile")
    return ConnectionProfile(host, port, fingerprint.lower(), admin)

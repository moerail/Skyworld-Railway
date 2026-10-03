"""Connection profile validation for the bundled desktop dispatcher."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from dispatcher_profile import ProfileError, load_profile


class ConnectionProfileTest(unittest.TestCase):
    def test_public_profile_loads_without_token(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "profile.json"
            path.write_text(json.dumps({"schemaVersion": 1, "host": "rail.example", "port": 8766,
                                        "fingerprint": "AB" * 32, "admin": "dispatcher1"}), encoding="utf-8")
            profile = load_profile(path, required=True)
            self.assertEqual(("rail.example", 8766, "ab" * 32, "dispatcher1"),
                             (profile.host, profile.port, profile.fingerprint, profile.admin))
            self.assertFalse(hasattr(profile, "token"))

    def test_token_and_unknown_fields_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "profile.json"
            path.write_text(json.dumps({"schemaVersion": 1, "host": "localhost", "port": 8766,
                                        "fingerprint": "ab" * 32, "admin": "dispatcher1",
                                        "token": "never-save-this"}), encoding="utf-8")
            with self.assertRaises(ProfileError):
                load_profile(path, required=True)

    def test_missing_optional_profile(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "missing.json"
            self.assertIsNone(load_profile(path))
            with self.assertRaises(ProfileError):
                load_profile(path, required=True)

    def test_invalid_port_and_fingerprint(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "profile.json"
            values = {"schemaVersion": 1, "host": "localhost", "port": 8766,
                      "fingerprint": "ab" * 32, "admin": "dispatcher1"}
            for key, invalid in (("port", 0), ("port", True), ("fingerprint", "ab")):
                with self.subTest(key=key, invalid=invalid):
                    candidate = dict(values, **{key: invalid})
                    path.write_text(json.dumps(candidate), encoding="utf-8")
                    with self.assertRaises(ProfileError):
                        load_profile(path, required=True)


if __name__ == "__main__":
    unittest.main()

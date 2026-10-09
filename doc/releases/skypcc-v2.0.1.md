# SkyPCC 2.0.1 — remote token control candidate

Compatible suite baseline: SkyTrainFolia 4.0.2, STCS 4.0.2, SkyworldTrainAPI 2.0.0. This patch changes only SkyPCC's HTTP authorization gate; the in-process protocols and request bodies are unchanged. Production validation is pending.

## Changes

- `/api/v5/switch` and `/api/v6/sr` accept remote POST requests with `Content-Type: application/json` and the correct `Authorization: Bearer <token>` header.
- The old loopback-address and localhost Origin/Host restrictions are removed. Invalid, missing, or too-short configured tokens still deny control. `web.control-enabled: false` still disables control.
- The denial code is now `CONTROL_AUTH_REQUIRED`, with updated web translations. The operator dialog still clears the entered token after submission and does not place it in a URL.

## Configuration and deployment

Existing production `config.yml` is retained on upgrade. Set `web.control-enabled: true` and a private `web.control-token` of at least 32 characters. The default bind address remains `127.0.0.1`; use an HTTPS reverse proxy for remote access or explicitly configure the bind address and protected transport. Do not copy a real token into the repository.

Stop the server, back up the old SkyPCC JAR and configuration, replace only the SkyPCC JAR, then start the server and refresh the browser. Ensure no duplicate SkyPCC JAR remains in `plugins/`.

## Acceptance checks

1. From the remote SkyPCC page, a wrong token is rejected for both switch conversion and SR approval with `CONTROL_AUTH_REQUIRED`.
2. A correct token reaches the existing switch and SR gateways; observe their actual result. A pending or unconfirmed switch response is not proof of physical conversion.
3. With `web.control-enabled: false`, both writes are denied. Read-only map and status endpoints continue to work.

The JAR must not be exposed over plain HTTP on an untrusted network because the bearer token would travel in cleartext.

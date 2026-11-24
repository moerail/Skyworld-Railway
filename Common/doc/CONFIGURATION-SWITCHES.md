# MA exit capacity and PCC HTTPS policy

Both switches default to `true`, including existing configurations that omit the
new keys. Change the corresponding plugin's `config.yml`, restart the server,
and refresh the PCC page.

Validation: 91 Java test entry points passed on each of Shiroha 26.2 and 26.3.
Browser tests covered missing/enabled/disabled policy on HTTP, enabled policy on
HTTPS, all three control actions, v4 UUID fallback and stable IDs during polling.
Existing desktop/mobile language and static-graph polling/SSE regressions passed.

## STCS: whole-train exit capacity

In `plugins/STCS/config.yml`:

```yaml
ma:
  check-exit-capacity: true
```

With `true`, a grant through a point needs enough free, granted route beyond the
throat for the whole train and its margin. With `false`, MA may extend into an
exit that is too short to fit the entire train, or be clipped inside that exit.
The setting applies to FS, SH and SR and survives dynamic lock-range updates.
Point state, lock range, occupancy/resource conflicts, look-ahead and maximum MA
distance continue to limit grants. Retained occupancy and grant acknowledgement
are unchanged.

中文：`ma.check-exit-capacity: false` 关闭发放 MA 时的整列车出口容量检查。
关闭后列车可能在尚未完全清出道岔时停车；占用冲突、道岔状态和距离限制仍生效。

## SkyPCC: browser HTTPS requirement

In `plugins/SkyPCC/config.yml`:

```yaml
web:
  require-https-for-control: true
```

`true` keeps the existing browser requirement for a secure context: HTTPS or a
browser-trusted localhost origin. `false` allows switch changes, SR approvals and
MA revocation from HTTP pages. Control still requires `web.control-enabled: true`
and a matching Bearer token of at least 32 characters; keep real tokens private.

The public `/api/v5/config` response exposes `requireHttpsForControl`, without
exposing the token. Older responses missing the field retain the HTTPS
requirement. On HTTP, request UUIDs use `crypto.getRandomValues` when
`crypto.randomUUID` is unavailable, retaining the same UUID throughout polling.

中文：`web.require-https-for-control: false` 允许 HTTP 网页进行控制操作。
此项控制网页的 HTTPS 门槛，HTTP 监听方式及服务端 Bearer 鉴权保持现有方式。
HTTP 会明文传输令牌，应仅在可信网络中使用；公网连接建议保持 HTTPS。

For HTTPS access keep the existing reverse-proxy/TLS setup. This switch does not
provision a certificate or replace the HTTP listener with a TLS listener.

# MA exit capacity and PCC HTTPS policy

**[修改 · STF 5.1.3 · 2026-11-26]** 自动驾驶不再固定 P2/B4：起步默认 P4，接近目标速度收档，常用制动默认 B6，停车保持 B7。可配置档位上限；MA、ATP、TIMS 和车型限制不变。 [档位配置](AUTOMATIC-DRIVING-HANDLES.md)

**[修正 · SAL0.7.6 · 2026-11-26]** 回库库位复核、障碍保持、等待重新选位时维护原已接受进路的静默续租，避免超过1500ms心跳失效。复核默认2秒；车身暂不确定不再误报拓扑改变并立即丢目标。持续未知停为FAULT，真实空间／库位问题停稳后重选；状态显示具体原因并明确区分历史派车记录。 [详细说明](SAL-RETURN-RECHECK.md)

**[新增／修正 · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** 轨道坐标和方向统一取自同一份STF遥测，避免转弯时混用不同帧。定位暂态立即制动、默认复核2秒；节点容差0.75格、投影误差仍默认1.5格，均可有限配置。两份独立观测确认不同分支则提前恢复；不新建或扩大MA。原始故障持久保留并在PCC事件正文与/sal status直接显示。 [详细契约](SAL-LOCALISATION-TOLERANCE.md)

**[2026-11-26 · SAL0.7.0]** New keys in SAL config.yml: dispatch.batch-window-ms=500 (100–5000), request-timeout-ms=120000 (5000–600000), compute-budget-ms=500 (50–2000), last-vehicle-penalty-seconds=0 (0–3600). Missing keys use defaults. Pure matching runs on a dedicated JVM worker. Supply mode, preference seconds and pickup cap are per-route draft/publication policy, edited in PCC or /sal route supply; they are not global config keys. [Dispatch contract](SAL-SHARED-DISPATCH.en.md)

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


## **[修改 · 2026-11-25] 中心停车与库位容量**

**[破坏性修改 · 2026-11-25]** depot改为列车中心对牌；空间不足时只在本库位边界内调整计划中心，整列放不下则换位。同股道相邻库位沿轨中分并留净间隔；单端深处先停，双端评估两侧合法路径；出入库都排除车身阻挡。PCC显示容量、类型、范围和目标偏移。旧车头停车位置须手动调整，station语义不变。

配套 STA2.8.0 / STF5.1.0 / STCS4.3.5 / SAL0.6.0 / PCC2.5.0；以上旧版章节保留为历史实现说明，depot旧领车参考以本次中心参考为准。[完整边界、配置、契约与重启规则](SAL-CENTRE-STABLING.md) · [English](SAL-CENTRE-STABLING.en.md)

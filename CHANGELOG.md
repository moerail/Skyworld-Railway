# Updates / 更新说明

## Experimental M3 manual-train protection / M3 手动列车实验保护 - 2026-09-25

**Pre-release: `suite-v4.0.0`.** 73 Java test entry points and PCC edge-line / four-language resource checks passed before publication. The latest fixes have not completed another live-server acceptance run.

**预发布：`suite-v4.0.0`。** 发布前已通过 73 个 Java 测试入口及 PCC 线路归属、四语言资源检查；最近修复尚未完成新一轮服务器验收。

Release set: **STF 4.0.0 / STCS 4.0.0 / STA 2.0.0 / SkyPCC 2.0.0**. Built locally; live-server acceptance is still pending. Upgrade installed components together after backing up RailGraph and retained occupancy. Do not mix binaries.

- Added a separate STA v6 operational-authority service. Manual trains alone use `SB/FS/SH/SR/TR/PT`; `/stcs admin enforce true|false` explicitly changes the ATP channel while stopped. Shadow MA/EoA remains observational.
- Added conservative onboard MA/EoA supervision with configurable relaxed/strict braking profiles, last-confirmed EoA fallback within the same graph/control session, trip acknowledgement, and a latched EB for unresponsive ordinary overspeed. New grants are acknowledged before earlier forward reservations are released.
- Added bounded SH requests and dispatcher-approved SR to a selected infrastructure node, exposed through an authenticated PCC control and `/stcs sr` admin command. MA loss produces a deduplicated PCC warning. SR approval is a service/event operation, not an ETCS telegram; executable MA reuses private Message 1003 / Packet 1015.
- Cab ATP mode now displays translated **channel | unchanged operating code**, for example `Enforced | SR`. STF's built-in pseudo-ATO automatic trains are excluded, including passenger-entered MA/mode commands.
- Fixed Enforced driver feedback: near-limit/overspeed warning playback now accepts the active channel. The sidebar and BossBar identify actual ATP B7/EB intervention, with short localized reasons in the sidebar. Configurable one-shot service/emergency cues play on intervention transitions, not repeatedly during a stationary SB hold.
- SH/SR now apply B7 immediately above their configured mode speed ceiling, independently of the relaxed FS overspeed timer. EoA overrun sends a localized TR message and emergency cue to the driver; the sidebar and BossBar retain the ATP EB indication.
- SR target reachability is checked over the saved graph independently of the short rolling MA window. Targets beyond the initial window can be approved, but each issued grant remains bounded by locally verified occupancy and switch state. Graph gaps, unknown points and conflicts still prevent unsafe extension. Added SR route regression tests and localized SR/Trip command results in STF and PCC.
- This is experimental game software, not certified ATP, certified interlocking or ETCS compliance. A successful local build or a browser display is not a safety acceptance test.

本轮仅手动列车进入运行模式状态机；影子通道继续只读，自动列车保持 STF 内置伪 ATO 行为。强制保护需管理员在停车时显式启用，并经过实服验证。四插件及中英法日荷文档同步更新，不清空轨道图和占用账本。

## Breaking STA v5 / 破坏性协议升级 - 2026-09-23

Release set: **STF 3.0.0 / STCS 3.0.0 / STA 1.0.0 / SkyPCC 1.0.0**. Not yet deployed or published.

- Moved v2/v4 service contracts to v5; version-first decoding rejects legacy message IDs. PCC uses `/api/v5/` only.
- Added conceptual numbering: allocated MA Message 1003 / Packet 1015; telemetry Message 1136. Private removal/graph/status messages use 2001/2002/2003. Removal never grants clearance.
- Updated consumers, browser fixtures and multilingual manuals. STF standalone support remains intact.
- Added `/stcs integrity [train-name|uuid]`, an admin-only, in-memory observer of member distribution and inferred node passages. It does not clear occupancy, prove tail clearance or grant MA. [Diagnostic scope](doc/NODE-INTEGRITY.md).
- Validation: 63 Java test entry points, PCC JavaScript and two browser regression suites passed. Live-server validation of this release remains outstanding.
- No persistent ledger schema change, ATP actuation or FS activation. [Migration guide](doc/STA-V5-MIGRATION.md).

四插件配套升级，不兼容旧组合；编号仅为致敬，不代表 ETCS 编码或标准符合性。新增节点通过只读诊断，不释放占用。保留轨道图和占用账本，不通过清空数据升级。以下为先前版本的开发记录，其功能继续保留。

## STF 2.1.5 Baseline / 先前基线 — 2026-09-23

Component versions / 组件版本：STF **2.1.5**, STCS **2.2.1**, STA **0.8.1**, SkyPCC **0.8.1**.
Only STF's version changed in that batch. STCS source fixes below retained its existing version.
该批仅提升 STF 版本号；下述 STCS 源码修正当时沿用原版本。

### STF

- Added read-only onboard shadow speed curves using valid MA/EoA and vehicle B7 parameters. Defaults: stop 1 m before EoA and approach at no more than 5 km/h within 5 m, decreasing continuously to zero.
  新增有效 MA/EoA 与 B7 参数驱动的只读影子速度曲线；默认 EoA 前 1 m 零速、最后 5 m 最高 5 km/h，连续降至零。
- Replaced theoretical maximum-speed telemetry-age compensation with a source/current measured-speed estimate. This removes fictitious stationary travel and the associated low-speed refresh jumps. Actual MA shortening is not delayed or visually smoothed.
  遥测延迟改用源采样及当前实测速度估算，修正静止时虚构位移和低速刷新跳变；真实 MA 缩短仍立即反映。
- Displayed the same shadow limit on sidebar line two and the driver-only MA BossBar. Added Chinese, English, French and Japanese BossBar labels. Invalid inputs show `--`.
  计分板第二行与司机专用 BossBar 显示同一影子限速，支持中英法日；失效时显示 `--`。
- Added configurable near-limit audio with hysteresis and cooldown under `shadow-atp.warning` and `ma-sounds.near-limit`. Existing MA sounds are retained.
  新增带滞回与冷却的接近限速音效，配置位于上述两节，保留原有 MA 音效。
- Preserved optional STA integration. Session, lease, graph revision, source sequence, freshness and reversal checks guard curve inputs.
  保持 STA 可选集成，并检查会话、驾驶权、图版本、源序号、时效及换向。

### STCS And Tools / STCS 与工具

- Preserved the actual downstream stop reason when a short common exit is blocked by unknown/pending points, graph gaps or lookahead limits; a real track end still reports insufficient exit capacity. Added common-exit regression coverage.
  短 common 出口遇到未知/待处理道岔、图缺口或前视边界时保留真实原因；真实线路尽头仍可报出口容量不足。新增出口回归测试。
- Added read-only Python ledger inspection for matching RailGraph and shadow-occupancy files, including spatial/legacy resources, owner UUIDs, saved observations and unmapped evidence.
  Python 测试台新增只读账本核对，显示空间/旧整边资源、所属 UUID、历史观测和未映射证据。
- Documented the existing console-only, backed-up and audited manual clearance command. No automatic deletion, member reassignment cleanup or live ledger editor was added. The reported stale identity remains an investigation lead, not a proven automatic fix.
  补充现有控制台人工解除的备份与审计流程；没有新增自动删除、车厢归组清理或在线账本编辑器。旧身份残留仍是调查线索，不宣称自动修复。

### Validation And Limits / 验证与边界

- Local build: four plugins, 62 Java test entry points passed. Python: 60 discovered, 57 passed, 3 skipped. The owner reported a successful server test; this is not an exhaustive safety validation. No new PCC browser validation was run for this batch.
  本地构建四插件，62 个 Java 测试入口通过；Python 60 项中 57 通过、3 跳过。服主反馈测试通过，不代表完整安全验收；本批未新增 PCC 浏览器验收。
- Curves remain experimental, level-track, display-only estimates. No automatic ATP brake execution, FS activation or PCC curve publication. `shadow-atp.enforcement-enabled: true` is rejected. No ETCS/SUBSET-026 compliance claim.
  曲线仍是实验性平坡显示估算，没有自动 ATP 制动、FS 启用或 PCC 曲线报文；执行开关 true 被拒绝，不声称符合 ETCS/SUBSET-026。
- Existing configurations use defaults for omitted keys. Stop trains before `/st reload`. Do not clear occupancy merely because a chunk is unloaded or a resource fails to match an unrelated graph.
  旧配置缺项使用默认值，停车后重载；区块未加载或轨道图不配套，不是删除占用的依据。

[Curve details / 曲线说明](doc/SHADOW-ATP.md) · [Ledger workflow / 账本流程](STCS/tools/testbench/OCCUPANCY-AUDIT.md)

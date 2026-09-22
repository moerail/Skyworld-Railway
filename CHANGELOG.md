# Updates / 更新说明

## Pending Publication / 待发布 — 2026-09-23

Component versions / 组件版本：STF **2.1.5**, STCS **2.2.1**, STA **0.8.1**, SkyPCC **0.8.1**.
Only STF's version changed in this batch. STCS source fixes below retain its existing version.
本批仅提升 STF 版本号；下述 STCS 源码修正仍沿用现有版本。尚未创建 Git 标签或 GitHub Release。

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

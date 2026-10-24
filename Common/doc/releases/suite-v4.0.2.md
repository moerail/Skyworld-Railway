# SkyRail Suite v4.0.2 (pre-release)

**Experimental pre-release. Live-server acceptance of these changes is pending.**

Release set: **SkyTrainFolia 4.0.2 / STCS 4.0.2 / SkyworldTrainAPI 2.0.0 / SkyPCC 2.0.0**. Minecraft/Folia 26.2 baseline, Java 25. STA protocol and PCC version are unchanged.

## Changes

- Near-limit audio now has a low-speed band selected by the current ATP limit. At limits <=40 km/h, including the default SH ceiling, warn at limit minus 5 km/h and clear at/below limit minus 8 km/h. At a 40 km/h limit: start at 35, clear at 32. Normal-band defaults for new installs are 15 / 18 km/h; existing normal-band settings remain unchanged. Overspeed priority and ATP brake thresholds are unchanged.
- Configure the low-speed band in STF under `shadow-atp.warning`: `low-speed-limit-kmh: 40`, `low-speed-enter-gap-kmh: 5`, `low-speed-clear-gap-kmh: 8`.
- STCS retries temporary Windows occupancy-ledger replacement denial up to five attempts with 185 ms total backoff. Persistent failures still stop allocation without clearing retained occupancy.
- New `/stcs admin ma restart` command (`stcs.admin`, console supported) attempts recovery from an MA-service I/O failure. It checks source availability, requires fresh stationary reports for outstanding executable authorities, backs up the committed ledger and pending snapshot, and verifies a real write. Drivers must demand MA again. It does not release brakes, change the ATP channel or bypass unknown infrastructure. Non-I/O runtime faults require investigation/server restart.
- Updated the Chinese, English, Dutch, French and Japanese manuals and the Chinese/English/French/Japanese driver guides. The classical Japanese companion is also updated.

## Installation and Limits

Validation: all four plugins built; 75 Java test entry points passed, as did PCC edge-line and four-language resource tests and Markdown local-link/fence checks. The local JDK emitted dependency-archive close/access diagnostics while returning success; this is not a clean warning-free build or live-server acceptance. `SHA256.json` lists the attached JAR checksums.

Stop the server and back up plugin data, including RailGraph, occupancy ledgers and pending `.tmp` files. Replace STF and STCS; STA/SkyPCC remain at 2.0.0. Do not leave duplicate old/new JARs installed. Restart fully to load the new binaries. For later STF configuration changes, stop all trains before `/st reload`; it revokes driving control and hotbar mode. Existing normal-band warning values are preserved: explicitly set `enter-gap-kmh: 15` and `clear-gap-kmh: 18` if desired.

This remains an experimental game system, not certified ATP/interlocking or ETCS SUBSET-026 compliance. Manual-train protection does not apply to STF's built-in sign-driven pseudo-ATO trains. Unknown is not clear. An unconfirmed remote point conversion must not be treated as confirmed; that separate issue is not fixed in this release.

## 中文说明

**预发布，尚待服务器验收。** 配套版本为 STF/STCS 4.0.2、STA/SkyPCC 2.0.0；Minecraft/Folia 26.2、Java 25，协议不变。

四模块构建完成，75 个 Java 测试入口、PCC 线路归属与四语言资源测试、Markdown 本地链接及代码块检查通过。本机 JDK 关闭依赖 JAR 时仍有访问诊断但返回成功；不声称无警告构建或实服验收通过。附件校验值见 `SHA256.json`。

- 低速预警由当前 ATP 限速选档：≤40 km/h 时提前 5 km/h 报警、低于限速 8 km/h 及以下解除；40 限速对应 35 开始、32 解除。普通档新安装默认 15 / 18，已有配置保留。超速优先级、制动逻辑不变。
- STCS 增加 Windows 账本替换有限重试与 `/stcs admin ma restart`。恢复保留占用并备份账本，需要有效数据源以及旧可执行许可对应列车的新鲜停稳报告。恢复后重新申请 MA，不自动缓解制动；持续故障保持停用。
- 各语言 README 与中英法日司机手册已同步。更新二进制需停服、备份后替换 STF/STCS 并完整重启；不要删除占用数据。后续 `/st reload` 前应停稳所有列车，重载会收回驾驶权和 hotbar。
- 本版没有修复道岔实际写入成功但返回 `UNCONFIRMED` 的确认问题，不应据此将未确认当作成功。仍非真实铁路安全设备或标准认证实现，手动列车状态机不适用于内置伪 ATO。

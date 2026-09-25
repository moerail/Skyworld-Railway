# SkyRail Suite 4.0.0: Experimental M3 Manual-Train Protection

**Pre-release.** Automated checks passed; the latest fixes have not completed another live-server acceptance run. This release is experimental Minecraft software, not certified ATP, interlocking or an ETCS-compliant implementation.

## Components

| Plugin | Version |
| --- | --- |
| SkyTrainFolia | 4.0.0 |
| STCS | 4.0.0 |
| SkyworldTrainAPI | 2.0.0 |
| SkyPCC | 2.0.0 |

## Changes

- Adds manual-train operating modes SB, FS, SH, SR, TR and PT, with a separate administrator-controlled Enforced channel. STF's built-in sign-driven pseudo-ATO trains remain outside this state machine.
- Adds STA v6 operational permissions, onboard speed/EoA supervision, retained last-confirmed EoA within the validated session, Trip acknowledgement and authenticated PCC SR approval. Shadow MA remains observational.
- Corrects SH/SR speed-ceiling braking, near-limit/overspeed warnings in Enforced, ATP B7/EB feedback and localized TR messages and cues.
- Separates SR target reachability from the rolling MA window: default target search is 5000 m, while each local grant is capped at 120 m. Unknown points, graph gaps, occupancy uncertainty and resource conflicts still restrict authority.
- Fixes driver/hotbar revocation and consist geometry retention during STF reload. Adds configurable continuous warning sounds and intervention cues.
- Updates the multilingual README editions and Chinese, English, French and Japanese driver manuals, including introductory MA/EoA/ATP terminology.

## Installation and Validation

Stop the server, back up plugin data, then replace the installed suite components together. Retain RailGraph and occupancy ledgers; do not mix old and new binaries. Hard-refresh PCC after updating. STF can still run standalone, but Enforced requires STA and STCS. Enforced is explicitly enabled on a stopped manual train with `/stcs admin enforce true`; `false` returns to RECOVERING brake hold.

The attached four JARs were built against the existing Minecraft/Folia 26.2 baseline with Java 25. `SHA256.json` contains their checksums. Validation already completed: 73 Java test entry points, PCC edge-line and four-language resource tests, and documentation file-link/fence checks. This publication does not claim successful live-server acceptance of the latest changes.

## 中文说明

**本次为预发布。** 已完成自动检查，但最近的修复未再进行一轮服务器验收。配套版本为 STF/STCS 4.0.0、STA/SkyPCC 2.0.0。

- 新增仅适用于手动列车的 SB/FS/SH/SR/TR/PT 模式，以及管理员显式启用的 Enforced 制动监督；内置牌子控车伪 ATO 不进入此状态机。
- 新增 STA v6 可执行许可、车载速度/EoA 监督、有效会话内最后确认 EoA 的保持、Trip 确认和 PCC SR 审批。影子 MA 仍只读。
- 修正 SH/SR 模式限速制动、强制保护通道的接近限速/超速警报、ATP B7/EB 显示和 TR 消息与音效。
- SR 目标可达性与滚动 MA 窗口分离，默认目标搜索 5000 m、单次许可 120 m；未知状态、占用和资源冲突仍限制授权。
- 修复 STF reload 时驾驶权与热键栏撤销、编组几何保持，并同步各语言 README 和中英法日司机术语入门。

停服备份后一起替换已安装的组件，保留轨道图与占用账本，刷新 PCC 缓存。附件基于 Minecraft/Folia 26.2、Java 25 构建。73 个 Java 测试入口及 PCC 相关检查已通过；这不等于服务器验收通过，也不代表真实铁路 ATP、联锁或 ETCS 标准认证。

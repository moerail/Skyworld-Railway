# Node Passage Integrity / 节点通过完整度核对

Local build: 63 Java test entry points passed, including parking, straddling, reverse,
duplicate delivery, skipped nodes, stale input, identity conflicts, switch ports and
composition changes. Not yet verified on a live server.

本地完整构建 63 个 Java 测试入口通过；此新增诊断尚未在服务器实测。

## First Increment / 第一阶段

STCS consumes existing STA v3 consist observations: train/session UUID, ordered member
UUID manifest, sequence and member positions. No new STF hard dependency or wire contract.
The manifest ID is locally derived from session and member order, not a new STF version field.

STCS 使用现有 STA v3 编组观测，包含列车/会话 UUID、有序成员列表、序号和位置。
编组标识由会话及成员顺序本地生成，不冒充 STF 新增的编组版本字段。

`/stcs integrity [train-name|uuid]` requires `stcs.admin`. The command shows manifest,
member-to-edge resource assignments and the last eight inferred transfers with node ports.
There is no clearance command in this subsystem. Existing MA/occupancy behaviour is unchanged.

管理员使用上述指令查看编组、成员所在边及最近八次带端口的推断转移。
两条边合并凑齐成员只代表观测可以解释编组，绝不表示其中任何边空闲。
此子系统没有解除占用的指令，不参与 MA 分配或现有占用释放。

## Rules / 规则

- A fresh stationary train can remain observed indefinitely. The 1.5 s data-freshness check
  marks lost observations uncertain; it never expires occupancy or clears member records.
  停车没有清除时限。1.5 秒是观测时效检查，不是占用超时；数据失效仅标记不确定。
- Opposite directed edges share one physical resource. A unique adjacent transition updates
  exit and entry counts together; repetitions of the same sequence do not increment counts.
  正反有向边归入同一物理资源，邻边转移一次完成进出记录，重复报文不重复计数。
- Nodes may straddle multiple candidates: retain the last unambiguous assignment. An initial
  observation is a baseline, not a fabricated entry event. Member centres are not vehicle tails.
  边界上保留上次确定位置，首次观测只建立基线，不补造入口事件；车厢中心不等于车尾。
- Unknown points, skipped/nonadjacent edges, graph/session/manifest changes, stale/missing
  observations and duplicate member ownership retain uncertainty. No timer resolves it.
  未知道岔、跨过多节点、图/会话/编组变化、漏报和重复归属均保留不确定，不自动解锁。

## Limits / 限制

This is a snapshot-based diagnostic, NOT a completed virtual axle-counter safety function.
Adjacent transitions are explicitly INFERRED; detours or out-and-back movement between
samples cannot be excluded. A real complete-passage evidence chain requires STF to provide
reliable ordered along-track events and explicit manifest changes, plus replay/gap handling.
No independent sensor redundancy, swept-body/tail clearance, FS or certified interlocking.

这是基于快照的诊断，不是完整虚拟计轴安全功能。相邻边转移明确标为 INFERRED，
不能排除采样间绕行或往返；后续还需要 STF 可靠有序的沿轨事件、显式编组变更、重放和漏报处理。
没有独立传感冗余，不提供车尾完整出清、FS 或认证联锁。

The diagnostic state is in memory and resets on plugin/server restart. Existing durable
shadow occupancy and M1 ledgers are neither edited nor cleared by this observer. Restart
does not prove clearance; it starts a new diagnostic baseline. At most 64 recent inferred
transfers are retained per train; these are not a complete historical audit log.
At 4096 retained identities the observer stops with an explicit unavailable state instead
of evicting evidence; existing MA and occupancy processing continues independently.

诊断状态保存在内存，重启后重新建立基线；原有持久化占用账本不受影响，重启不证明空闲。
每列车保留最近 64 次推断转移，不是完整历史审计日志。连续性破坏后本轮诊断保持不确定。

# Experimental Onboard Shadow Curve / 实验性车载影子曲线

This document describes the **read-only shadow channel**. STF 4.0.0 also has a separate
experimental `Enforced` channel for **manual trains only**. It requires STA 2.0.0 and
STCS 4.0.0, an explicit admin switch while stopped, and a validated executable v6 MA.
Its settings are under `active-atp`, not `shadow-atp`. STF's built-in pseudo-ATO
automatic trains do not enter `SB/FS/SH/SR/TR/PT`. Neither channel is certified ATP.

本文只说明**只读影子通道**。STF 4.0.0 另有仅对**手动列车**生效的实验性
`Enforced` 通道，需要 STA 2.0.0、STCS 4.0.0、停车时管理员显式切换和有效的
v6 可执行 MA。其参数位于 `active-atp`，不在 `shadow-atp`。STF 内置伪 ATO
自动列车不进入 `SB/FS/SH/SR/TR/PT`；两种通道都不是认证 ATP。

STF can calculate a read-only speed envelope from STCS shadow MA via optional STA.
The driver's sidebar shows `Shadow limit` on the second line; the BossBar shows MA
information followed by the same ATP limit, in Chinese, English, French or Japanese.
No traction, brake, protection mode or existing MA allocation is changed by this calculator.

STF 通过可选 STA 接口读取 STCS 影子 MA，计算只读速度包络。司机右侧计分板第二行显示影子限速，
BossBar 显示 MA 信息及相同的 ATP 限速，支持中英法日。计算器不改变牵引、制动、防护模式或 MA 分配。

In the experimental Enforced channel, the same near-limit and overspeed warning loop
uses the active ATP limit. When ATP actually applies B7 or EB, the driver sees the
intervention and its reason in the sidebar, an `ATP B7`/`ATP EB` marker on the BossBar,
and hears a one-shot cue. These cues are configurable under `ma-sounds.atp-service`
and `ma-sounds.atp-emergency`; omitted keys use shipped defaults. A stationary SB hold
does not repeatedly announce itself. Automatic trains do not use this feedback.

实验性强制保护通道也会按当前 ATP 限速播放接近限速和超速循环警告。ATP 实际施加 B7/EB 时，
司机计分板显示干预及原因，BossBar 标出 `ATP B7`/`ATP EB`，并播放一次提示音。
可在 `ma-sounds.atp-service` 和 `ma-sounds.atp-emergency` 配置；旧配置缺项使用默认值。
静止的 SB 保持不会反复响，内置自动列车不使用这套提示。

## Model / 模型

All calculations use metres and seconds. Effective deceleration is the vehicle profile's B7
value multiplied by `service-brake-factor`. Internal block/tick units are converted using
`infrastructure.blocks-per-meter` and the nominal 20 ticks/second model.

`v*t + (v² - vt²)/(2*a) <= d` defines the approach to a lower target speed. The result is
the minimum of the vehicle/server/train ceiling, the EoA stopping curve, and the approach
speed envelope. Defaults: stop 1 m before EoA; at most 5 km/h within the final 5 m, with
continuous deceleration to zero. A slow approach is a ceiling, not permission to move.

全部计算使用米和秒。有效减速度取车辆 B7 参数乘以配置系数，按方块/米比例和名义
20 tick/s 换算。曲线取车辆限速、EoA 停车曲线和接近限速包络的最小值。
默认在 EoA 前 1 m 停车，最后 5 m 内最高 5 km/h，同时连续降低至零。低速上限不是开车许可。

## Validity / 有效性

The input must match the driver lease, graph revision, telemetry session and a retained
source telemetry sequence. Source and authority ages must be at most 1.5 seconds.
Direction changes invalidate the input. Movement since the source sample is
estimated using the greater of source and current measured speeds, multiplied by sample
age. The theoretical speed ceiling is not used for this extrapolation, avoiding large
sawtooth jumps at each new MA report while creeping or stopped. This is not continuous
odometry or a proven safety bound. Actual MA reductions are applied immediately.
Unavailable/expired inputs display `--`, never a fabricated zero-speed requirement.

输入必须匹配司机控制权、图版本、遥测会话和保留的源遥测序号，源数据和 MA 均不得超过
1.5 秒。换向后旧输入失效。源采样后的位移按源采样与当前实测速度较大者乘以数据年龄估算，
不再用理论最高速度扣距离，避免低速或静止时每次 MA 刷新造成明显锯齿跳变。
这是影子估算而非连续测距或经过证明的安全边界；真实 MA 缩短仍立即反映。
失效或缺失时显示 `--`，不同于有效曲线要求零速。

## Configuration / 配置

See STF `config.yml`, section `shadow-atp`. Existing installations can add this section;
omitted keys use the shipped defaults. Stop all trains before `/st reload`.
`enabled: false` disables the display calculation. `enforcement-enabled: false` reserves
the future execution gate; **true is rejected**, not silently accepted as FS.

配置位于 STF `config.yml` 的 `shadow-atp` 节；旧配置缺项使用默认值。
停车后使用 `/st reload`。`enabled: false` 关闭计算。`enforcement-enabled` 是预留执行开关，
当前必须为 false，true 会明确拒绝，不能据此宣称已启用 FS。

A successful `/st reload` releases driving leases, disables hotbar controls and retires
old cab displays. Drivers are notified in their selected UI language. Use `/st drive`
again to acquire a new lease and restore the HMI; hotbar control requires slot 5 and
another `/st hotbar`. Moving-train or configuration-validation rejection leaves sessions intact.
Live train objects, formation order, remembered direction, rail paths, member motion
frames and entity tasks remain in place; reload does not reconstruct or reposition the consist.
If the member tick interval changes, only its scheduler tasks are replaced on their owning
entities, still using the same live trains and paths.

`/st reload` 成功后收回驾驶权、关闭热键驾驶并清理旧 HMI，按司机所选语言通知。
重新 `/st drive` 可取得新驾驶权并恢复显示；热键驾驶需选中第 5 格后重新 `/st hotbar`。
列车未停车或配置校验失败而拒绝重载时，不清理现有会话。
保留现场列车对象、编组顺序、方向记忆、轨道路径、车厢运动帧和实体任务，不再从 YAML 重建或重排列车。
只有车厢 tick 间隔改变时，才在实体所属调度器上更换任务，继续使用原列车与路径。

## Limits And Validation / 边界与验证

Driver-only audio uses `ma-sounds.near-limit`, `ma-sounds.overspeed` and `shadow-atp.warning`.
Defaults: enter within 2 km/h of the shadow limit, continuously loop six C5-G5 pairs
without a burst gap, and clear at 5 km/h below the limit or below 0.5 km/h.
Above the limit, a faster bit-tone alarm replaces the near-limit melody. It returns to
the near-limit warning at 1 km/h below the limit. All thresholds are configurable.
The entity scheduler rechecks the curve and driver lease every tick. Missing/stale curves,
leaving Shadow mode or losing driving control stop playback. One speed-warning loop
per driver prevents overlap; MA notifications use a separate channel. No braking is applied.
The vanilla flute approximates the pitches, not an authentic CTCS buzzer recording.
The shrinking-MA cue is three short `minecraft:block.note_block.bit` tones.

接近限速提示音仅对司机播放。默认距限速 2 km/h 时开始，每轮六组 Do5-Sol5，
轮间没有冷却空档，一直循环；降到限速以下 5 km/h 或低于 0.5 km/h 时停止。
超过限速改为更急促的 bit 警报，优先替代接近限速音；降到限速以下 1 km/h 后退回普通提示。
阈值均可配置。每 tick 检查曲线和驾驶权；曲线失效、退出影子模式或失去驾驶权会停止播放。
每位司机只有一个速度警报循环，MA 通知使用独立通道。仍不施加制动。
原版长笛仅近似音高，不是 CTCS 蜂鸣器录音；MA 开始缩短改为三声短促 bit 音效。

### Existing Configurations / 旧配置更新

Existing sound choices are preserved. Merge the following entries into the existing
`ma-sounds` section of `plugins/SkyTrainFolia/config.yml` to adopt the new defaults;
do not add duplicate YAML keys. Stop all trains, then run `/st reload`.
`pitch-sequence` overrides scalar `pitch`; `count` repeats the entire sequence.
Pitches must be 0.5..2, count 1..16, with at most 64 notes per burst.

旧配置保留原音效选择；采用新默认值时，将下面各项合并到 STF 配置的现有 `ma-sounds`
节点，不要重复添加同名 YAML 键。停车后 `/st reload`。
`pitch-sequence` 优先于 `pitch`，`count` 表示整组序列重复次数；音高 0.5..2，
重复 1..16 次，每轮最多 64 个音符。节奏和滞回参数仍可自行调整。

```yaml
ma-sounds:
  near-limit:
    enabled: true
    sound: minecraft:block.note_block.flute
    category: MASTER
    volume: 1.0
    pitch: 0.7071068
    pitch-sequence: [0.7071068, 1.0594631]
    count: 6
    interval-ticks: 5
  overspeed:
    enabled: true
    sound: minecraft:block.note_block.bit
    category: MASTER
    volume: 1.0
    pitch: 1.5
    count: 1
    interval-ticks: 3
  shrinking:
    enabled: true
    sound: minecraft:block.note_block.bit
    category: MASTER
    volume: 1.0
    pitch: 1.0
    count: 3
    interval-ticks: 5
```

The interval is between notes (5 ticks = 0.25 s at 20 TPS). Resource-pack sound IDs
can replace these vanilla approximations; pitch must match the custom sample's base note.
音符间隔默认 5 tick（20 TPS 时为 0.25 秒）；使用自定义资源包时，需要按采样基音调整音高。

Merge these keys into `shadow-atp.warning`. Legacy `cooldown-millis` is ignored;
near-limit and overspeed warnings are continuous. Overspeed entry is strictly above
limit + margin; its clear gap must be non-negative and smaller than `clear-gap-kmh`.
将以下键合并到 `shadow-atp.warning`。旧 `cooldown-millis` 不再生效，两种警报均连续循环。
超速触发条件是严格大于限速加容差；超速解除间距须非负且小于普通警报解除间距。

```yaml
shadow-atp:
  warning:
    enter-gap-kmh: 2.0
    clear-gap-kmh: 5.0
    minimum-speed-kmh: 0.5
    overspeed-enter-margin-kmh: 0.0
    overspeed-clear-gap-kmh: 1.0
```

This first increment provides the independent model and driver display, not PCC curve
publication or an executable ATP service. It assumes level track and constant available
service deceleration. It does not model adhesion, downhill profiles, brake build-up beyond
the configured reaction allowance, or route speed profiles. It is not ETCS/SUBSET-026,
certified interlocking, or real railway ATP. Shadow overspeed is informational only.

第一步仅提供独立模型和司机显示，不提供 PCC 曲线报文或可执行 ATP 服务。
模型假设平坡及恒定可用常用制动减速度，不包含黏着、下坡、完整制动建立过程和线路限速剖面。
影子超限只作提示，不是 ETCS/SUBSET-026、认证联锁或真实铁路 ATP。

Before any future enforcement work, server tests must cover approach/stop, MA changes,
release, driver changes, reversal, stale telemetry, graph rebuild, disconnection and reload.
Observe unchanged handles/braking in shadow mode. Missing STA must leave standalone STF
functional. A successful shadow test does not by itself validate FS brake execution.

后续接执行链之前，应实测接近停车、MA 跳变/释放、换司机、换向、遥测过期、图重建、断连和重载，
并确认影子状态不改变手柄及制动。无 STA 时 STF 独立功能应保持可用。
影子测试通过本身不等于 FS 制动执行链验收通过。

## Experimental M3 SR target handling / 实验性 M3 SR 目标处理

In the enforced channel, a dispatcher may approve a distant SR target using the saved
RailGraph. `ma.sr.max-target-distance-meters` bounds this topology-only search; it does
not enlarge the executable MA. `ma.sr.max-distance-meters` still bounds each rolling grant.
Unloaded plain-track chunks do not by themselves require the whole target to fit into one
grant. Missing graph edges, unresolved nodes, unknown switch positions, uncertain occupancy
or conflicting reservations do not become clear because the target is topologically reachable.
SH/SR apply B7 immediately above their configured speed ceiling; an EoA overrun causes TR,
a driver alert and emergency-brake cue. All of this remains experimental and manual-train only.

强制保护通道中，调度可以利用已保存的 RailGraph 审批较远的 SR 目标。
`ma.sr.max-target-distance-meters` 只限制拓扑搜索，不放大可执行 MA；每次滚动授权仍受
`ma.sr.max-distance-meters` 约束。普通未加载区块不要求一次 MA 覆盖整个目标，但图缺口、
未知道岔状态、不确定占用或冲突预约仍阻止不安全的延长。SH/SR 超过配置限速立即投入 B7；
冒进 EoA 进入 TR，并向司机发出提示与紧急制动音效。该功能仍属实验性，仅适用于手动列车。

# Experimental Onboard Shadow Curve / 实验性车载影子曲线

STF can calculate a read-only speed envelope from STCS shadow MA via optional STA.
The driver's sidebar shows `Shadow limit` on the second line; the BossBar shows MA
information followed by the same ATP limit, in Chinese, English, French or Japanese.
No traction, brake, protection mode or existing MA allocation is changed by this calculator.

STF 通过可选 STA 接口读取 STCS 影子 MA，计算只读速度包络。司机右侧计分板第二行显示影子限速，
BossBar 显示 MA 信息及相同的 ATP 限速，支持中英法日。计算器不改变牵引、制动、防护模式或 MA 分配。

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

## Limits And Validation / 边界与验证

Driver-only near-limit audio uses `ma-sounds.near-limit` (namespace ID, category, volume,
pitch, count and interval), and `shadow-atp.warning` for hysteresis. Defaults: warn within
2 km/h of the shadow limit; re-arm 5 km/h below it, or below 0.5 km/h; at least 5 seconds
between notices. An unavailable curve never triggers or re-arms a latched warning.

接近限速提示音仅对司机播放，音效在 `ma-sounds.near-limit` 配置（默认
`minecraft:block.note_block.bell`），滞回在 `shadow-atp.warning` 配置。
默认距影子限速不足 2 km/h 时响一次，回落到限速以下 5 km/h 或低于 0.5 km/h 才重新准备报警，
最短间隔 5 秒。曲线不可用不会报警，也不会重置已经触发的提示。

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

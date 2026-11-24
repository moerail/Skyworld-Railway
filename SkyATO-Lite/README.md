# SkyATO-Lite 0.4.1

[中文](../Common/README.zh.md) · [English](../Common/README.en.md) · [Français](../Common/README.fr.md) · [Nederlands](../Common/README.nl.md) · [Русский](../Common/README.ru.md) · [日本語](../Common/README.ja.md) · [文語體](../Common/README.ja.classical.md)

**[新增／修改 · 2026-11-24]** 配套 STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1。PCC通过STA v10编辑既有SAL设备组成的线路，草稿与发布版分离，每线至少绑定一个车辆段；运行任务固定发布快照。游戏与PCC并发编辑冻结双方，重启不自动解除，须人工解消。

[PCC中文操作与契约](../Common/doc/PCC-ROUTE-EDITING.md) · [English editor guide](../Common/doc/PCC-ROUTE-EDITING.en.md)


**[修改 · 2026-11-21]** [完整分发手册](../Common/README.zh.md)：新增运营地图（STA只读快照）、蓝色 `[SkyATO-Lite]` 前缀及分组帮助，版本页显示SAL和默认可执行ATP。正文以下操作仍适用；全套版本以新版手册为准。

固定运营线路的按钮叫车、停站、乘客信息和回库插件。依赖本批 STA 2.6.1、STF 5.0.1、STCS 4.3.3。
PCC 恢复告警及自动列车 SR 使用本批 SkyPCC 2.3.1。自动司机显示名为琪露诺，各语言使用对应名字。
新增双向出入库、首站复用、进路静默续租及手推入位停车；完整说明见 [出入库与退役契约](../Common/doc/SAL-DEPOT-AND-RETIREMENT.md)。
这是隔离测试候选版；不要把离线测试通过当作现有线路的实服验收。

运营线路使用独立的名字与有序路径点，不是 STCS 的工务线路。可以跨工务线路，也不会重写工务归属。
SkyATO-Lite 通过 STA v8 契约提交任务和控制意图，通过 STA v10 管理线路编辑会话；STCS 发放 MA，STF 在实体所属线程执行。

## 安装

停测试服，备份后配套替换 STA、STF、STCS，再加入 SkyATO-Lite。SkyPCC 可使用配套构建。
保留已有 RailGraph、占用账本和列车数据，不要为升级删除它们。不要混用旧 STA JAR。

新车默认使用可执行保护；手动司机取得驾驶权后默认申请 FS，获 MA 后进入 FS。
显式隔离/恢复通道不自动解除。未配置有效任务的旧伪 ATO 列车不会绕过 MA 自行行驶。
SAL 任务重启后进入 RECOVERY_REQUIRED，需要管理员确认，恢复时先回库。

## 手动与自动移交

已回库待命的列车：坐入后 `/sal release`，等待释放，再到首/末车厢 `/st drive`。
手动列车：司机在 depot 停车点连续停稳3秒，点击 `[确认SAL纳管]`，再自己输入 `/st release`
或点击第二个确认按钮，才会转入自动待命。直接释放驾驶权、离车或断线不等于同意纳管。
手动状态持久保留，不会刚释放又被自动接管；纳管成功后归属设为实际车辆段。

STF旧自动牌执行链和 mode automatic 入口已删除，自动驾驶统一经SAL和STA。
[操作、权限、确认有效期和契约](../Common/doc/SAL-MANUAL-HANDOVER.md)。

## 牌子

牌头 `[SAL]` 与 `[SkyATOL]` 等价。登记、编辑、拆除需要 `skyatolite.admin`（默认 OP）。
除 awake 外，牌子放在对应轨道柱下；站立牌须有自身支撑，墙牌须附在轨道柱上。
该轨道必须已存在于 STCS RailGraph；SAL 从图绑定位置，不自行扫描世界中的铁轨。

```text
[SAL]
depot
maintown
L1
```

第三行是车辆段，第四行是独立停车位。L2、L3 各放一个同格式牌子。位置可以分散。

```text
[SAL]
awake
maintown
Line1
```

用按钮给牌子或其支撑方块供电，上升沿叫车；awake 不需要放在轨道下。
同一车辆段/线路已有车辆接客时合并请求，短时间连点不会不断派车。
入口先完成一次电平初始化，持续供电及服务器启动不应当自动叫车。

```text
[SAL]
station
15
Line1
```

第三行是停站秒数，第四行是 SAL 运营线路。先创建线路再放牌子；新 station 会按放置顺序追加为路径点。
station 的内部身份由牌子位置确定，不占用第三行另写站名。

```text
[SAL]
property
V_target
36km/h
```

也支持简写：

```text
[SAL]
v_target
36km/h

```

和 STF 一样接受 km/h、kmh、m/s、m/t、b/t、mph；无单位为方块/tick。只改变任务巡航目标，
仍受车型、列车限速和 MA 保护约束。property 目前只接受 V_target。

```text
[SAL]
message
欢迎来到 Skyworld
5
```

本车乘客看到中央 Title，显示 5 秒。新 message 立即覆盖旧 message，不排队，不延迟清除新标题。
文本按字面显示，不执行指令。高速度同一采样跨过多块牌子时，按沿线先后顺序处理。

## 建一条线路

1. 面向首站的行驶方向，执行 `/sal route create Line1`。也可以用 `/sal route direction Line1 east` 修正方向。
2. 放首站 station。然后沿计划行驶方向，站在应答器/道岔旁执行 `/sal route bind Line1`。
3. 多个设备在 8 格内时显示类型、名称、坐标和 UUID，用 `/sal route choose 编号` 选择，60 秒有效。
4. 后续 station 放置时会追加。用 `/sal route list Line1` 看顺序，`/sal route remove Line1 序号` 移除某次访问。
5. 回到首站附近，再绑定首站作为终到，可以在同一 station 上执行接客和一圈结束后的停站。
6. `/sal route preview Line1`：两分钟可见预览；闭环为绿，断路/歧义/错误为红。`preview Line1 off` 关闭。
7. `/sal route validate Line1` 检查；`/sal route publish Line1` 发布。

首尾必须为 station，中间可以绑定工务设备。不需要绑定每根铁轨；图中有多条可选路径时需要补中间设备。
可以用 `/sal route bind Line1 设备UUID` 精确绑定；station ID 从 list 中复制。
在 station 的绑定命令末尾加 `reverse`，表示此次停站后折返；同一个 station 可以绑定为多次访问。
线路有任务时不允许直接编辑。图变化先检查线路依赖：无关变化保持发布，受影响线路自动重新验证，失败取消发布。
实际运行路径改变时列车保持并等待管理员检查；不会沿旧路径直接继续。
STCS 默认每 60 秒复核已加载轨道，卸载区域保留已知连接。详见 [局部更新与 STA 依赖契约](../Common/doc/RAILGRAPH-INCREMENTAL.md)。

绿色只表示拓扑、方向和闭环成立，不表示实时空闲，更不是 MA。预览只给编辑管理员，不全服广播。
首站出库与终到回库路径还会按实际列车位置另行计算；多路径歧义必须先解决。

## 列车与运行

管理员坐进已经组成 STF 编组并停稳的列车：

```text
/sal train home maintown
```

不填名字查询当前归属。绑定本身不发车，也不固定 L1；管理员可手推至所属 depot 停车点，由 STF 接管保持停车。
叫车会考虑两个方向，需要时先释放旧 MA、停稳换向，再从实际领车位置规划到首站，不要求预先摆正车头。
可用列车需要成员观测完整、无 TIMS 保持、无人驾驶且没有其他任务。

按 awake 按钮，或管理员用 `/sal awake maintown Line1` 测试。

一次叫车执行：出库 → 第一站接客 → 依次运行及停站 → 最后一站停站 → 查找并预约空闲车位 → 回库停稳结束。
无人上车也完成一次任务，不按签到、乘车人数或下车事件追加圈数。乘客可跟着回库，下一圈要在站台重新叫车。
首站上尚未离站的同线路任务优先复用：取消回库安排，必要时安全换向，重新计首站停站时间，再运行下一圈。
已经离站或发生故障的车辆不复用。停车位没有合适空间或路径时保持等待，不生成新车或占用另一列车的车位。
出入库路径按 RailGraph 寻找，道岔由 STCS 排路并经 STA 执行；无需管理员预先手动扳到目标方向。

管理命令：

```text
/sal status
/sal stop 列车UUID
/sal resume 列车UUID
/sal finish 列车UUID
```

stop 保持制动；resume 在管理员核对停稳、完整性和图后恢复为回库任务；finish 需要完整定位且全列停稳，
先释放旧 MA，再结束自动控制权。不要用 reload 当作运行中的恢复方式。

## 空车、区块与故障

STF 的列车区块票据不依赖乘客。SAL 通过 STA 提交前方路径的区块保活需求，STF 异步加载并在区块所属线程添加票据；
前方区块未准备好时禁止牵引。保留全列所在区块，按任务进度移动前方范围，不强制加载整个世界。
如果测试服启用了空服暂停，还需关闭全服暂停（本次实车测试使用 `pause-when-empty-seconds=-1`）；
区块票据不会让一个被整体暂停的服务器继续 tick。

TIMS 自愈限于暂态观测问题：先停稳，真实完整性恢复后确认，再释放旧许可并重申请 FS。
保留成员 UUID 与车身占用；不删车重生、不改编组名单。最多尝试三次，失败保持停车并给出原因。
真实缺车、编组改变、异常间距、人工 Trip 等需要检查；不能通过自动确认掩盖。

偏离运营路径会停止牵引、在 PCC 运行日志中产生告警，并在全列停稳、TIMS 完整和图定位可信后申请待批准 SR。
管理员在 PCC 选择待批准列车和下一目标节点，检查进路/道岔后批准。虚拟司机以低速执行该段 SR；
到授权边界前停车、释放旧许可，再申请下一段。每一段都需要重新批准，不自行无限取得 SR。
批准对自动恢复列车也明确授权停稳后的 TR/PT 恢复；这不会清除 TIMS 问题。无法可信定位时不执行 SR。
当已批准的 SR 覆盖可用归属停车位及所需余量时，可以在该授权内完成入位；否则等待管理员继续逐段引导。
PCC 恢复目前沿列车现有行驶方向审批；界面没有独立的远程换端按钮。配置线路中的 station 折返由 SAL 按计划执行。

## 数据与验收

STA v8 编号：私有 Message `6508`；驾驶意图为 `SUBSET_139/Packet 1031`，车辆反馈为 `SUBSET_139/Packet 1032`，
致敬真实 ATO 车辆接口的 31/32 + 1000。接管、折返、Title、区块保活与许可申请使用私有 Packet；
MA 继续使用 Message 1003 / Packet 1015。目录、官方来源及升级说明见
[STA v8 ATO 契约](../Common/doc/STA-V8-ATO-PROTOCOL.md)。

`plugins/SkyATO-Lite/state.json` 保存牌子、线路、列车归属及任务；保存前备份为 `.bak`，写入失败停止发车。
启动后有旧任务会保持，不能凭存档猜测车辆已加载。运行中定期检查已加载的牌子，移除或篡改引用会使任务保持。

离线回归覆盖牌子字段、速度单位、闭环/断环/分支歧义、路线进度、自动 FS、身份和图版本拒绝、
请求过期、迟到请求 fencing、TIMS 撤销与 MA 释放。部署前仍须在实际测试线上验证：

- 无人在线/无人乘坐时完成出库、停站、一圈与回库；多停车位、多车竞争。
- 道岔拒绝、折返全列清岔、不同长度和车型的停车位置。
- 乘客 Title 覆盖、跨区与下车；载人回库不自行再跑。
- TIMS 暂态恢复、真实缺车保持、服务中断及服务器重启恢复。

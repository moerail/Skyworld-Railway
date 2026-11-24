# STA v8 ATO 编号与接口契约

本说明最初随 STA 2.3.1、STF 4.1.1、STCS 4.2.1、SkyATO-Lite 0.1.1、SkyPCC 2.2.0 发布。
当前增量建图配套版本与新增 v9 依赖契约见 [RailGraph 增量说明](RAILGRAPH-INCREMENTAL.md)；本页的 v8 ATO 编号不变。
协议仍为 v8；这是原 v8 Java 服务的增量契约，插件版本与协议版本分别管理。

## 官方来源与编号性质

核对日期：2026-11-19，来源为 ERA 发布的 UNISIG 规范：

- [SUBSET-126 v1.1.0](https://www.era.europa.eu/sites/default/files/2026-04/index085_subset-126%20v1.1.0.pdf)，§7.1 Table 2、§7.2，PDF 第 14–15 页：ATO-OB↔ATO-TS 编号字段是 NID_PACKET_ATO，不是 NID_MESSAGE。
- [SUBSET-130 v1.0.0](https://www.era.europa.eu/system/files/2023-09/index087_-_SUBSET-130_v100.pdf)，§6.1 Table 2，PDF 第 10 页：ATO-OB↔ETCS-OB 使用 Packet Number，引用 SUBSET-143 的 NID_PACKET、Slot 1。表中 Message Data 是数据类别，不是 Message 编号。
- [SUBSET-139 v1.0.0](https://www.era.europa.eu/system/files/2023-09/index088_-_SUBSET-139_v100.pdf)，§7.2 Table 16，PDF 第 20 页：ATO 与车辆控制/反馈使用 Packet 31、32、33。

仅采用已核实的 **Packet 原编号 + 1000**。**Message 6508 是 STA 私有 ATO 外层**，与 v7 的私有 Message 6502 分开，没有真实标准编号来源。

## 实际运行的契约

新 ATO 头部统一为 `M_VERSION=8, NID_MESSAGE=6508`，强制携带 `NID_INTERFACE` 与 `NID_PACKET`。

| NID_INTERFACE | Packet | 数据/操作 | 来源 |
| --- | --- | --- | --- |
| SUBSET_139 | 1031 | SAL→STF，AutomaticTrainService.Intent | ATO_RST_data，31+1000 |
| SUBSET_139 | 1032 | STF→SAL，AutomaticTrainService.View | RST_ATO_data fast，32+1000 |
| SKY_ATO | 5100 | claim，接管 | 私有 |
| SKY_ATO | 5101 | release，释放驾驶控制权 | 私有 |
| SKY_ATO | 5102 | reverse，停稳后折返 | 私有 |
| SKY_ATO | 5103 | recover，受限 TIMS 恢复 | 私有 |
| SKY_ATO | 5104 | prepareSr，停止并隔离 FS | 私有 |
| SKY_ATO | 5105 | acceptRecoverySr，接收已批准恢复 SR | 私有 |
| SKY_ATO | 5106 | message，乘客 Title | 私有；不是协议 Message 类型 |
| SKY_ATO | 5107 | corridor，前方区块保活 | 私有 |
| SKY_ATO | 5110 | request，向 STCS 申请进路/许可 | 私有请求，不能充当 MA |
| SKY_ATO | 5111 | release，向 STCS 释放任务许可 | 私有 |
| SKY_ATO | 5112 | requestSr，申请待 PCC 批准 SR | 私有，不自行批准 |
| SKY_ATO | 5113 | requestFs，默认 FS 申请 | 私有，不自行授予 FS |
| SKY_ATO | 5114 | change，STCS→STF 道岔执行 | 私有，保留执行前 guard |
| SKY_ATO | 5115 | renew，静默续租已有进路 | 私有，不创建路径或授予 MA |
| SKY_ATO | 5116 | ParkingTarget / park，手推入位停车接管 | 私有，仅制动保持 |
| SKY_ATO | 5117 | releaseStandby，回库待命移交手动 | 私有，仅完整停稳的停车会话 |
| SKY_ATO | 5118 | acceptDepotHandover，确认移交自动待命 | 私有，显式释放回执、库位与停稳复核、一次性消费 |

5117/5118随STA 2.5.0新增，仍使用Message 6508，均非真实ATO标准编号。
新增不可变ManualDriver与ManualRelease反馈，不将断线/离车推定为显式释放。
[移管时序与STF 5.0破坏性变更](SAL-MANUAL-HANDOVER.md)。

1031/1032 仅致敬控制/反馈的方向与职责。Intent 是带 owner、sequence、expiresAt 的 Minecraft 目标距离/速度意图，
不是标准牵引/制动力百分比；View 是不可变观测，没有实现标准字段集合、周期或车辆接口。
这些 Java/JSON 整数不是标准 8 位字段的编码，不能发送给真实铁路设备。

## 已登记、尚未启用的参考编号

以下仅为 AtoProtocol.Packet 参考目录，`runtime=false`。Header 可描述这些已知类型，
执行入口与 DTO 不接受它们，尚未提供对应握手、计划下载、状态发布服务。

| 接口族 | STA Packet | 按编号顺序对应的概念 |
| --- | --- | --- |
| SUBSET_126 | 1000、1001、1002 | Handshake Request / Acknowledgement / Reject |
| SUBSET_126 | 1003、1004、1005 | Journey Profile Request / Profile / Acknowledgement |
| SUBSET_126 | 1006、1007 | Segment Profile Request / Profile |
| SUBSET_126 | 1008、1009 | Status Report / Acknowledgement |
| SUBSET_126 | 1010、1011 | Session Termination Request / Termination |
| SUBSET_130 | 1000、1001、1006 | ATO_ETCS_Status / ATO_ETCS_DMI / ETCS_ATO_Dynamic |
| SUBSET_139 | 1033 | RST_ATO_data slow |

运营线路不等于 STCS 工务线路；新增编号也没有将它们转换为标准 Journey/Segment Profile。

## 校验、兼容与权限边界

类型识别使用 `(M_VERSION, NID_MESSAGE, NID_INTERFACE, NID_PACKET)` 全组，不能只看整数。
SUBSET_126 Packet 1000 与 SUBSET_130 Packet 1000 是不同类型。Packet 1003 与既有 ETCS Message 1003 也是两个命名空间。
既有 MA **Message 1003 / Packet 1015**、遥测 **Message 1136** 保持原值及原契约。
ATO 请求必须取得 STCS 的有效许可才能牵引；控制意图不能充当 MA。

Header 构造时拒绝错误版本、Message、缺失接口族、未知接口族/Packet 配对；require(expected) 再验证具体操作与启用状态。
Intent/View 的规范构造器校验头部，Gson record 反序列化也经过这些校验。缺头部的旧 JSON 不能静默猜测。
旧参数 Java 构造器保留，自动生成正确头部；新增带 Header 的服务重载先校验再委托原实现。
SAL 实际控制、STF 默认 FS、STCS 道岔执行均已改用带 Header 入口。
旧无 Header 方法仅作为进程内兼容入口保留，不能用作未校验网络解码入口。

本次没有新增网络传输、鉴权或安全认证。编号不替代 owner/lease、序列围栏、图版本、有效期、TIMS、MA、
道岔占用与所属线程检查。物理实体操作仍只由 STF 在所属线程执行。

## 更新与验证

保留配置、任务、轨道图与占用账本；配套替换 STA/STF/STCS/SAL 并完整重启。View/Intent 新增 record 字段会改变
规范构造器与反射结构，外部消费者应重新编译并识别新头部；旧参数列表 Java 构造器保留。
PCC 既有 MA 与 SR 审批格式没有改变。

AtoProtocolContractTest 检查标准映射、接口族重号、Message/Packet 错位、版本错误、预留编号拒绝、Gson 往返及畸形头部、
不可变观测/路径，以及控制、许可、道岔入口在错误头部到达委托前拒绝。

验证结果：2026-11-19 完整构建成功，26.2、26.3 各 97 项 Java 测试通过（启用断言），包含新增契约测试及既有
权限、序列围栏、TIMS、MA、释放竞态测试。日志为 `Common/logs/ato-protocol-20261119.log`，
五个配套 JAR 与 SHA256 清单位于 `Latest/`。本补丁未再次运行实车场景，未修改生产服务器。

# SkyworldTrainAPI / STA 2.9.2

**[新增／修正 · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** depot纳管提示可补发、确认码过期刷新，停稳阈值与STF一致；出库接客和回库共用支持途中折返的分段寻路，经STCS释放旧MA、STA换向后逐段运行。hotbar等待区块加载不再清除手动牵引档，实际牵引仍被保护禁止。

**[修正 · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** 双车回库保留已选路径和库位，临时咽喉交通由 STCS 限制/延长 MA，不再因其他运行列车经过就丢计划。自动排道岔不得撤销另一列车 MA；目的库位净空、停放车障碍及真实故障检查保留。

**[修正 · SAL0.7.6 · 2026-11-26]** 回库库位复核、障碍保持、等待重新选位时维护原已接受进路的静默续租，避免超过1500ms心跳失效。复核默认2秒；车身暂不确定不再误报拓扑改变并立即丢目标。持续未知停为FAULT，真实空间／库位问题停稳后重选；状态显示具体原因并明确区分历史派车记录。

**[新增／修正 · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** 轨道坐标和方向统一取自同一份STF遥测，避免转弯时混用不同帧。定位暂态立即制动、默认复核2秒；节点容差0.75格、投影误差仍默认1.5格，均可有限配置。两份独立观测确认不同分支则提前恢复；不新建或扩大MA。原始故障持久保留并在PCC事件正文与/sal status直接显示。

**[新增／修正 · 2026-11-26]** STA2.9.2 / STF5.1.2 / SAL0.7.6：管理员 /sal override <列车名或UUID> 保持制动，确认撤销MA后退出SAL并保留手动状态，完成后可 /st drive。保留车身占用、TIMS及TR/PT，不授予行车许可。坡道定位改用STF经STA上报的实际railXYZ，不再floor实体坐标猜轨道。

**[新增 · SAL0.7.6 · 2026-11-26]** 叫车队列和超时结果新增逐车原因：未参选的状态／归属／手动保留／任务／观测／TIMS／车身条件；正反向路径与接客估计上限；阻断的其他列车名称、观测年龄、缺失或无法匹配图的车身位置；实际车身挡住的轨道边，以及图版本／计算时效变化。保持原安全条件，不自动解除TIMS或忽略其他列车。其它列车仅有TIMS保持本身并不造成全局阻断。诊断数量有上限，不显示为已穷尽全网。

**[修改 · SAL0.7.6 / PCC2.6.4 · 2026-11-26]** 两个访问点间自动取最短合法有向轨道路径，不再因存在更长绕行而报多路径。显式中间设备／图边仍约束走法；方向、图故障、闭环及MA检查保留。PCC沿真实轨道绘制，未完成草稿的已确认前缀也沿轨高亮，未确认部分才是估计线。

**[新增／破坏性变更 · 2026-11-26]** STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.6 / PCC2.6.4：公共station（第四行唯一名称）、awake线路＋始发站、共享待命车池、GLOBAL偏好／RESTRICTED限制、批次最小成本匹配、独立JVM计算线程、提交前版本与车身复核。PCC显示策略、访问引用、请求与决策；STCS发放MA、STA传递契约、STF操作实体的边界不变。旧awake需重写，旧站名自动迁移；保留列车/库位/账本。

[中文契约](../Common/doc/SAL-SHARED-DISPATCH.md) · [English contract](../Common/doc/SAL-SHARED-DISPATCH.en.md) · [中文生产测试](../SkyATO-Lite/TEST.zh.md)

**[修正 · 2026-11-25]** STF5.1.2 / SAL0.7.6：手动司机选定前进或后退后才自动申请FS/MA，取得驾驶权但空挡时不申请。手动纳管放宽为车身任意部分覆盖depot并停稳3秒，自动提供随机六位码；也可停稳后 `/sal handover` 立即申请，再 `/sal handover 六位码` 确认、`/st release` 移交。自动回库中心停车不变。


[中文](../Common/README.zh.md) · [English](../Common/README.en.md) · [Français](../Common/README.fr.md) · [Nederlands](../Common/README.nl.md) · [Русский](../Common/README.ru.md) · [日本語](../Common/README.ja.md) · [文語體](../Common/README.ja.classical.md)

## Current role / 当前职责

Typed contracts between components; STA owns no train entities. / 模块间契约，不持有列车实体。

## **[ADDED / CHANGED · 2026-11-24]**

v5 telemetry and RailGraph, executable MA/ATP contracts, v8 SAL operation/protocol, v9 graph dependencies, v10 route-editing service. / 遥测与轨道图、正式MA/ATP、SAL任务、图依赖和线路编辑契约。

[Architecture](../Common/doc/ARCHITECTURE.md) · [ATO Message/Packet contract](../Common/doc/STA-V8-ATO-PROTOCOL.md) · [v10 route editor](../Common/doc/PCC-ROUTE-EDITING.en.md)

## Compatible set / 配套版本

STA2.9.2 / STF5.1.2 / STCS4.3.6 / SAL0.8.0 / PCC2.6.4. Stop and back up before replacing plugins; preserve trains, graph, route state and occupancy ledgers. / 停服备份后更新，保留列车、图、线路与占用数据。

[Validation / 验证](../VALIDATION.md) · [Breaking changes / 破坏性更新](../README.md#breaking-changes--破坏性更新)


## **[2026-11-25] Centre-aligned storage / 中心停车**

**[破坏性修改 · 2026-11-25]** depot改为列车中心对牌；空间不足时只在本库位边界内调整计划中心，整列放不下则换位。同股道相邻库位沿轨中分并留净间隔；单端深处先停，双端评估两侧合法路径；出入库都排除车身阻挡。PCC显示容量、类型、范围和目标偏移。旧车头停车位置须手动调整，station语义不变。

**[BREAKING · 2026-11-25]** Depot stops now align the consist middle. Clamp the planned centre inside its own berth; reject an oversized train and consider another berth. Neighbouring markers split along-track space with a net gap. Single-ended roads fill deep first; double-ended roads consider both legal approaches. Body obstructions exclude blocked departures and arrivals. PCC displays capacity, access type, boundaries and target offset. Manually reposition old front-aligned trains; station semantics stay unchanged.

[中文设计、契约与配置](../Common/doc/SAL-CENTRE-STABLING.md) · [English design and contract](../Common/doc/SAL-CENTRE-STABLING.en.md)

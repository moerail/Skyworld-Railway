# SkyRail Suite — 2026.11.26

**Current compatible set / 当前配套：STA 2.9.2 · STF 5.1.4 · STCS 4.3.6 · SAL 0.8.0 · PCC 2.6.4**

[Release notes / 本版发布说明](Common/doc/RELEASE-2026.11.26.md) · [中文SAL手册](SkyATO-Lite/MANUAL.zh.md) · [生产测试步骤](SkyATO-Lite/TEST.zh.md)

**[UPDATED · SAL 0.8.0]** Bounded intermediate-reversal pickup/return routing is shipped (up to4 reversals); unbounded shunting, direction-policy proposals and a Python planner remain separate design work. [Runtime contract](Common/doc/SAL-MANEUVERS-AND-HANDOVER.en.md).

**[新增／修正 · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** depot纳管提示可补发、确认码过期刷新，停稳阈值与STF一致；出库接客和回库共用支持途中折返的分段寻路，经STCS释放旧MA、STA换向后逐段运行。hotbar等待区块加载不再清除手动牵引档，实际牵引仍被保护禁止。

**[修正 · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** 双车回库保留已选路径和库位，临时咽喉交通由 STCS 限制/延长 MA，不再因其他运行列车经过就丢计划。自动排道岔不得撤销另一列车 MA；目的库位净空、停放车障碍及真实故障检查保留。

**[修改 · STF 5.1.3 · 2026-11-26]** 自动驾驶不再固定 P2/B4：起步默认 P4，接近目标速度收档，常用制动默认 B6，停车保持 B7。可配置档位上限；MA、ATP、TIMS 和车型限制不变。

**[修正 · SAL0.7.6 · 2026-11-26]** 回库库位复核、障碍保持、等待重新选位时维护原已接受进路的静默续租，避免超过1500ms心跳失效。复核默认2秒；车身暂不确定不再误报拓扑改变并立即丢目标。持续未知停为FAULT，真实空间／库位问题停稳后重选；状态显示具体原因并明确区分历史派车记录。

**[新增／修正 · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** 轨道坐标和方向统一取自同一份STF遥测，避免转弯时混用不同帧。定位暂态立即制动、默认复核2秒；节点容差0.75格、投影误差仍默认1.5格，均可有限配置。两份独立观测确认不同分支则提前恢复；不新建或扩大MA。原始故障持久保留并在PCC事件正文与/sal status直接显示。

**[新增／修正 · 2026-11-26]** STA2.9.2 / STF5.1.2 / SAL0.7.6：管理员 /sal override <列车名或UUID> 保持制动，确认撤销MA后退出SAL并保留手动状态，完成后可 /st drive。保留车身占用、TIMS及TR/PT，不授予行车许可。坡道定位改用STF经STA上报的实际railXYZ，不再floor实体坐标猜轨道。

**[新增 · SAL0.7.6 · 2026-11-26]** 叫车队列和超时结果新增逐车原因：未参选的状态／归属／手动保留／任务／观测／TIMS／车身条件；正反向路径与接客估计上限；阻断的其他列车名称、观测年龄、缺失或无法匹配图的车身位置；实际车身挡住的轨道边，以及图版本／计算时效变化。保持原安全条件，不自动解除TIMS或忽略其他列车。其它列车仅有TIMS保持本身并不造成全局阻断。诊断数量有上限，不显示为已穷尽全网。

**[修改 · SAL0.7.6 / PCC2.6.4 · 2026-11-26]** 两个访问点间自动取最短合法有向轨道路径，不再因存在更长绕行而报多路径。显式中间设备／图边仍约束走法；方向、图故障、闭环及MA检查保留。PCC沿真实轨道绘制，未完成草稿的已确认前缀也沿轨高亮，未确认部分才是估计线。

**[新增／修改 · 2026-11-26]** PCC2.6.4 / SAL0.7.6 / STA2.9.2：线路验证显示首个失败区间的访问序号、设备名称和ID；列表两端标红，点击诊断跳转。修正后再次验证，不把未知到达方向下的后续推断当成真实错误。访问列表使用右侧≡手柄鼠标／触屏拖动排序，松手后提交一次，取消不提交；手柄聚焦后上下方向键也可调整。访问UUID、停站和折返属性随访问保留；重排不自动保存草稿或发布，仍需保存→验证→发布。

**[修正 · PCC2.6.4 · 2026-11-26]** 实时轮询不再重建未变化的SAL点选目标和线路列表；窄屏编辑面板不会因未选择列车而被刷新隐藏。SAL站点与线路点击不再进入地图拖动，草稿预览不拦截站点。短暂目录故障保留本地草稿与输入，过期数据禁用写入，恢复后继续原编辑；真实跨端冲突仍须人工解消。替换PCC后Ctrl+F5刷新页面。

**[新增／破坏性变更 · 2026-11-26]** STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.6 / PCC2.6.4：公共station（第四行唯一名称）、awake线路＋始发站、共享待命车池、GLOBAL偏好／RESTRICTED限制、批次最小成本匹配、独立JVM计算线程、提交前版本与车身复核。PCC显示策略、访问引用、请求与决策；STCS发放MA、STA传递契约、STF操作实体的边界不变。旧awake需重写，旧站名自动迁移；保留列车/库位/账本。

[中文契约](Common/doc/SAL-SHARED-DISPATCH.md) · [English contract](Common/doc/SAL-SHARED-DISPATCH.en.md) · [中文生产测试](SkyATO-Lite/TEST.zh.md)

**[修正 · 2026-11-25]** STF5.1.2 / SAL0.7.6：手动司机选定前进或后退后才自动申请FS/MA，取得驾驶权但空挡时不申请。手动纳管放宽为车身任意部分覆盖depot并停稳3秒，自动提供随机六位码；也可停稳后 `/sal handover` 立即申请，再 `/sal handover 六位码` 确认、`/st release` 移交。自动回库中心停车不变。


**[破坏性修改 · 2026-11-25]** depot改为列车中心对牌；空间不足时只在本库位边界内调整计划中心，整列放不下则换位。同股道相邻库位沿轨中分并留净间隔；单端深处先停，双端评估两侧合法路径；出入库都排除车身阻挡。PCC显示容量、类型、范围和目标偏移。旧车头停车位置须手动调整，station语义不变。

**[BREAKING · 2026-11-25]** Depot stops now align the consist middle. Clamp the planned centre inside its own berth; reject an oversized train and consider another berth. Neighbouring markers split along-track space with a net gap. Single-ended roads fill deep first; double-ended roads consider both legal approaches. Body obstructions exclude blocked departures and arrivals. PCC displays capacity, access type, boundaries and target offset. Manually reposition old front-aligned trains; station semantics stay unchanged.

[Centre stabling / 中心停车](Common/doc/SAL-CENTRE-STABLING.md) · [English contract](Common/doc/SAL-CENTRE-STABLING.en.md)


**[ADDED / FIXED · PCC 2.4.1]** Screen-sized selection highlights with H− / H+, reset and colour controls beside text scaling; infrastructure child filters for Balise, END, ORIGIN and switches. / 高亮固定屏幕线宽，文字缩放旁可调整大小、复位和颜色；新增应答器、END、ORIGIN、道岔子图层。

Module guides / 模块说明： [STF](SkyTrainFolia/README.md) · [STCS](STCS/README.md) · [STA](STA/README.md) · [SAL](SkyATO-Lite/README.md) · [PCC](SkyPCC/README.md)


**TRAIN Runs Asynchronously In Network**

Minecraft/Folia railway operation, signalling and control suite. / 基于Minecraft/Folia的铁路运行、列控与调度套件。

![SkyRail Suite](Common/logo.png)

## Unified map and route editor / 统一地图与线路编辑

**[ADDED / 新增 · 2026-11-24]** One RailGraph map with checkbox layers; existing-token editor, separate drafts/publications, pinned task versions, mandatory depot bindings and persistent two-sided conflict freeze. / 同一地图勾选图层、既有Token编辑、草稿与发布版隔离、任务固定版本、车辆段必选、两侧冲突冻结且人工解消。

[中文操作与契约](Common/doc/PCC-ROUTE-EDITING.md) · [English guide and contract](Common/doc/PCC-ROUTE-EDITING.en.md)

## Detailed manuals / 详细手册

- [中文](Common/README.zh.md)
- [English](Common/README.en.md)
- [Français](Common/README.fr.md)
- [Nederlands](Common/README.nl.md)
- [Русский](Common/README.ru.md)
- [日本語](Common/README.ja.md)
- [文語體・詳細技術取扱（日文併記）](Common/README.ja.classical.md)

Each manual starts with breaking changes and final deliverables, followed by installation, driving, commands, infrastructure, SAL sign layouts, depot operation, handover, MA/ATP, PCC and recovery. **[ADDED / CHANGED / REMOVED / QUERY]** labels identify this release; ~~strikethrough~~ preserves removed instructions. Previous manuals are archived and explicitly historical.

各语言手册以破坏性更新和本轮产物开篇，更新正文和操作步骤；**[新增／修改／删除／查询]** 高亮新版变动，~~删除线~~ 保留已废弃用法。历史原文单独归档。

## Breaking changes / 破坏性更新

- **[REMOVED / 删除]** STF legacy automatic station/spawn/destroy/property execution and ~~`mode auto` / `mode automatic`~~. Use SAL for automatic operation. Old signs remain but no longer execute. / STF旧伪ATO和自动牌执行链移除，自动运营统一经SAL。
- **[CHANGED / 修改]** ~~Default shadow MA; ATP inactive~~ → new driving sessions request FS by default with executable ATP. Movement still requires valid MA. Explicit shadow testing remains. / 默认请求FS并启用可执行保护，有效MA获批前保持制动。
- **[CHANGED / 修改]** SAL depot handover requires confirmation followed by explicit release of the same driving lease. `/st release` alone does not consent. / 库位纳管须先确认，再显式释放同一驾驶权。
- **[CHANGED / 修改]** RailGraph scans loaded chunks locally, preserving confirmed unloaded connections. Never clear ledgers or mix old STA binaries for migration. / 局部扫描保留未加载旧连接，升级不清账本、不混用STA。

## Final compatible set / 最终配套版本

| File | Responsibility / 职责 |
| --- | --- |
| `SkyworldTrainAPI-2.9.2.jar` | Contracts and telemetry / 契约与遥测 |
| `SkyTrainFolia-5.1.4.jar` | Physical execution, manual driving, TIMS/ATP / 实体执行与保护 |
| `STCS-4.3.6.jar` | RailGraph, occupancy, MA and routing / 图、占用、许可与排路 |
| `SkyATO-Lite-0.8.0.jar` | Services, calls, dwell, depot and handover / 运营任务与移交 |
| `SkyPCC-2.6.4.jar` | Infrastructure and operating maps / 工务与运营地图 |

**[ADDED / 新增]** Blue `[SkyATO-Lite]` prefix and grouped help; grey STF and yellow STCS. Version output includes SAL and current ATP wording. / SAL蓝色前缀、分组帮助，STF灰色，STCS黄色；修正版本提示。

**[ADDED / 新增]** PCC unified layers and operating editor, DRAFT/VALIDATED/PUBLISHED line styles, depot highlighting and parked/operating/unknown lists. / PCC统一图层及运营编辑、三种状态线型、车辆段停车位高亮及车辆列表。

**Design only / 仅设计**: up/down preferences, whole single-track-section reservations, unbounded general shunting and Python computation daemon. [Design / 设计](Common/doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md). These are not installed runtime features.

## Distribution / 分发包

**Extract the whole ZIP** before opening documents. Keep its directory structure: all local links and images stay inside it. / **完整解压ZIP后**再打开文档，保留目录结构，链接和图片使用包内相对路径。

```text
SkyRail-Suite-2026.11.24/
  README.md                 Start here / 入口
  Common/README.*.md         Seven manuals / 七版完整手册
  Common/doc/               Contracts, migration and historical manuals
  plugins/                  Five JARs + SHA256.json
  Source/                   Source archive / 源码包
  VALIDATION.md             Validation scope / 验证记录
  MANIFEST.sha256            All-file checksums / 全文件校验
```

Java25 and adapted Shiroha/Folia26.2 or26.3. Stop and back up, replace the five plugins together, merge configuration, restart. No Minecraft core, world, private token, certificate or production configuration is distributed.

环境为Java25及已适配Shiroha/Folia26.2或26.3。备份停服、整套替换、合并配置、重启；不含MC核心、世界、私有令牌、证书或生产配置。

[Validation / 验证](VALIDATION.md) · [Changelog / 更新记录](Common/CHANGELOG.md) · [Code licence / 代码许可](Common/LICENSE) · [Image permissions / 图片许可](Common/ASSET-LICENSE.md)

Game implementation inspired by railway concepts; not certified railway equipment or interoperable ETCS. / 游戏内铁路控制实现，不是认证铁路设备或标准ETCS互操作产品。

Acknowledgements: [bergerhealer](https://github.com/bergerhealer), [TrainCarts](https://github.com/bergerhealer/TrainCarts), [BKCommonLib](https://github.com/bergerhealer/BKCommonLib). TC/BKCommonLib are not runtime requirements.

# SkyRail Suite

<p align="center">
  <img src="logo.png" alt="SkyRail Suite" width="64" height="64">
</p>

**TRAIN** **R**uns **A**synchronously **I**n **N**etwork

> An open-source railway operation, control and simulation project built on Minecraft/Folia, combining a live multiplayer railway with experimental signalling and train-control models.
>
> 基于 Minecraft/Folia 的开源铁路运行、控制与仿真实验项目，将实际多人服务器中的铁路运行与信号、列控概念的实验探索相结合。

**Full manuals / 完整手册:** [中文](README.zh.md) | [English](README.en.md) | [Nederlands](README.nl.md) | [Français](README.fr.md) | [日本語](README.ja.md)

**Driver quick start / 玩家司机手册:** [中文](doc/driver/README.zh.md) | [English](doc/driver/README.en.md) | [Français](doc/driver/README.fr.md) | [日本語](doc/driver/README.ja.md)

从上车、取得驾驶权到手柄控制、MA 申请与停车交接；面向普通玩家，无需阅读管理员配置章节。

From boarding and claiming control to notches, MA requests and handover, without the administrator configuration chapters.

**趣味别册 / Just for fun:** [鐵道運轉試驗規程 · 舊式技術訓令體](README.ja.classical.md)

日语旧式技术训令体节编，非历史文献，不替代完整手册；技术限制与安全边界照旧。*文體ハ舊式ナレドモ、運轉ハ非同期ナリ。*

An abridged Japanese companion in an old-fashioned technical-regulations style, not a historical document or a replacement for the full manual. Technical and safety limitations remain unchanged. *Old-fashioned prose, asynchronous trains.*

**已用于 Skyworld，持续开发中。** 影子 MA/EoA 仍只读；新增的 `Enforced` 车载制动监督仅面向手动列车、需管理员显式启用，尚待实服验证。本项目不是认证铁路安全系统。

**In use on Skyworld; under active development.** Shadow MA/EoA remains advisory. Experimental onboard brake supervision is available only for manually driven trains after an explicit admin switch to `Enforced`; live validation is still pending. This is not a certified railway safety system.

## 项目概览 / Overview

SkyRail Suite / Skyworld Railway 起源于 **Skyworld Minecraft Server** 的铁路运行需求，目前在该服务器上实际使用并持续测试。随着项目发展，其范围从单项列车功能逐渐扩展为列车运动、基础设施、定位、占用、控制信息与调度等相互分工的系统职责。

SkyRail Suite / Skyworld Railway grew out of railway operating needs on **Skyworld Minecraft Server**, where it is used and continuously tested. Its scope has developed beyond individual train features into separate responsibilities for train motion, infrastructure, localisation, occupancy, control information and dispatching.

Minecraft 既是实际运行环境，也是可交互的铁路系统实验环境：用户可以建设线路、运行列车，观察道岔、占用、资源预约与控制信息如何相互作用。项目也希望帮助铁路相关学生与爱好者理解这些关系。这一教育用途仍处于探索阶段，并不意味着项目已是成熟教学平台或经过验证的铁路仿真软件。

Minecraft is both the live operating environment and an interactive railway-system test environment: users can build track, run trains and observe how points, occupancy, resource reservations and control information interact. The project also aims to make these relationships accessible to railway students and enthusiasts. That educational use is an exploration, not an established teaching platform or a validated railway simulator.

## 破坏性协议升级 / Breaking Protocol Upgrade

**当前为预发布 / Current pre-release: [`suite-v4.0.0`](https://github.com/moerail/Skyworld-Railway/releases/tag/suite-v4.0.0)。** 自动检查已通过，最近修复尚待服务器复测。Automated checks passed; the latest fixes still await live-server retesting.

**本地修复 / Local patch: STCS 4.0.2（未发布 / unpublished）**：包含 Windows 账本替换重试，以及 `/stcs admin ma restart`（权限 `stcs.admin`，支持控制台）。仅恢复 I/O 故障导致的 MA 停用；保留占用、备份账本，旧可执行许可对应列车需有新鲜停稳报告。恢复后司机重新申请 MA，不自动缓解制动；持续写入失败则继续停用。与上述发布的其他组件兼容，仅需替换 STCS。

STCS 4.0.2 retries transient Windows ledger replacement failures and adds `/stcs admin ma restart` (`stcs.admin`, console supported). Recovery is limited to I/O faults, backs up the ledger, retains occupancy and requires fresh stationary reports for outstanding executable authorities. Drivers must demand MA again; brakes are not released. Persistent write failures remain fail-closed. Only STCS needs replacing; the other released components remain compatible.

本批版本为 **STF 4.0.0 / STCS 4.0.0 / STA 2.0.0 / SkyPCC 2.0.0**。新增 STA v6 可执行授权与 PCC `/api/v6/operational-ma`、SR 审批接口；原有 v5 影子信息继续保留。不要混用旧版二进制：停服、备份后一起替换所安装的组件并强制刷新网页。STF 仍可独立安装。保留 RailGraph 与占用账本，不应删除数据来升级。

This release uses **STF 4.0.0 / STCS 4.0.0 / STA 2.0.0 / SkyPCC 2.0.0**. It adds executable STA v6 permissions and PCC `/api/v6/operational-ma` and SR approval, while preserving v5 shadow information. Do not mix old and new binaries: stop and back up the server, replace installed components together, then hard-refresh the browser. STF remains standalone-capable. Keep RailGraph and occupancy ledgers.

编号彩蛋区分 Message 与 Packet：v5 影子 MA 与 v6 实验性可执行 MA 均使用私有 **Message 1003 / Packet 1015**，列车遥测为 **Message 1136**。自定义列车删除为 **Message 2001**，不代表占用出清；图定位与等待/未分配状态分别为 **Message 2002 / 2003**。SR 审批是 SkyRail 服务调用和行车事件，不是新增的 ETCS 报文。仅借用 SUBSET-026 部分编号加 1000 的概念，不是 ETCS 报文编码或标准符合性声明。

The numbering homage keeps Message and Packet namespaces separate: v5 shadow MA and v6 experimental executable MA both use private **Message 1003 / Packet 1015**; telemetry uses **Message 1136**. Custom removal is **Message 2001**, never occupancy clearance; graph reports and waiting/inactive status use **Message 2002 / 2003**. SR approval is a SkyRail service call and railway event, not a new ETCS telegram. Selected SUBSET-026 identifiers plus 1000 are conceptual references only, not ETCS wire encoding or compliance.

[编号、升级与安全边界 / Numbering, migration and boundaries](doc/STA-V5-MIGRATION.md)

## 影子曲线 / Shadow Curves

STCS 新增管理员只读指令 `/stcs integrity [列车名|UUID]`（权限 `stcs.admin`），显示成员跨边分布和推断的节点通过。它不是完整计轴器，不释放占用；停车不会使占用超时消失，内存诊断重启后重新建立基线，持久化账本保持不变。仍需实服验证。

STCS adds the read-only admin command `/stcs integrity [train-name|uuid]` (`stcs.admin`) for member distribution and inferred node passages. It is not a completed axle counter and never clears occupancy. Parking does not expire occupancy; restarting resets the in-memory diagnostic baseline, not the persistent ledger. Live-server validation remains necessary.

STF 提供只读影子速度曲线，以及仅对手动列车生效的实验性 `Enforced` 制动监督。影子通道仍不施加制动；管理员须在停车时显式切换通道，司机取得可执行 MA 后才能进入 `FS/SH/SR`。ATP 状态在计分板显示为“通道 | 运行模式”，例如 `强制保护 | SR`。STF 内置伪 ATO 自动列车不进入该状态机。

STF provides read-only shadow speed curves and experimental `Enforced` brake supervision for manual trains only. The shadow channel never intervenes; an admin must explicitly change channels while stopped, and a driver needs an executable MA to enter `FS/SH/SR`. The sidebar shows `channel | operating mode`, for example `Enforced | SR`. STF's built-in pseudo-ATO automatic trains do not enter this state machine.

[更新说明 / Release notes](CHANGELOG.md)

本轮修正了 SH/SR 模式限速制动、TR 冒进提示和远端 SR 审批。强制保护下，SH/SR 默认 40 km/h 上限超限后按小幅判定容差投入 B7，不等待 FS 的宽松超速计时；EoA 曲线仍可要求更低速度。计分板与司机 BossBar 显示实际 ATP B7/EB，TR 另有消息和可配置音效。SR 目标搜索与每次 MA 窗口分开，默认分别为 5000 m 与 120 m；已保存轨道图可跨未加载普通区块核对，但未知状态和资源冲突仍限制授权。四语言司机手册现从 MA、EoA、ATP 等术语入门。

This update corrects SH/SR mode-speed braking, TR overrun feedback and distant SR approval. In the enforced channel, SH/SR default to a 40 km/h ceiling with B7 beyond a small detection tolerance, without the relaxed FS overspeed delay; the EoA curve can require a lower speed. The sidebar and driver BossBar show ATP B7/EB, and TR adds a message and configurable sound. SR target search and each rolling MA window are separate, defaulting to 5000 m and 120 m respectively. Saved geometry can span unloaded plain-track chunks, while unknown states and conflicts still restrict authority. The four-language driver manuals now begin with MA, EoA and ATP terminology.

## 主要模块 / Main Modules

| 插件 / Plugin | 版本 / Version | 职责 / Responsibility |
| --- | --- | --- |
| [SkyTrainFolia (STF)](SkyTrainFolia/) | 4.0.0 | 列车运动、编组、驾驶控制、车型、牌子、实体道岔执行与驾驶室 HMI。<br>Train motion, consists, driving control, vehicle profiles, signs, physical point actuation and cab HMI. |
| [Skyworld Train Control System (STCS)](STCS/) | 4.0.0 | RailGraph、线路归属、定位、保留占用与实验性 MA/EoA 分配。<br>RailGraph, line attribution, localisation, retained occupancy and experimental MA/EoA allocation. |
| [SkyworldTrainAPI (STA)](STA/) | 2.0.0 | 插件之间的版本化进程内服务契约、遥测与行车事件。<br>Versioned in-process service contracts, telemetry and railway events between plugins. |
| [SkyPCC](SkyPCC/) | 2.0.0 | HTTP/SSE 调度显示、基础设施详情、事件日志与经过身份验证的道岔操作、SR 审批。<br>HTTP/SSE dispatch display, inspectors, event log, authenticated turnout control and SR approval. |

[shared/](shared/) 包含编译进各插件的共通指令、帮助与版本界面代码，并非第五个运行时插件。STA 是进程内 Java API，而不是通用的 Python 网络协议。

[shared/](shared/) contains common command/help/version code compiled into the plugins, not a fifth runtime plugin. STA is an in-process Java API; it is not a general-purpose Python network protocol.

## 当前能力 / Current Capabilities

- **列车运行：** 基于轨道的编组运动、牵引与制动级位、车型参数、手动控制，以及基础的牌子驱动自动停站。这种伪 ATO 并非完整 ATO 系统。
- **基础设施与定位：** 有向 RailGraph、物理连通关系、线路与里程归属、应答器、道岔及列车位置跟踪。
- **占用与影子许可：** 保留占用证据、空间资源冲突、预约，以及实验性的 MA/EoA 计算与显示。不可用或不确定的观测被显式表达，而不是视为空闲。
- **实验性手动列车保护：** 显式启用的 `Enforced` 通道、STA v6 可执行许可、`SB/FS/SH/SR/TR/PT` 状态和车载制动干预；仍待实服验证，不适用于内置伪 ATO 列车。
- **调度与车载信息：** 实时 PCC 轨道图、占用与预约着色、列车和基础设施详情、事件、道岔控制请求，以及面向司机的 HMI。
- **开发工具：** Java 回归测试、PCC JavaScript/浏览器测试，以及验证拓扑、占用、预约和 EoA 的离线 Python 测试台。这些工具补充而不替代实服测试。

**English:**

- **Train operation:** track-based consist movement, traction/brake notches, vehicle profiles, manual controls and basic sign-driven automatic station operation. This pseudo-ATO is not a complete ATO system.
- **Infrastructure and localisation:** a directed RailGraph, physical connectivity, line/mileage attribution, balises, points and train-position tracking.
- **Occupancy and shadow authority:** retained occupancy evidence, spatial resource conflicts, reservations and experimental MA/EoA calculation and display. Unavailable or uncertain observations are represented rather than assumed clear.
- **Experimental manual-train protection:** explicitly enabled `Enforced` channel, STA v6 executable permission, `SB/FS/SH/SR/TR/PT` states and onboard brake intervention; live validation remains outstanding and built-in pseudo-ATO trains are excluded.
- **Dispatching and onboard information:** a live PCC track diagram, occupancy/reservation colouring, train and infrastructure inspectors, events, turnout-control requests and driver-facing HMI.
- **Development tools:** Java regression tests, PCC JavaScript/browser tests and an offline Python testbench for topology, occupancy, reservations and EoA. These complement, rather than replace, live-server testing.

STF 2.1.3 包含 `V_target` 属性牌、无线路标定的车站检测、领车停车对位，以及基于剩余距离反馈的进站制动。详见[自动牌子与验收步骤](doc/BASIC-AUTOMATIC-SIGNS.md)。

STF 2.1.3 includes `V_target` property signs, station detection without line calibration, lead-cart stopping alignment and distance-feedback station braking. See [automatic signs and acceptance checks](doc/BASIC-AUTOMATIC-SIGNS.md).

## 教育与仿真目标 / Educational and Simulation Goals

项目希望为关注铁路信号、列车控制和运输组织的学生与爱好者提供低门槛实验环境。未来可探索通过运行场景理解以下内容：

- 轨道与区段占用、列车定位、道岔。
- 进路、资源冲突、预约与调度决策。
- 行车许可（MA）与行车许可终点（EoA）。
- 未知或不可用的基础设施、通信中断与信息降级。
- 调度侧与车载侧信息的差异，以及为什么**未知不能自动等同于空闲**。

相比静态示意图，可交互世界允许学习者同时观察列车运动、基础设施状态和控制决策。这些是教育探索目标，并不表示已有完整课程体系、完备的故障注入工具或符合铁路标准的培训环境。

**English:**

The intended direction is a low-barrier experimental environment for students and enthusiasts interested in railway signalling, train control and transport operations. Potential exercises would use operating scenarios to explore:

- Track/section occupancy, train localisation and points/switches.
- Routes, resource conflicts, reservations and dispatching decisions.
- Movement Authority and End of Authority.
- Unknown or unavailable infrastructure, communication loss and degraded information.
- Differences between dispatcher-side and onboard information, and why **unknown must not automatically mean clear**.

Unlike a static diagram, an interactive world lets learners observe train motion alongside infrastructure states and control decisions. These are educational goals, not claims of a complete curriculum, implemented fault-injection suite or standards-compliant training environment.

## 安全与适用边界 / Safety and Scope Limitations

- **不得用于真实铁路安全关键场景。** 本项目既不是认证铁路安全系统，也不是认证联锁。
- **影子 MA/EoA 仍不触发制动。** `Enforced` 是另一个需要显式启用的实验通道，仅对手动列车按有效授权施加制动，尚非实服验证或认证的 ATP 实现。
- 司机失能紧急制动、手动紧急制动和 RECOVERING 制动保持是独立的实际控制功能，其存在不代表已实现基于 MA 的防护。
- 使用铁路术语或借鉴设计思想，不构成符合 **ETCS SUBSET-026 或任何其他铁路安全标准**的声明。
- 区块卸载、观测缺失或基础设施未知，不能自动解释为轨道空闲。保留证据与实物状态核验承担不同职责。
- 浏览器渲染、插值和 UI 数据不一定是权威的安全状态数据。可见的列车、高亮的进路或成功的演示都不能证明系统安全。

车型参数与停站制动模型是面向游戏的近似。离线测试台不验证 Folia 调度、实体生命周期或真实铁路制动安全。

**English:**

- **Not for real railway safety-critical use.** This is neither a certified railway safety system nor a certified interlocking.
- **Shadow MA/EoA never applies brakes.** `Enforced` is a separate, explicitly enabled experimental channel for manual-train braking against validated permission. It has not been live safety-validated or certified as ATP.
- Driver-loss emergency braking, manual emergency braking and RECOVERING brake hold are separate active functions; their existence does not establish MA-based protection.
- Railway terminology and design inspiration do not constitute compliance with **ETCS SUBSET-026 or any other railway safety standard**.
- Unloaded chunks, missing observations and unknown infrastructure must not automatically be interpreted as clear track. Retained evidence and physical validation have different roles.
- Browser rendering, interpolation and UI data are not necessarily authoritative safety-state data. A visible train, a highlighted route or a successful demonstration is not proof of safety.

Vehicle profiles and station-braking models are game-oriented approximations. The offline testbench does not validate Folia scheduling, entity lifecycle or real-world braking safety.

## 开发状态 / Development Status

这是一个持续开发、已部署在 Skyworld 实际服务器环境中的开源项目，而非完成品铁路仿真软件。功能、API 和行为仍可能变化；部分铁路概念采用实验性模型，实际部署不代表完整性、可靠性或认证保证。

This is an actively developed open-source project deployed in a live Skyworld server environment, not a finished railway simulation product. Features, APIs and behaviour may change. Some railway concepts are experimental models, and live deployment is not a guarantee of completeness, reliability or certification.

报告问题时，请尽量提供相关服务端与插件版本，以及可复现的小型线路场景。涉及占用或行车许可的修改，应说明如何处理未知、过期和卸载状态；不能为了让演示继续而抹去不确定性。

Reproduce issues with the relevant server/plugin versions and a small track scenario where possible. Changes involving occupancy or authority should explain how unknown, stale and unloaded states are handled; do not erase uncertainty simply to make a demonstration proceed.

## 文档、构建与测试 / Documentation, Build and Tests

- [架构与维护边界](doc/ARCHITECTURE.md)：模块职责、Folia 线程、依赖和测试布局。
- [验证记录](doc/VALIDATION.md)：特定日期的迁移结果与已知测试缺口，不代表当前全部测试均通过。
- [线路边界语义](doc/STCS-BOUNDARY-MIGRATION.md)：ORIGIN/END、物理连通与里程。
- [删除后的占用清理](doc/DELETION-CLEARANCE.md)：证据保留、确认删除与操作员核验。
- [TrainManager 职责拆分](doc/TRAIN-MANAGER-REFACTOR.md)：内部边界与保持不变的契约。
- [发布检查清单](doc/PUBLISHING.md)：源码打包、依赖、素材与发布核验。

**English:**

- [Architecture and maintenance boundaries](doc/ARCHITECTURE.md): module ownership, Folia threading, dependencies and test layout.
- [Validation record](doc/VALIDATION.md): dated migration results and known test gaps, not a blanket statement that every current test passes.
- [Line-boundary semantics](doc/STCS-BOUNDARY-MIGRATION.md): ORIGIN/END, physical connectivity and mileage.
- [Deletion clearance](doc/DELETION-CLEARANCE.md): evidence retention, confirmed removal and operator checks.
- [TrainManager responsibility split](doc/TRAIN-MANAGER-REFACTOR.md): internal boundaries and preserved contracts.
- [Publishing checklist](doc/PUBLISHING.md): source packaging, dependencies, assets and release checks.

当前构建基线为 **JDK 25，以及已准备好的 Shiroha/Folia 26.2 服务端依赖目录**。在仓库根目录执行：

Current build baseline: **JDK 25 and a prepared Shiroha/Folia 26.2 server dependency tree**. From the repository root:

```powershell
./build.ps1 -ServerRoot /path/to/prepared-server -JavaHome /path/to/jdk-25
```

[构建脚本](build.ps1) 从该服务端目录读取 `versions/26.2/shiroha-26.2.jar` 和 `libraries/`，构建四个 JAR，并启用断言运行 Java 测试入口。依赖不随源码打包。产物写入 `artifacts/` 和 `target/`，不会启动、部署或修改服务器。

The [build script](build.ps1) reads `versions/26.2/shiroha-26.2.jar` and `libraries/` from that server tree, builds four JARs and runs Java test entry points with assertions enabled. Dependencies are not bundled. Output is written under `artifacts/` and `target/`; the build does not start, deploy to or modify the server.

其他检查需在仓库根目录分别运行：

Additional checks, run separately from the repository root:

```powershell
npm --prefix SkyPCC install
npm --prefix SkyPCC test
npm --prefix SkyPCC run test:browser
python -m unittest discover -s STCS/tools/testbench -p "test_*.py"
```

[SkyPCC 测试](SkyPCC/tests/) 需要 Node.js；浏览器测试还需要安装 Playwright 的 Chromium，或通过 `PLAYWRIGHT_CHANNEL` 指定已安装的浏览器（例如 `msedge`）。[验证记录](doc/VALIDATION.md) 列出了历史浏览器测试缺口和可选的真实轨道图测试。实际运动、区域线程归属、实体生命周期及红石行为仍需实服测试。

The [SkyPCC tests](SkyPCC/tests/) require Node.js; browser tests additionally require Playwright's Chromium installation, or an installed browser selected through `PLAYWRIGHT_CHANNEL` (for example `msedge`). The [validation record](doc/VALIDATION.md) identifies historical browser-test gaps and optional real-graph tests. Live testing remains necessary for physical motion, region ownership, entity lifecycle and redstone.

安装时请使用 [Releases](https://github.com/moerail/Skyworld-Railway/releases) 中相互匹配的 JAR，备份服务器数据并在部署前测试。上方五语独立手册涵盖权限、指令、配置和操作教程。[package-source.ps1](package-source.ps1) 用于生成仅含源码的 ZIP。

For installation, use matching JARs from [Releases](https://github.com/moerail/Skyworld-Railway/releases), back up server data and test before deployment. The five standalone manuals above cover permissions, commands, configuration and operating tutorials. [package-source.ps1](package-source.ps1) creates a source-only ZIP.

## 致谢 / Acknowledgements / Dankwoord

**中文：** 感谢 [bergerkiller](https://github.com/bergerkiller) 及 [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)、[TrainCarts](https://github.com/bergerhealer/TrainCarts) 的贡献者，为 Minecraft 插件生态与矿车铁路玩法所做的长期工作。TrainCarts 的牌子接口和运行流程为本项目提供了重要参考。SkyRail Suite 是独立项目，不要求安装 BKCommonLib 或 TrainCarts，也不代表二者的官方版本、认可或完整兼容实现；相关 TrainCarts 版权与许可声明继续保留。

**English:** Thank you to [bergerkiller / Berger Healer](https://github.com/bergerhealer) and the contributors to [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) and [TrainCarts](https://github.com/bergerhealer/TrainCarts) for their long-standing work on Minecraft plugins and minecart railways. TrainCarts' sign interfaces and operating workflows have been important references for this project. SkyRail Suite is an independent project: it does not require either plugin, is not an official or endorsed version of either, and does not claim complete compatibility. The applicable TrainCarts copyright and license notice is retained.

**Nederlands:** Met dank aan [bergerkiller / Berger Healer](https://github.com/bergerhealer) en de bijdragers aan [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) en [TrainCarts](https://github.com/bergerhealer/TrainCarts) voor hun jarenlange werk aan Minecraft-plugins en spoorwegen met mijnkarretjes. De bordinterfaces en bedieningsprocedures van TrainCarts waren belangrijke referenties voor dit project. SkyRail Suite is een onafhankelijk project: geen van beide plugins is vereist, het is geen officiële of door hen onderschreven versie en volledige compatibiliteit wordt niet geclaimd. De toepasselijke copyright- en licentievermelding van TrainCarts blijft behouden.

## 授权 / License / Licentie

代码与文档 / Code and documentation / Code en documentatie: [MIT](LICENSE), `Copyright (c) 2026 Skyworld Minecraft Server contributors`.

维护联系 / Maintainer / Beheerder: [moerail](https://github.com/moerail).

Logo 与角色图片不适用代码 MIT 授权。SkyPCC 内置吉祥物已获准随包分发，这不等于将图片按 MIT 开放。

Logos and character artwork are outside the code's MIT license. Distribution of the bundled SkyPCC mascot is permitted; this does not MIT-license the artwork.

Logo's en personageafbeeldingen vallen niet onder de MIT-codelicentie. Verspreiding van de meegeleverde SkyPCC-mascotte is toegestaan; hierdoor valt de afbeelding niet onder MIT.

## 在真实服务器上体验 / See It in Action

SkyRail Suite 正在 **Skyworld Minecraft Server** 中运行和持续测试。欢迎加入服务器，或打开 SkyPCC 实时大屏，看看玩家建设的铁路和运行中的列车。

SkyRail Suite is running and being tested on **Skyworld Minecraft Server**. Join us in-game or explore the live SkyPCC dashboard to see our player-built railway network in operation.

> 这是实际部署与持续测试的展示，不代表所有功能已完成或不存在缺陷。影子 MA/EoA 不执行制动；新 `Enforced` 功能需单独实服验证。
>
> This is a live deployment, not a guarantee of completeness or reliability. Shadow MA/EoA does not brake; the new `Enforced` mode still requires separate live validation.

### 加入服务器 / Join the Server

**Minecraft Java Edition 26.2** · 无需安装铁路客户端模组 / No railway client mod required

| 服务 / Service | 地址 / Address |
| --- | --- |
| 生存服 / Survival server | `mc.skywor1d.cn` |
| 铁路测试服 / Railway test server | `irvine-filth.tun.ply.gg` |
| **SkyPCC 实时大屏 / Live railway dashboard** | **[打开大屏 / Open Dashboard](http://mc.skywor1d.cn:8765/)** |
| 皮肤站 / Skin & authentication service | [skin.deviy.cn](https://skin.deviy.cn) |

**测试服会删档，请勿存放需要长期保留的建筑或物品。**  
**The test server may be wiped. Do not use it for builds or items you want to preserve.**

登录请按服务器要求配置正版验证或外置登录；需要帮助可通过下方社区联系管理员。  
Configure official or external authentication as required by the server. Contact our community for help joining.

### 社区 / Community

- **QQ 群：204749370**
- **Telegram:** [Skyworld MC](https://t.me/skyworld_mc)
- **Discord:** [Join our Discord](https://discord.gg/bzM3tfm)
- **OOPZ:** [加入频道 / Join channel](https://oopz.cn/i/LExN5k) · `381998378`

### 项目历史 / Project History

SkyRail Suite 延续 SkyTrain Suite 开发线；插件名、指令和数据目录保持不变。下述 Skyworld 服务器历史早于本套件本身。

SkyRail Suite continues the SkyTrain Suite development line; the plugin names, commands and data directories remain unchanged. The Skyworld server history below predates this suite.

**自 2013 年起，一直都在。**

目前开放生存服；博物馆服正在筹备中，计划保存并展示自 2014 年以来能够找回的世界存档，包括 LL 小镇。

**Still here, since 2013.**

Our survival server is open. A museum server is being prepared to preserve and showcase recoverable worlds dating back to 2014, including LL Town.

服务器不提供锁箱或圈地保护，请自行保管物品、尊重他人的建筑与劳动。经查实的恶意破坏将被封禁。

There is no chest-locking or land-claim protection. Look after your belongings and respect other players' work. Confirmed malicious griefing results in a ban.

### 支持服务器 / Support the Server

欢迎自愿支持日常网络与电力开支：  
Optional contributions towards our network and electricity costs:

**[爱发电 / Support Skyworld](https://afdian.com/a/skyworld)**

**[支持本人 / Buy me a coffee](https://buymeacoffee.com/moerail)**

我们的宗旨是让每个玩家在这里玩得开心。  
Our goal is simple: a place where everyone can enjoy playing.

## 联系作者 / Contact 
- **邮箱 / Email：me@saionjirin.com; saionjirin@outlook.com**
- **QQ群 / QQ Group: 204749370**
- **Telegram: [@saionjirin](https://t.me/saionjirin)**

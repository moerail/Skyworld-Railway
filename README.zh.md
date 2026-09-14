# SkyRail Suite

<p align="center">
  <img src="logo.png" alt="SkyRail Suite" width="64" height="64">
</p>

[首页 / Home](README.md) | **中文** | [English](README.en.md) | [Nederlands](README.nl.md)

**中文完整手册，可独立阅读，无需配套文档。**

SkyRail Suite 是 SkyTrain Suite 开发线的新仓库名称；现有插件名、Java 包名、指令和数据目录保持不变。


面向 Minecraft / Folia 的列车运行、铁路基础设施、影子列控与调度显示套件。

本文依据 **2026-09-13 当前本地源码**整理，面向服务器管理员、司机、线路建设者和扩展开发者。历史讨论中的目标不等于已实现功能；旧版安装组合和命令以本手册及当前源码为准。

> **开发版安全边界：目前已能在线计算、分配和显示影子 MA/EoA，但 ATP 不根据它施加制动。申请成功、PCC 显示空闲或 RBC 在线，都不是安全开车保证。司机失能 EB、手动 EB 和 RECOVERING 制动保持是另外的实际控车功能。**

## 致谢 / Acknowledgements / Dankwoord

**中文：** 感谢 [bergerkiller / Berger Healer](https://github.com/bergerhealer) 及 [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)、[TrainCarts](https://github.com/bergerhealer/TrainCarts) 的贡献者，为 Minecraft 插件生态与矿车铁路玩法所做的长期工作。TrainCarts 的牌子接口和运行流程为本项目提供了重要参考。SkyRail Suite 是独立项目，不要求安装 BKCommonLib 或 TrainCarts，也不代表二者的官方版本、认可或完整兼容实现；相关 TrainCarts 版权与许可声明继续保留。

**English:** Thank you to [bergerkiller / Berger Healer](https://github.com/bergerhealer) and the contributors to [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) and [TrainCarts](https://github.com/bergerhealer/TrainCarts) for their long-standing work on Minecraft plugins and minecart railways. TrainCarts' sign interfaces and operating workflows have been important references for this project. SkyRail Suite is an independent project: it does not require either plugin, is not an official or endorsed version of either, and does not claim complete compatibility. The applicable TrainCarts copyright and license notice is retained.

**Nederlands:** Met dank aan [bergerkiller / Berger Healer](https://github.com/bergerhealer) en de bijdragers aan [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) en [TrainCarts](https://github.com/bergerhealer/TrainCarts) voor hun jarenlange werk aan Minecraft-plugins en spoorwegen met mijnkarretjes. De bordinterfaces en bedieningsprocedures van TrainCarts waren belangrijke referenties voor dit project. SkyRail Suite is een onafhankelijk project: geen van beide plugins is vereist, het is geen officiële of door hen onderschreven versie en volledige compatibiliteit wordt niet geclaimd. De toepasselijke copyright- en licentievermelding van TrainCarts blijft behouden.

## 授权与维护

代码与文档采用 MIT 许可证：`Copyright (c) 2026 Skyworld Minecraft Server contributors`。维护联系账号：[moerail](https://github.com/moerail)。完整许可随源码和四个插件 JAR 分发。

Logo 与角色图片不包含在代码 MIT 授权内。项目维护者已确认 SkyPCC 内置角色吉祥物允许随包分发；此许可不等于将图片改为 MIT 授权，也不自动授予修改或独立再利用权利。

STF 保留 `META-INF/NOTICE-TrainCarts.txt`，包含自动牌子兼容工作参考提交 `9813810aa7e751d3a00d3d087186d44f6e90df10` 与 TrainCarts 原始 MIT 声明。套件署名不替代第三方版权声明。

## 目录

1. [目标与组件](#1-目标与组件)
2. [安装与升级](#2-安装与升级)
3. [权限与语言](#3-权限与语言)
4. [第一列手动列车](#4-第一列手动列车)
5. [指令参考](#5-指令参考)
6. [车型与属性](#6-车型与属性)
7. [线路与道岔建设](#7-线路与道岔建设)
8. [自动牌子与站停](#8-自动牌子与站停)
9. [影子 MA 与模式](#9-影子-ma-与模式)
10. [SkyPCC 网页](#10-skypcc-网页)
11. [声音与 HMI](#11-声音与-hmi)
12. [STA 契约与报文](#12-sta-契约与报文)
13. [数据与故障排查](#13-数据与故障排查)
14. [验收与后续目标](#14-验收与后续目标)
15. [开发与构建](#15-开发与构建)

## 1. 目标与组件

SkyTrain Suite 把 Minecraft 作为可交互的铁路运行环境：玩家建设实际轨道、驾驶矿车编组，插件管理列车运动、识别线路，再把位置、占用与行车许可显示到驾驶台和调度台。

套件引入驾驶权、资源占用、进路冲突、MA、EoA、冻结保留等概念，参考 ETCS 的职责分离思想，但不是 SUBSET-026 兼容设备、认证联锁或真实铁路安全系统。目标是在操作直观的前提下逐步建立可验证的游戏列控逻辑，而不是把现实铁路全部规章搬进 Minecraft。

| 组件 | 当前版本 | 主要职责 |
| --- | --- | --- |
| SkyTrainFolia / STF | `2.1.0-alpha.7` | 矿车编组、运动与过弯、驾驶权、牵引制动、车型、牌子、实体道岔执行、HMI 和声音 |
| STCS | `2.2.0-alpha.1` | 基础设施、有向 RailGraph、线路里程、定位、保留占用账本、影子 MA/EoA 和局部道岔检查 |
| SkyworldTrainAPI / STA | `0.8.0` | 插件间带版本的服务契约、遥测、成员观测、驾驶台状态、许可和事件交换 |
| SkyPCC | `0.8.1` | 网页线路图、车辆/设施 Inspector、占用与预约显示、事件栏、经鉴权的道岔控制 |

```text
Minecraft 玩家 / 矿车 / 轨道 / 红石
                 |
                STF  实体运动、驾驶与执行
                 |
                STA  服务契约、遥测、事件
                 |
               STCS  图、定位、占用、影子许可
                 |
               SkyPCC  网页观察与操作入口
```

这是职责示意，不表示所有调用必须沿图串行转发。PCC 的屏幕插值不是安全定位数据，网页也不直接决定哪些轨道能够授权。

### 当前能力边界

| 范围 | 当前情况 |
| --- | --- |
| 手动驾驶、驾驶权、司机离车 EB | 已实现，每次上车需显式取得驾驶权 |
| 编组、轨道坐标运动与高速显示适配 | 已实现；跨区域、高速、第三方插件组合仍需实服验收 |
| Station 伪自动驾驶 | 已实现 MVP，使用车型 P/B 级位；不是完整 ATO |
| 图、线路归属、里程、占用保留 | 已实现；超时或卸载不能自动证明出清 |
| 在线 MA/EoA、空间占用与预约 | 已实现影子版本，不执行 ATP 制动 |
| 网页道岔控制 | 已实现鉴权、局部检查、异步 PENDING |
| 车载速度曲线、超速与 EoA 制动监督 | 尚未实现 |
| 完整目的地自动排路、时刻表 ATO | 尚未实现完整系统；目的地/route 字段不代表已排路 |
| FS、SR、SH、SB、TR、PT 等 ETCS 模式 | 尚未实现，不应把现有模式当成它们的完整替代 |
| 独立 SIR、SkyCBI、Python RBC 服务 | 架构讨论方向，不是当前安装组件 |

## 2. 安装与升级

### 环境

- 当前适配基线是 **Shiroha / Folia 26.2 与 Java 25**，应使用经过验证的同系列服务端构建。
- STF 有版本相关的实体运动/显示适配。`folia-supported: true` 不代表所有 Folia 版本都兼容；`api-version: 1.13` 也不是最低可运行游戏版本的承诺。
- PCC 使用普通浏览器，不要求安装客户端模组。
- TC / BKCommonLib 不是本套件的必需依赖。不要让两个控车插件同时接管同一辆矿车。

### 首次安装

1. 备份世界和整个 `plugins` 目录，优先在测试服验证。
2. 停服，把下面四个 JAR 放入服务器 `plugins`，移走旧版同名插件 JAR。
3. 完整启动一次生成默认配置，确认四个插件启用。
4. 停服调整配置，再完整启动。不要用第三方热卸载工具替换这些插件。
5. 游戏中执行 `/st version`、`/stcs status`；服务器本机浏览器打开 `http://127.0.0.1:8765/`。

从仓库 Releases 获取匹配的一组 JAR；本地构建输出位于 `artifacts/`。当前文件名：

```text
SkyTrainFolia-2.1.0-alpha.7.jar
STCS-2.2.0-alpha.1.jar
SkyworldTrainAPI-0.8.0.jar
SkyPCC-0.8.1.jar
```

STF/STCS 对 STA 使用软依赖，但完整套件应四者一起安装。PCC 硬依赖 STCS 和 STA。STF 单独运行不具备完整套件的图、MA 与调度显示能力。

### 升级约定

- 按兼容组合更新，尤其是带 STA 契约变化的版本；不要把 artifacts 内历史 JAR 全部装上。
- 保留配置与数据，对照当前默认配置合并新键。新增键不一定自动写入旧配置。
- `/st reload` 会重载配置和列车等数据，**不是只刷新声音**，必须先停稳所有列车。
- STCS/PCC 配置通过完整重启应用；没有在本手册中提供虚构的 `/stcs reload` 或 `/skypcc reload`。
- 图识别规则/线路结构变化后，加载受影响线路再 `/stcs rebuild`；单纯网页、声音更新通常无需重建。
- 更新网页后强制刷新。回退应恢复同一时间点的 JAR、世界和数据备份，不能随意混用旧版程序与新版账本。

## 3. 权限与语言

### 权限节点

| 权限 | 默认 | 主要用途 |
| --- | --- | --- |
| `skytrain.use` | 所有人 | 基本查询、个人语言/单位、常规驾驶入口 |
| `skytrain.admin` | OP | 编组、属性、模板、远程控车、道岔管理、保存/重载 |
| `stcs.use` | 所有人 | 附近设施查询、图状态 |
| `stcs.ma` | 所有人 | 司机申请/释放 MA，仍需有效驾驶权与允许的模式 |
| `stcs.admin` | OP | 设施登记、重建/导出、占用与 MA 诊断、模式和道岔管理 |

权限不等于驾驶权。拥有 `stcs.ma` 也不能代替其他司机申请 MA；管理员切换模式需要坐在目标列车内。需要附近实体/设施位置的指令也不能一概从控制台执行。

PCC 的 HTTP 控制使用独立 token、回环连接与同源检查，不自动继承玩家 OP 权限。不要向普通乘客发放控制 token。

### 中英法日

```text
/st lang zh
/st lang en
/st lang fr
/st lang jp
/st help 2 zh
/stcs help 1 fr
/sta version ja
/skypcc help
```

`ja` 和 `jp` 均可用于日语。四个根命令都支持 `help` 与 `version`。默认优先使用 STF 的玩家语言偏好；HMI、模式和 MA 提示跟随 `/st lang`。部分旧管理/诊断文本仍可能是固定中文或英文，不宣称所有历史字符串已翻译。

PCC 的语言选择独立保存在浏览器，不修改游戏内语言。

## 4. 第一列手动列车

在平直、已加载、没有其他列车的测试轨道放几辆矿车。使用小扫描半径，避免把隔壁股道车辆也纳入编组。

管理员执行：

```text
/st scan 6
/st create demo 6
/st info demo
/st property demo trainnumber T001
/st property demo mode manual
```

坐进车内，然后执行：

```text
/st lang zh
/st drive
/st forward
/p1
/n
/b3
/b7
```

- `P1..P4` 是牵引级位，`B1..B7` 是常用制动，`EB` 是紧急制动。
- `/n` 为主控手柄中立；`/st neutral` 为换向器中立，二者不同。
- 换向受低速/停稳检查，不能把强制反向当作常规制动。
- 每次重新上车都需 `/st drive`，普通驾驶指令不会隐式取得驾驶权。
- 司机下车、掉线、死亡、换车或驾驶席实体失效时撤权、牵引归零并 EB，不会自动把控制交给其他乘客。
- `/st release` 主动释放驾驶权并 EB。之后要自动运行，还需停稳并明确设为 `mode auto`。

### Hotbar

启用 `/st hotbar` 时必须先选中快捷栏 **第 5 格**。启用本身不输入 N，也不解除已有制动；之后切换槽位才输入级位。

| 槽位 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 级位 | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

再次 `/st hotbar` 退出时不要求第 5 格。也可用 `/st cab` 打开图形驾驶台；仍需先 `/st drive`。

## 5. 指令参考

`<...>` 必填，`[...]` 可选。下表采用推荐写法，不穷举历史别名。

### 司机与查询

| 指令 | 用途 |
| --- | --- |
| `/st help [page] [language]`、`/st version [language]` | 帮助与已安装版本 |
| `/st list`、`/st info [train]` | 列表、列车状态、自动牌子阻止原因 |
| `/st scan [radius]` | 查询附近矿车 |
| `/st drive`、`/st release` | 当前列车驾驶权 |
| `/st cab`、`/st hotbar` | 图形驾驶台/快捷栏 |
| `/st forward`、`/st neutral`、`/st backward` | 换向器 |
| `/st p1` 至 `/st p4`、`/st n`、`/st b1` 至 `/st b7`、`/st eb` | 手柄，也有 `/p1`、`/n`、`/b7` 等根命令 |
| `/st horn`、`/st bell` | 鸣笛/铃 |
| `/st lang zh\|en\|fr\|jp` | 个人语言 |
| `/st speedunit kph\|mph\|block/tick` | 显示单位，别名 `/st unit`；不改变管理指令数值单位 |
| `/st balise [info]`、`/st origin [info]`、`/st end [info]` | 附近设施 |
| `/st mileage [train]` | 里程；显式指定列车需管理员权限 |

### STF 管理员

| 指令 | 用途 |
| --- | --- |
| `/st connect [radius]` | 连接附近矿车 |
| `/st create <name> [radius]` | 建立编组 |
| `/st append <train> [radius]` | 追加成员；列控试验建议先用固定编组 |
| `/st unlink` | 解除最近矿车绑定 |
| `/st remove <train>` | 删除列车定义；不是清理 STCS 阻塞的捷径 |
| `/st start <train> [speed]`、`/st stop <train>` | 既有目标速度控制/停车，不是 MA 批准 |
| `/st reverse <train>` | 反转换向器，受停稳检查 |
| `/st speed <train> <speed>`、`/st maxspeed <train> <speed>` | 目标速度/列车上限，数值为 blocks/tick |
| `/st spacing <train> <spacing>` | 编组间距，可能受到全局紧密编组设置限制 |
| `/st property <train> <key> [value]` | 不填值查询，填值设置 |
| `/st tag <train> add\|remove\|list [tag]` | 标签 |
| `/st owner <train> add\|remove\|list [player]` | 所有者元数据 |
| `/st route <train> set\|add\|clear\|list [destination...]` | 目的地列表，不等于完整自动排路 |
| `/st savedtrain list` | 模板列表 |
| `/st savedtrain save <train> <template>` | 保存模板 |
| `/st savedtrain spawn <template> [train]` | 在玩家附近生成模板列车 |
| `/st switch list`、`/st switch scan [radius]` | 道岔列表/已加载区域扫描，默认 32、最大 128 格 |
| `/st switch info`、`/st switch set straight\|diverging` | 最近 8 格内道岔查询/转换请求 |
| `/st switch remove`、`/st switch cleanup` | 删除最近定义/清理无效登记；remove 保留世界方块 |
| `/st balise list`、`/st origin list`、`/st end list` | 基础设施列表 |
| `/st clearkm <line>` | 清除线路里程标定，不清除占用 |
| `/st admin release\|p1..p4\|b1..b7\|n\|eb <train>` | 明确远程控制 |
| `/st syncstatus` | 显示同步诊断 |
| `/st save`、`/st reload` | 保存/重载，reload 前停稳全服列车 |

属性还兼容 `property <train> get <key>` 和 `property <train> set <key> <value>`，教程统一使用较短形式。

### STCS

| 指令 | 权限 / 含义 |
| --- | --- |
| `/stcs help [page] [language]`、`/stcs version [language]` | 帮助与版本 |
| `/stcs inspect` | `stcs.use`，附近 3 格设施 |
| `/stcs status` | `stcs.use`，节点/边/图版本，不是 ATP 模式 |
| `/stcs ma demand` | `stcs.ma` + 当前司机，申请影子 MA |
| `/stcs ma release` | `stcs.ma` + 当前司机，释放车前预约，不清除车身占用 |
| `/stcs ma status` | `stcs.admin`，MA 与阻塞诊断 |
| `/stcs admin status` | `stcs.admin`，所乘列车保护模式 |
| `/stcs admin isolate true\|false` | 列控切除 |
| `/stcs admin bypass true\|false` | 监督旁路 |
| `/stcs admin shadow true\|false` | 影子测试 |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`，保留占用与成员观测诊断 |
| `/stcs rebuild` | `stcs.admin`，重建已登记设施的图 |
| `/stcs export` | `stcs.admin`，导出当前图，不等于重新扫描 |
| `/stcs switch info` | `stcs.use`，附近 8 格道岔查询 |
| `/stcs switch change` | `stcs.admin`，附近 8 格道岔转换 |

现在玩家申请命令是 **`demand`，不是 `request`**。内部事件仍可叫 `MA_REQUESTED`，不代表旧玩家指令有效。`/sta` 和 `/skypcc` 主要提供帮助/版本，不另设完整驾驶命令体系。

## 6. 车型与属性

### 默认车型

服务器在 `plugins/SkyTrainFolia/config.yml` 选择：

```yaml
settings:
  default-vehicle-profile: minecraft-comfort
  server-speed-limit-kmh: 420.0
```

车型文件在 `plugins/SkyTrainFolia/vehicles/<id>.yml`：

| ID | 用途 |
| --- | --- |
| `crh380b` | CRH380B 参考车型 |
| `cr400bf` | CR400BF 参考车型 |
| `keikyu-n1000` | 京急新 1000 参考车型 |
| `minecraft-comfort` | 面向游戏驾驶体验的虚构车型 |

现实车型是游戏近似与调参参考，不是认证性能数据。当前使用**服务器统一选定的车型**，没有 `/st property <train> profile ...` 这样的逐车切换指令。

车型定义的是一整列参考车，不随 Minecraft 车厢数量自动改变质量/性能。实际编组长度仍会影响占用、咽喉出口容量与站停，不能把动力学解耦理解成几何长度不重要。

| 车型字段 | 作用 |
| --- | --- |
| `physics-mode` | 动力学模式，当前预置使用 `force` |
| `mass-tonnes` | 完整参考列车质量 |
| `max-speed-kmh` | 车型速度上限，不保证动力能达到此速度 |
| `traction.acceleration-mps2.p1` 至 `p4` | 低速各级牵引基础加速度，扣阻力前 |
| `traction.base-speed-kmh` | 牵引曲线基速 |
| `traction.field-weakening-speed-kmh`、`minimum-ratio` | 高速牵引衰减与最小比例 |
| `brake.deceleration-mps2.b1` 至 `b7` | 各级常用制动基础减速度 |
| `brake.emergency-mps2` | EB 基础减速度 |
| `resistance.rolling-mps2` | 滚动阻力 |
| `resistance.air-mps2-at-100-kmh` | 100 km/h 空阻基准，按速度平方变化 |
| `resistance.grade-mps2` | 坡度加速度比例 |
| `control.direction-change-speed-kmh` | 换向允许的低速门槛 |
| `automatic.*` | 既有目标速度控制参数，不是全部 station 级位逻辑 |

旧 `drive-power-acceleration-p1` 等散落配置不是当前车型文件的编辑入口。修改前备份；停止所有列车再重载，或停服修改后重启。

### 上限为何仍是 130 km/h

车型上限、服务器上限与列车持久化 `maxspeed` 都可能限速。换默认车型不会抹掉旧列车自己的限制。

在 1 block = 1 m、20 TPS 名义换算下，`km/h = blocks/tick × 72`：

```text
/st property demo maxspeed
/st maxspeed demo 5
```

这里 5 是 360 km/h，不是 5 km/h，也不是 `5kmh` 字符串。更低的车型/服务器上限仍生效；高速牵引与阻力平衡也可能让车到不了上限。个人显示单位不改变管理指令的解析。

### 车号与车次

```text
/st property demo name test01
/st property test01 displayname Test Train
/st property test01 trainnumber G001
/st property test01 trainnumber
/st property test01 trainnumber clear
/st property test01 mode auto
/st property test01 pushable true
```

- `name` 是管理列车名/列车号；改名后命令使用新名称。
- `trainnumber` 是独立车次，可保留前导零，最多 32 字符，不允许控制字符或 `|`，不强制全服唯一。`clear` 或 `-` 清除。
- `displayname` 是显示名称，不能代替内部 UUID。
- 常用属性还包括 `destination`、`collision`、`playersenter`、`playersexit`、`pickupitems`、`invincible`、`allowplayertake`、`requirepoweredcart`、`sound`、`keepchunksloaded`、`gravity`、`friction`、`waitticks`、`speed`、`maxspeed`、`spacing`。
- `pushable=true` 不是唯一推动条件；驾驶权、未 release 的手动接管及运行状态仍可能阻止推动。查 `/st info` 的自动处理阻止原因。
- owner/tag/route 字段存在，不表示已实现完整调度、全网寻路或所有者权限隔离。

## 7. 线路与道岔建设

### 物理连接与线路归属

RailGraph 包含节点、端口、合法通路、有向边和轨道几何。线路名/里程是上层标注，不能因“经过同一道岔”任意传播。

- 正线使用唯一线路名，例如 `test_up`、`test_down`，沿线 balise 拼写一致。
- Origin/End 是线路标注边界，不必是物理轨道尽头；外侧确有轨道时仍应有物理边。
- 无名 balise 标定侧线节点，不应把正线归属继续传播到整条侧线。
- 多条通路都连接同名标记时，在正线支路增设同名 balise 消除歧义，不依靠当前道岔状态猜永久线路归属。
- 里程未知就显示未知，不能用最近节点数字假装可靠里程。

### STCS 四行牌子

放在能明确关联目标轨道的位置，避免同时靠近两条平行轨道造成歧义。

起点：

```text
[STCS]
origin
test_up
right
```

正线应答器：

```text
[STCS]
balise
test_up
0010
```

终点：

```text
[STCS]
end
test_up
right
```

侧线应答器，第三行留空：

```text
[STCS]
balise

5010
```

Origin 第四行是线路正向/内侧；End 第四行描述边界外侧方向，线路内侧相反。相对方向按牌面解释，不能只按 PCC 屏幕左右判断。`signal` 可作为登记节点，但不代表已实现信号机 ATP。

### STF 道岔

STCS 导入 STF 已登记道岔，不把任意弯轨直接视为可控道岔。登记支持 `[stf]`、`[+stf]`、`[SkyTrain]`、`[+SkyTrain]`，大小写不敏感。自动动作的红石使能与设施是否存在是不同问题，不能靠补 `+` 修复所有缺边。

既有前装布局示例：

```text
[SkyTrain]
switch
fl
S01
```

第四行是道岔名，真正身份仍是 UUID。STF `switch` 不是 TC `switcher`，布局参数不能直接混用。

墙牌支持既有侧贴/下装布局；`fl/fr` 前装要求尖轨在牌后承载方块上方两格。天花板悬挂牌支持“牌子 → 上方承载方块 → 上方尖轨”，朝向应为东南西北，不能斜放。尖轨使用可转换的普通铁轨。绑定拉杆随道岔转换并通知周围红石方块，复杂布线仍需实测。

建设完成后：

```text
/st switch scan 32
/st switch list
/stcs inspect
/stcs rebuild
/stcs status
/stcs export
```

Rebuild 基于已登记设施，不是无限扫描整个世界。WorldEdit 后先确认登记；默认仅扫描已加载区块，未完成部分保留已有拓扑，从未扫描过的边不会凭空补出。

STCS 扫描配置：

```yaml
scan:
  max-distance-meters: 256.0
  blocks-per-meter: 1.0
  marker-rail-search-radius: 3.0
  only-loaded-chunks: true
graph:
  file: railgraph.json
  pretty-print: true
```

扫描距离不是 MA 前视距离。长区间需合理补 balise 或增加扫描距离。STF 与 STCS 的 blocks-per-meter 应一致，通常保持 1。

## 8. 自动牌子与站停

Station / spawn / destroy 的用户接口参考 TC，轨道关联采用轨道下方牌柱与附着关系，而不是任意邻近球形范围。**本实现支持的格式不等于所有 TC 表达式、远程牌与扩展功能完全兼容。**

手动驾驶列车不会被 station/destroy 接管。先停稳，由司机 `/st release`，管理员明确 `/st property <train> mode auto`。ISOLATED、RECOVERING 和未释放手动接管仍会阻止自动处理。

常用牌头：`[stf]` 受红石使能，`[+stf]` 持续使能，`[!stf]` 反向使能，`[-stf]` 禁用。解析器也有上升/下降沿形式，实际激活还取决于对应动作。

### Station

```text
[+stf]
station
5
continue 40kmh
```

| 行 | 定义 |
| --- | --- |
| 1 | 牌头和使能 |
| 2 | station，可附支持的发车距离/时间/加速度选项与停车偏移 |
| 3 | 等待时间，纯数字为秒，也支持 `5s`、`100t`、`00:05` |
| 4 | 方向和速度，例如 `continue 40kmh`、`reverse 0.4`；无单位速度为 blocks/tick |

满足 auto、已释放、可推动等条件后，可将车推入 station 扣停并发车。长距离预告依赖 STA/STCS 的线路与车站查询；在牌上停住不等于提前预告成功。

伪自动驾驶使用车型 P/B 级位：起步较高牵引，目标速度附近 N/P1 调整，进站制动逐步减轻，停稳后 B7 停放。不是直接瞬移速度，也不保证任意车型/站距下均可精确停车。

STF `settings.station-look-ahead-blocks` 默认 8192；`station-launch-speed` 默认 0.4 blocks/tick，最后对标还受 docking 参数影响。这些与 STCS MA 前视不同。先用低速、长站距试验线验收。

### Spawn 与 Destroy

```text
[stf]
spawn 0.0
mmm

```

用受控红石触发三节普通矿车生成。第二行支持 `spawn [velocity] [interval]`，周期可用 `00:30`；第三、四行拼接为生成模式。支持的基本矿车符号有 `m` 普通、`s` 箱子、`p` 动力、`h` 漏斗、`t` TNT，以及实现支持的模板模式。首次试验不要使用 TNT。

模板用于自动生成时需明确以 auto 保存；不要在无防护线路连续周期发车。

```text
[+stf]
destroy


```

Destroy 是实际销毁动作，在备份后的独立测试线上验证。手动控制列车不应被它删除。先验收 station，再单独试 spawn/destroy，避免多种状态叠加。

## 9. 影子 MA 与模式

### 概念和流程

| 名词 | 当前含义 |
| --- | --- |
| MA / Movement Authority | 沿合法有向路径分配的影子许可 |
| EoA / End of Authority | 当前许可终点，不一定是线路终点 |
| Credit | 到 EoA 的剩余路径距离，不是空间直线距离 |
| Occupancy | 列车成员观测推导/保留的轨道占用 |
| Reservation | 车前影子预约，不是认证进路锁闭 |
| RBC link | 影子信息通道状态，不证明存在实际无线 RBC 或有效 ATP |

1. 确认图、道岔、定位和实际试验线路正确。
2. 司机上车 `/st drive`，停稳设置方向，使用允许申请的模式。
3. `/stcs ma demand`，同时检查分配结果和原因，不能只看“已申请”。
4. 司机 BossBar 显示剩余 MA，HMI 显示 EoA 线路/里程，PCC 显示预约区间。
5. 手动控制速度与停车；释放车前预约时 `/stcs ma release`。

无司机列车不主动取得新 MA；有司机的停车状态与无司机不同。MA release 不清除车身占用，也不是停车指令。

### 模式状态机

| 状态 | MA / 通道 | 实际控车 |
| --- | --- | --- |
| SHADOW | 允许影子申请/识别 | 不执行 MA/速度 ATP |
| BYPASS | 保持通道，可保持/申请影子 MA | 监督旁路，不是通信切除 |
| ISOLATED | 拒绝新申请，车载列控切除 | 保留只读里程、STA 遥测，禁止自动牌子 |
| RECOVERING | 不授予新的有效车载 MA | 保持制动、拒绝牵引，等待明确后续选择 |

模式随列车数据持久化。任一有效开关 `false` 进入 RECOVERING；目标 `true` 需要先处于 RECOVERING、全列观测新鲜且停稳。不能行驶中随意跨模式。

管理员坐在目标列车内，例如从影子转旁路：

```text
/stcs admin shadow false
/stcs admin status
```

确认全列停稳后：

```text
/stcs admin bypass true
```

返回影子测试时先 `bypass false`，停稳后 `shadow true`。改变模式会断牵引/制动，不自动发车。切除后保留的地面预约/占用证据，不等于车载仍有可用 MA，也不能作为持续延长授权的依据。

### 三个距离

STCS `config.yml`：

```yaml
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  margin-meters: 2.0
```

| 配置 | 含义 |
| --- | --- |
| `look-ahead-meters` | 前方通路搜索上限，不把看到的全部轨道预约 |
| `lock-distance-meters` | 允许影子 MA 跨越道岔的接近距离；更远时留在道岔前，不是实体锁闭证明 |
| `max-authority-distance-meters` | 实际 Credit 上限，同时不超过前视范围 |
| `margin-meters` | 冲突/障碍前余量，不是完整制动距离 |

旧 `ma.horizon-meters` 可为缺少的新距离键提供兼容默认值；新配置应明确填写三项。STF 道岔预开通 `switch-approach-distance`、station 前视和 BossBar 显示量程也各自独立。

### 跟车、平行进路与咽喉

当前空间 MA 使用物理轨道单元与边内区间，而不是简单将整条应答器间边交给一列车。正反向边共享同一物理位置资源。

- 同向跟车按前方占用/预约的空间边界截断，不应始终退回前一个 balise。
- 平行进路没有共享资源和冲突时应可同时存在，不能仅因同属咽喉就全区封锁。
- 渡线与道岔沿合法通路预约，不把未走的另一个出口自动算成车身占用。
- 进入咽喉前检查出口能容纳整列，否则在入口前等待。
- 图/道岔未知、成员缺失、冻结与旧账本记录仍可能阻塞；不能为了画面“看起来通”跳过检查。

车身映射和异步一致性仍存在保守处理，不是完整列车完整性与扫掠包络证明。算法沿路径每四分之一格采样障碍，但分配的是物理轨道格资源，属于格尺度影子分配，不是认证的连续移动闭塞 ATP。预约止于授予区间；共用的道岔物理格可能使未选分支的短端点着色，不等于预约整条分支。

资源标识使用 `cell@world:x:y:z`。旧整边占用记录会保守阻塞，直到完整、新鲜的观测替换它；卸载、重启、成员缺失或图版本不一致均不自动清除证据。带旧记录的离线列车仍可能阻塞较大范围。回退版本时应使用配套备份，不直接沿用新版账本。

## 10. SkyPCC 网页

默认配置：

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  control-enabled: false
  control-token: ''
  update-mode: auto
  poll-interval-millis: 1000
  sse-keepalive-seconds: 15
```

服务器本机访问 `http://127.0.0.1:8765/`。远程电脑上的 127.0.0.1 指远程电脑自己，不是游戏服务器。远程调度控制建议 SSH 本地隧道；不要简单绑定公网就当成安全发布。

### 界面功能

- 中英法日、深浅色主题、内置日夜 Logo，偏好保存在浏览器。
- 线路筛选、缩放、适配视图和文字大小。
- 列表包含等待定位列车；保留记录不等于实时定位。
- 地图标签为 `<车次号> | <列车号> | <速度> km/h`，无车次使用占位。
- 列车 Inspector 可开启/取消镜头跟随，显示模式、MA/EoA、原因、方向、换向器、手柄、司机、里程、轨道边与数据年龄。
- 道岔编号黄底粗体黑字，方向箭头单独着色：直向紫色、侧向橘黄色。
- 点击应答器、道岔或边打开设施 Inspector。主线上有确认里程的设备显示里程，无里程设备显示相邻图节点/端口距离，不凭空推导侧线里程。
- 道岔显示基于收到的图/状态快照，不是网页重新验证世界的证明。
- 占用、冻结/不确定、影子预约与未分配分色。灰色“未分配”不等于已证明空闲。
- Operations log 支持等级筛选、收起与向上展开更多。

### 道岔操作

开启 `control-enabled` 并设置至少 32 字符的私密随机 token，完整重启。在网页操作入口输入 token，不放进 URL、截图或公开日志。

点击道岔查看信息，再从转换入口确认。请求包含图版本、道岔身份、预期/目标状态和位置。STCS 局部检查后由 STF 执行。

- 不因远处无关列车一律拒绝，但本地占用、预约冲突、不确定信息、状态/位置不匹配仍可阻止转换。
- 未加载时进入 PENDING，异步加载并在所属区域重验后执行；PENDING 不是成功，不能据此放行。
- 仅 token 正确还不够，写操作同时检查回环连接与同源。
- 公开只读网页也应另设访问控制；控制凭据与普通观测访问不能混为一谈。

### 事件栏

当前事件包括驾驶权取得/释放/失能、道岔转换、疑似挤岔、EB 进入、MA 申请/释放、ATP 模式切换。模式事件标明操作者及前后状态，管理员操作者不一定是当前司机。

STA 默认保留本次会话最近 500 条，不是永久审计库。正常遥测和每次平滑 MA 延长不会都刷事件；“疑似挤岔”也不是已经完整证明的故障。

## 11. 声音与 HMI

MA BossBar 只给有效司机显示，不给其他乘客显示。STF `settings.cab-ma-bar-range-meters` 默认 300 m，仅控制血条比例，文本仍为实际距离。右侧 ATP 限速目前是未实现功能的占位，不是已经计算好的曲线。

### 可配置 MA 声音

STF `config.yml` 顶层 `ma-sounds`，不是 `settings.ma-sounds`：

```yaml
ma-sounds:
  enabled: true
  granted:
    enabled: true
    sound: minecraft:block.anvil.land
    category: MASTER
    volume: 1.0
    pitch: 2.0
    count: 2
    interval-ticks: 5
```

`changed`、`released`、`shrinking`、`low` 都可配置同样字段。示例只展示局部，修改现有节，不要重复建立同名 YAML 键。

| 提示 | 默认声音 | 行为 |
| --- | --- | --- |
| granted | `minecraft:block.anvil.land` | 获得申请的许可，pitch 2，两声 |
| changed | `minecraft:block.anvil.land` | 明显跳变，pitch 2，一声 |
| released | `minecraft:block.iron_trapdoor.close` | MA 释放 |
| shrinking | `minecraft:entity.experience_orb.pickup` | 运行中剩余滑动 MA 开始收缩 |
| low | `minecraft:block.note_block.pling` | 低余量警告 |

声音 ID 使用 Java 版 `namespace:path`，省略命名空间默认为 minecraft。自定义声音需客户端资源包支持。范围：volume 0..4、pitch 0.5..2、count 1..5、interval-ticks 1..200。

STCS 控制触发条件：

```yaml
ma:
  sound:
    enabled: true
    jump-threshold-meters: 20.0
    cooldown-ms: 1500
    low-remaining-meters: 50.0
```

低余量设 0 关闭阈值提示，恢复到阈值上方一定余量后才重置，避免反复响。正常随车平滑向前延长不应重复播放跳变音。延迟提示仍检查驾驶权，不能向旧司机持续播报。

### 轮轨与制动

轮轨声随速度改变音量/音高，默认 10 km/h 以下静音，120 km/h 达到配置曲线上限，对应 `settings.trackside-running-sound-*`。

增加制动或从牵引/N 进入制动有施加音，完全退出制动有缓解音；减小但未退出制动不逐档播放缓解音。对应 `settings.brake-sound-*`。当前未加入电机声。

修改后先停稳所有列车，再 `/st reload`。这是完整数据重载，不是局部声音热更新。

## 12. STA 契约与报文

STA 首先是同一服务器 JVM 内的 Java 服务接口，不是自动开放给 Python 的 TCP/WebSocket RBC。PCC 提供既有 HTTP/SSE 观测与受限道岔写入入口。

| 数据/接口 | 主要生产方 | 主要消费方 | 说明 |
| --- | --- | --- | --- |
| v2 TELEMETRY_REPORT / 1001 | STF | STCS、订阅者 | 物理遥测 |
| v2 TRACK_REPORT / 1002 | STCS | PCC、订阅者 | 与原遥测身份绑定的线路/图定位 |
| v2 TRAIN_REMOVED / 1003 | 来源清理流程 | 注册/订阅者 | 不等于清除安全占用账本 |
| v2 RailNetworkService | STCS | STF 等 | 图、导航、边位置、车站前视 |
| v3 ConsistObservation | STF | STCS | 成员观测与生命周期 |
| v3 RailwayEvent | STF、STCS | PCC、订阅者 | 运行/操作事件 |
| v4 DriverDeskService | STF | STCS | 司机、驾驶权、ATP 模式 |
| v4 ShadowAuthorityService | STCS | STF、PCC | 非可执行 MA/EoA 与区段状态 |
| v4 SwitchControl | PCC 等发请求，STCS 检查、STF 执行 | 请求方 | 状态/版本/坐标检查与 PENDING |

“有几种报文”要区分层次：v2 通用消息 kind 有三种，整套 API 还有独立服务、快照、事件和命令契约，不能都计入那三种。

v2 消息头包含 version、kind、source、sessionId、sequence、emittedAt、trainId。质量值包括 VALID、UNLOCATED、STALE、EXPIRED、GRAPH_CHANGED、SOURCE_UNAVAILABLE、SCALE_MISMATCH，消费方不能忽略质量、会话和序号只取坐标。

v4 影子快照带 `simulationOnly=true`、`executable=false`。许可含路径、EoA 边/偏移、剩余距离及来源观测身份；Section 可带 `fromMeters/toMeters`，同一边可以有多个区间。消费端不能将一个区间出现理解成整边占用。

领车中心不是已证明的最前端，名义编组长度不是完整性证明；名义 20 TPS 速度也不是卡顿时的墙钟速度。当前契约参考 ETCS 思想，**不是 SUBSET-026 报文编码或互操作实现**。

### PCC HTTP

| 接口 | 内容 |
| --- | --- |
| `GET /api/v1/graph` | RailGraph |
| `GET /api/v1/trains` | 列车显示快照 |
| `GET /api/v2/messages` | 遥测消息快照 |
| `GET /api/v3/railway-events` | 运行事件 |
| `GET /api/v4/shadow-ma` | 影子许可与区段 |
| `GET /api/v1/config` | 网页公开配置，不提供私密 token |
| `GET /api/v1/events` | SSE 更新流 |
| `POST /api/v4/switch` | 经鉴权的道岔操作 |

接口路径版本与 JAR 版本不是同一编号；不能把显示快照重放成正式行车许可。这里是接口概览，不是完整 SDK 或生成的字段规范。外部客户端在发送命令前，必须核验部署版本的实际字段和契约；特别是区段端点为空时保留旧整边语义，而显式端点限定空间区间。

## 13. 数据与故障排查

### 重要文件

以下相对于服务器根目录：

| 路径 | 内容 |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`、`vehicles/` | 全局配置、声音与车型 |
| `plugins/SkyTrainFolia/trains.yml` | 编组、属性、保护模式 |
| `plugins/SkyTrainFolia/savedtrains.yml` | 模板 |
| `plugins/SkyTrainFolia/switches.yml` | 道岔登记 |
| `plugins/SkyTrainFolia/infrastructure.yml` | STF 基础设施/标定 |
| `plugins/SkyTrainFolia/stations.yml`、`automatic-signs.yml` | 车站/自动牌子记录 |
| `plugins/STCS/markers.yml` | STCS 设施登记 |
| `plugins/STCS/railgraph.json` | 图快照，不是占用账本 |
| `plugins/STCS/occupancy-ledger.json` | 保留占用证据，不可为强行发 MA 随意删除 |
| `plugins/SkyPCC/config.yml` | 网页与控制凭据 |

当前图仍是一个逻辑图/JSON 导出，未实现“一条主线一个文件”的生产分片。跨线路物理边不能随意按线路名切断；离线分析导出副本，不编辑运行中图/账本。

### 常见现象

| 现象 | 优先检查 |
| --- | --- |
| info 是 auto，station 无效 | 是否有司机、是否显式 release、保护模式、红石使能、牌柱关联及阻止原因 |
| 已申请但 HMI 未下发 | `/stcs ma status` 与 PCC MA reason；申请接受不等于分配成功 |
| SWITCH_UNKNOWN | 尖轨区块/区域是否完成实际状态校验、设施登记、位置匹配；图节点存在不证明实体已知 |
| FLEET_UNCERTAIN / 保留车辆未知 | occupancy 与 UUID；PCC 没有可定位车不代表账本无记录，不能直接删账本 |
| NO_EXIT_CAPACITY | 咽喉出口整列容量、MA 上限、断图/错误路径归属 |
| 平行进路互锁或多占一个分支 | 实际路径、空间区间、旧保留资源、新鲜全列观测；保留诊断和图 |
| 里程丢失/正线传播到侧线 | Origin/End 方向、同名 balise、无名侧线边界、多通路歧义，加载后 rebuild |
| PCC INVALID_REQUEST | 请求格式/版本/预期状态/坐标及前后端版本；不等于 token 错 |
| PCC 控制不可用 | 开关、token 长度、回环、同源、依赖插件与具体拒绝原因 |
| PENDING 未结束 | 异步加载、实体重验、服务器日志，不能当作成功 |
| 高速车厢异常 | 服务端适配版本、区域迁移、其他插件接管/销毁实体，不是单改最高速度即可解决 |

卸载不是车尾出清；重启保留不是新鲜定位。占用账本质量中的 RECOVERING 与司机端 ATP 模式 RECOVERING 也不是同一个状态机。

反馈时提供四插件/服务端版本、时间、列车名和 UUID、设备编号、步骤、日志、导出图及必要占用诊断。分享配置前去除 token 等私密信息。

## 14. 验收与后续目标

### 更新后的最小测试清单

1. 四插件加载与版本、帮助正常，无配置解析错误。
2. 每次上车需 drive；乘客不自动接管；司机下车/掉线 EB；重连不恢复旧牵引。
3. 模式 false 进入恢复，恢复不能牵引，停稳才允许目标 true，重启状态保留。
4. 图覆盖直线、侧线、渡线、背对背 Origin、End 外连接、悬挂牌；线路归属不乱传播。
5. 运行/停车/卸载/重启定位，冻结与保留证据不凭空消失。
6. 无司机、新申请/释放、同向跟车、对向冲突、平行进路、咽喉容量和车尾出清。
7. 本地/PCC 道岔转换、冲突拒绝、未加载 PENDING、实际轨形与拉杆红石一致。
8. 司机专属 BossBar、EoA 里程、PCC 区间颜色、设施 Inspector、日志展开、四语言/日夜主题。
9. 授予两声、跳变一声、平滑延长不重复、低余量/收缩/释放提示；撤权不继续向旧司机播放。
10. Manual 不被自动牌接管；release + auto 后 station 正常，独立测试 spawn/destroy。

离线 Python 测试台可以验证图、方向、占用、预约与 EoA 场景，但不覆盖 Bukkit 实体生命周期、Folia 调度、网络与实际制动性能。上述也是游戏回归，不是铁路安全认证。

### 阶段定位

M0 模式/契约与 M1 观测/保留已有实现并经历玩家联测，M2 已进入在线影子 MA 与空间资源改进。历史“通过”不替代新版本回归，也不意味着 ATP 准入条件已满足。

下一步可以加入**车载影子速度曲线**：读取 MA/EoA 与车型制动参数，仅显示/记录目标速度和曲线，不施加制动。验证后再讨论 ATP 介入。

真正 ATP 前仍需完整列车前后包络与一致性、可靠许可身份/确认/撤销、失联/冻结/旁路策略、线路限速与制动模型、资源释放证明、故障注入及版本回归。目前不把这些写成已完成能力。

## 15. 开发与构建

### 源码布局

```text
SkyRail Suite/
  SkyTrainFolia/       编组、运动、驾驶、牌子与 HMI
  STCS/               基础设施、图、定位、占用与影子 MA
    tools/            图查看器与离线 Python 测试台
  STA/                插件间契约与服务
  SkyPCC/             网页服务、前端与测试
  shared/             编译进各插件的共用代码，不是第五个插件
  doc/                补充开发记录
  README.md           精简项目首页
  README.zh.md        中文完整手册
  README.en.md        英文完整手册
  README.nl.md        荷兰语完整手册
  LICENSE             代码 MIT 许可证
  ASSET-LICENSE.md    图片授权范围说明
  logo.png            项目标志
  build.ps1           构建与 Java 回归入口
  package-source.ps1  源码归档入口
```

每个插件的代码、默认配置/资源、测试分别位于 `src/main/java`、`src/main/resources`、`src/test/java`。实际版本以各模块 `plugin.yml` 或游戏内 version 为准。

### 运动与 Folia 边界

STF 使用轨道坐标与编组间距处理运动，而不是只把领车速度复制给后车。位置修正、乘客平滑与显示同步是不同环节；这不意味着可以无限提速，过弯、坡道、区域迁移和实体销毁仍需实服测试。

实体和方块操作必须遵守 Folia 的实体/区域所有权调度。异步图与许可计算使用快照，不得据此跨线程任意读取 Bukkit 对象。区块可用、图中已知和道岔实体验证是不同状态；区块已加载不等于此前未知的道岔已经校验。

Minecraft 更新可能改变实体内部结构、数据包/显示适配和调度假设。抽象层能减少改动范围，但不能做到免维护，仍需针对新服务端回归。

### 与 TrainCarts 的比较边界

- 当前自动牌兼容重点是 station、spawn、destroy，使用 STF/SkyTrain 牌头和本文描述的解析/激活规则。
- 轨道关联采用牌柱与附着关系，不代表支持 TC 的所有牌子、表达式或扩展。
- STF switch 描述显式几何、端口和执行器，不是 TC switcher 的选路表达式。
- 熟悉的 station 用户界面不意味着底层控制器相同：此 MVP 使用车型级位，并另有最终停靠处理。
- destination 元数据、车站前视、资源预约和可执行 MA 是不同层次，不能由其中一项推断其余都已完成。

### 构建四个插件

需要 JDK 25，以及单独准备的匹配 Shiroha/Folia 26.2 服务端依赖。服务端及第三方库不随源码分发。安装已构建的 JAR 不需要自行编译。

在仓库根目录运行 PowerShell：

```powershell
./build.ps1 -ServerRoot '/path/to/prepared-server' -JavaHome '/path/to/jdk-25'
```

也可设置 `JAVA_HOME` 后省略 `-JavaHome`。脚本默认读取服务端 `versions/26.2/shiroha-26.2.jar` 与 `libraries/`；可用 `-ServerJarRelativePath` 调整相对位置，但路径兼容不代表不同服务端实现兼容。

构建顺序为 STA、STF、STCS、PCC，不依赖旧版套件 JAR。脚本执行 Java 回归，输出进入已忽略的 `artifacts/`，中间文件进入 `target/`；不会启动、部署或修改服务器。根许可证与图片范围声明进入四个 JAR，TC 原声明继续保留在 STF 中。

### 网页与离线测试

```powershell
cd SkyPCC
npm install
npm test
npx playwright install chromium
npm run test:browser
```

`test:browser` 为当前界面回归；`test:browser:legacy` 保留旧 UI 场景，不能假定全部通过。也可设置环境变量 `PLAYWRIGHT_CHANNEL=msedge` 使用本机 Edge。输出写入模块 `target/`。

从仓库根目录执行 Python 测试与图形测试台：

```powershell
python -m unittest discover -s STCS/tools/testbench -p 'test_*.py'
python STCS/tools/testbench/railgraph_testbench.py
```

使用 Python 标准库，图形界面需要 Tkinter。测试台不会连接服务器，通过“打开轨道图”载入导出副本。三个可选实图回归默认跳过，可用 `STCS_TEST_GRAPH` 指向具有对应场景的副本。不要提交生产服图、场景或占用账本。

### 源码与二进制发布

```powershell
./package-source.ps1
```

生成 `dist/SkyRail-Suite-source-<timestamp>.zip`，不包含服务器、JAR、依赖库、缓存或构建输出。GitHub 仓库保存源码，Releases 附件保存匹配提交的四个 JAR 与 `SHA256.json`。当前影子阶段应标记为预发布，不能作为已完成 ATP 的稳定版宣传。

自动测试覆盖契约、状态转换、空间冲突/保留和界面行为，但不能替代司机生命周期、Folia 所有权、红石、区块加载及真实运动的实服验收。文档更新不是新的运行时验证。

本手册已包含安装、权限、指令、配置、教程、接口边界与已知限制；语言切换是可选入口，不要求依赖其他 README 或历史专题文档。

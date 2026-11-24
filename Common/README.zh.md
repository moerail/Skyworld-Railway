# SkyRail Suite — 中文详细手册

## **[2026-11-24] 新增／修正：PCC 2.4.1 地图显示**

~~选中轨道或设备时使用浏览器原生焦点外框。~~ 现在高亮沿实际轨道路径或设备圆环显示，采用固定屏幕线宽，缩放地图不会把外框放大。鼠标选择及键盘焦点都保留可见反馈。

文字 A− / A+ 控件旁新增 **H− / H+**（50%–250%）、百分比复位按钮及颜色选择。点击百分比恢复100%；大小、颜色保存在当前浏览器，存储不可用时仍能即时调整。

工务设备下新增 **Balise（应答器）、END、ORIGIN、道岔** 四个子选项。总开关全选或全关；可单独选择类型，部分选中时总开关显示半选。设备图标、名称及道岔方向箭头同步隐藏；轨道底图和SAL图层保持独立。

这些选项只改变显示，不修改RailGraph、设备、线路、MA或占用。刷新旧页面时使用 Ctrl+F5。


## **[新增／修改 · 2026-11-24] PCC统一地图与线路编辑**

在同一张轨道图上勾选工务设备、SAL设备、运营线路与实时行车。点击「进入运营线路编辑模式」，输入转岔使用的Token；选择线路后点「编辑所选线路」，或选择至少一个已有车辆段创建草稿。

先登记depot再建线。点选应答器、道岔、图边和归属正确的station，调整访问顺序、停站秒数与折返；不会改写实体牌子。草稿保存、验证、发布分别执行，既有任务固定原发布版。

游戏与PCC并发编辑会同时冻结双方，保存/验证/发布/删除均拒绝；重启不解冻。人工选择提案或放弃双方修改后仍须另行保存发布。游戏编辑结束请 `/sal route close <线路>`，发布成功会关闭游戏会话。

`/sal route create Line1 maintown` · `/sal route depot Line1 maintown,otherdepot` · `/sal route resolve Line1 DISCARD`

[PCC统一地图与线路编辑](doc/PCC-ROUTE-EDITING.md)


[中文详细手册](README.zh.md) · [Detailed English manual](README.en.md) · [Manuel détaillé français](README.fr.md) · [Uitgebreide Nederlandse handleiding](README.nl.md) · [日本語詳細マニュアル](README.ja.md) · [Подробное руководство на русском](README.ru.md) · [文語體鐵道運轉詳細規程](README.ja.classical.md)

本分发版日期为2026-11-24。以原详细Manuel为基础整合本轮最终代码，保留未变更的驾驶、车型、工务和声音说明。粗体标记表示新版新增、修改、删除或查询入口变动；删除线保留已废弃的旧用法。后附旧手册只供追溯。

## 破坏性更新必须先读

- STF旧自动起停、巡航、station/spawn/destroy/property牌执行链和注册轮询器已移除，不能通过配置重新启用。原牌子与旧文件不会自动删除，但不再执行。
- ~~`/st property demo mode auto` / `mode automatic`~~：旧伪ATO入口已删除。自动运行必须交给SAL；保留手动驾驶、STF执行器和工务设备。
- ~~只执行 `/st release` 就会被自动接管~~：司机必须先在库位确认SAL纳管，再显式释放同一驾驶权。
- ~~默认影子MA，ATP不动作~~：新驾驶会话默认请求FS并启用可执行保护，SAL同样受MA/ATP监督；有效许可获批前仍保持制动。显式影子测试通道保留。
- ~~用 `scan.only-loaded-chunks: false` 让建图加载区块~~：拓扑维护只扫描已加载区域，旧值会迁移为true。不能把卸载当作断轨或出清。
- 五个插件按下表整套替换，不能混用旧STA。保留世界、编组、RailGraph、SAL状态、已确认销毁回执及所有占用账本。回退恢复同一时点备份。

**[新增 · 2026-11-21]** 本轮交付包括SAL固定运营线路和出入库、双向驾驶权移交、RailGraph局部更新、正式可执行MA/ATP显示、PCC独立运营地图，以及新的指令主题。SAL蓝色前缀为 `[SkyATO-Lite]`，STF灰色，STCS黄色；旧默认前缀自动迁移，自定义前缀保留。版本页列出SAL，并纠正过时的“ATP不动作”提示。

| 文件 / 组件 | 版本 | 职责 |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | 类型化契约、遥测、驾驶/调度服务；不是独立服务器核心 |
| SkyTrainFolia / STF | **5.0.2** | 编组、实体物理、区块票据、手动驾驶、TIMS、ATP及动作执行 |
| STCS | **4.3.5** | 工务设备、RailGraph、定位、占用、预约、MA和道岔排路仲裁 |
| SkyATO-Lite / SAL | **0.5.0** | SAL运营计划、叫车、停站、Title、回库与移交；不直接操纵实体 |
| SkyPCC / PCC | **2.4.1** | 网页工务地图、运营地图、车辆段列表及受控调度操作 |

## 目录

1. [目标、组件与本轮产物](#section-1)
2. [安装、升级与回退](#section-2)
3. [权限与语言](#section-3)
4. [第一列手动列车](#section-4)
5. [指令参考](#section-5)
6. [车型与属性](#section-6)
7. [线路与道岔建设](#section-7)
8. [SAL运营线路、牌子与驾驶权移交](#section-8)
9. [可执行MA、默认FS与ATP](#section-9)
10. [SkyPCC工务与运营地图](#section-10)
11. [声音与HMI](#section-11)
12. [STA契约与HTTP接口](#section-12)
13. [数据与故障排查](#section-13)
14. [验收清单与已知边界](#section-14)
15. [开发、源码与分发](#section-15)

<a id="section-1"></a>

## 1. 目标、组件与本轮产物

**[修改 · 2026-11-21]** 运营线路是SAL的有序停站/路径计划，工务线路是STCS的线路归属与里程，二者不共用语义。SAL只消费RailGraph；STCS负责许可与排路，经STA请求STF操作实体和道岔。这里的ATP为游戏内可执行保护，不是经过认证的ETCS产品。

| 文件 / 组件 | 版本 | 职责 |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | 类型化契约、遥测、驾驶/调度服务；不是独立服务器核心 |
| SkyTrainFolia / STF | **5.0.2** | 编组、实体物理、区块票据、手动驾驶、TIMS、ATP及动作执行 |
| STCS | **4.3.5** | 工务设备、RailGraph、定位、占用、预约、MA和道岔排路仲裁 |
| SkyATO-Lite / SAL | **0.5.0** | SAL运营计划、叫车、停站、Title、回库与移交；不直接操纵实体 |
| SkyPCC / PCC | **2.4.1** | 网页工务地图、运营地图、车辆段列表及受控调度操作 |

**[新增 · 2026-11-21]** 本轮交付包括SAL固定运营线路和出入库、双向驾驶权移交、RailGraph局部更新、正式可执行MA/ATP显示、PCC独立运营地图，以及新的指令主题。SAL蓝色前缀为 `[SkyATO-Lite]`，STF灰色，STCS黄色；旧默认前缀自动迁移，自定义前缀保留。版本页列出SAL，并纠正过时的“ATP不动作”提示。

**[修改 · 2026-11-21]** 上下行 `<线路名>:up|down` 优先方向、单线站间整体预约、任意途中多次折返寻路、Python计算守护进程仍为设计草案，本包没有这些运行能力。现有出入库可以评估停车后的双向路径，不能据此承诺任意配线都能自动多次换端。

[TRAFFIC-PLANNING-DAEMON-DESIGN](doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md)

<a id="section-2"></a>

## 2. 安装、升级与回退

**[修改 · 2026-11-21]** 环境：Java25、已适配的Shiroha/Folia26.2或26.3；普通浏览器可使用PCC，无需客户端模组或TrainCarts/BKCommonLib。版本适配不等于兼容所有Folia分支。

1. 完整解压ZIP。先备份世界和整个plugins，再停服，将分发目录 `plugins/` 中五个JAR复制到服务器plugins；移走各模块旧JAR，不要复制整个文档目录进plugins。
2. 启动生成默认配置，检查五插件启用；停服合并配置后完整重启。不要热卸载。已有配置不一定自动补齐所有键。
3. 使用 `/st version zh`、`/stcs status`、`/sal help` 检查。服务器本机打开 http://127.0.0.1:8765/ ，网页更新后强制刷新。远程访问由管理员设置反向代理与鉴权。
4. `/st reload` 会重载列车数据，必须先停稳全部列车；STCS/PCC配置通过完整重启应用。SAL旧任务重启后进入RECOVERY_REQUIRED，检查后恢复为先回库，不盲目续跑。
5. 不清空账本来“修复”升级。ZIP中的SHA256清单用于核对文件，不能证明世界和账本可随意混用。源代码归档位于Source，补充契约和图片均在包内。

```text
SkyworldTrainAPI-2.7.0.jar
SkyTrainFolia-5.0.2.jar
STCS-4.3.5.jar
SkyATO-Lite-0.5.0.jar
SkyPCC-2.4.1.jar
```

[SHA256](../Latest/SHA256.json) · [LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md)

<a id="section-3"></a>

## 3. 权限与语言

**[新增 · 2026-11-21]** 直接释放、离车、断线、移动、换司机、离开库位或确认过期都不能完成移管；手动状态持久保存，不会马上被自动接管。普通司机需skyatolite.driver及skytrain.use；管理建线与home需skyatolite.admin。

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

<a id="section-4"></a>

## 4. 第一列手动列车

**[修改 · 2026-11-21]** 默认请求FS，不等于立即获批FS。手动司机显式取得驾驶权后请求FS；SAL虚拟司机同样必须取得有效可执行许可，未就绪/SB/恢复中保持制动。

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
- `/st release` 主动释放驾驶权并保持制动。只有先在库位确认SAL纳管，才会完成自动移管。

### Hotbar

启用 `/st hotbar` 时必须先选中快捷栏 **第 5 格**。启用本身不输入 N，也不解除已有制动；之后切换槽位才输入级位。

| 槽位 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 级位 | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

再次 `/st hotbar` 退出时不要求第 5 格。也可用 `/st cab` 打开图形驾驶台；仍需先 `/st drive`。

<a id="section-5"></a>

## 5. 指令参考

`<...>` 必填，`[...]` 可选。下表采用推荐写法，不穷举历史别名。

### 司机与查询

| 指令 | 用途 |
| --- | --- |
| `/st help [page] [language]`、`/st version [language]` | 帮助与已安装版本 |
| `/st list`、`/st info [train]` | 列表、列车状态及实际操控模式 |
| `/st scan [radius]` | 查询附近矿车 |
| `/st drive`、`/st release` | 当前列车驾驶权 |
| `/st cab`、`/st hotbar` | 图形驾驶台/快捷栏 |
| `/st forward`、`/st neutral`、`/st backward` | 换向器 |
| `/st p1` 至 `/st p4`、`/st n`、`/st b1` 至 `/st b7`、`/st eb` | 手柄，也有 `/p1`、`/n`、`/b7` 等根命令 |
| `/st horn`、`/st bell` | 鸣笛/铃 |
| `/st lang zh\|en\|fr\|jp\|ru` | 个人语言 |
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
| `/stcs ma fs` | **[修改]** 默认请求FS，不等于立即获批FS。手动司机显式取得驾驶权后请求FS；SAL虚拟司机同样必须取得有效可执行许可，未就绪/SB/恢复中保持制动。 |
| `/stcs ma release` | `stcs.ma` + 当前司机，释放车前预约，不清除车身占用 |
| `/stcs ma status` | `stcs.admin`，MA 与阻塞诊断 |
| `/stcs admin status` | `stcs.admin`，所乘列车保护模式 |
| `/stcs admin isolate true\|false` | 列控切除 |
| `/stcs admin bypass true\|false` | 监督旁路 |
| `/stcs admin shadow true\|false` | 影子测试 |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`，保留占用与成员观测诊断 |
| `/stcs rebuild` | **[修改]** STCS拥有唯一物理图。新增/删除设备和轨道事件只触发局部更新；道岔开向变化不等于拓扑变更。rebuild优先执行管理员附近的已加载源，再处理其他已加载源，到卸载边界停止。 |
| `/stcs export` | `stcs.admin`，导出当前图，不等于重新扫描 |
| `/stcs switch info` | `stcs.use`，附近 8 格道岔查询 |
| `/stcs switch change` | `stcs.admin`，附近 8 格道岔转换 |

~~现在玩家申请命令是 **`demand`，不是 `request`**。内部事件仍可叫 `MA_REQUESTED`，不代表旧玩家指令有效。`/sta` 和 `/skypcc` 主要提供帮助/版本，不另设完整驾驶命令体系。~~

**[修改 · 2026-11-21]** `/stcs ma fs` · `/stcs ma demand` (alias)

**[查询 · 2026-11-21]** `/sal help` · `/sal status` · `/sal route list Line1` · `/sal train home`

直接释放、离车、断线、移动、换司机、离开库位或确认过期都不能完成移管；手动状态持久保存，不会马上被自动接管。普通司机需skyatolite.driver及skytrain.use；管理建线与home需skyatolite.admin。

<a id="section-6"></a>

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
| `automatic.*` | **[修改]** 编组、实体物理、区块票据、手动驾驶、TIMS、ATP及动作执行 / V_target |


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
/sal release
/st property test01 pushable true
```

- `name` 是管理列车名/列车号；改名后命令使用新名称。
- `trainnumber` 是独立车次，可保留前导零，最多 32 字符，不允许控制字符或 `|`，不强制全服唯一。`clear` 或 `-` 清除。
- `displayname` 是显示名称，不能代替内部 UUID。
- 常用属性还包括 `destination`、`collision`、`playersenter`、`playersexit`、`pickupitems`、`invincible`、`allowplayertake`、`requirepoweredcart`、`sound`、`keepchunksloaded`、`gravity`、`friction`、`waitticks`、`speed`、`maxspeed`、`spacing`。
- `pushable=true` 不是唯一推动条件；驾驶权、未 release 的手动接管及运行状态仍可能阻止推动。查 `/st info` 的自动处理阻止原因。
- owner/tag/route 字段存在，不表示已实现完整调度、全网寻路或所有者权限隔离。

<a id="section-7"></a>

## 7. 线路与道岔建设

**[修改 · 2026-11-21]** STCS拥有唯一物理图。新增/删除设备和轨道事件只触发局部更新；道岔开向变化不等于拓扑变更。rebuild优先执行管理员附近的已加载源，再处理其他已加载源，到卸载边界停止。

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

Origin 第四行指定从零点向哪一侧发送里程；End 第四行指定从哪一侧接收里程，并显式结束本线归属，而不是结束物理轨道。两者均填写 `left`/`right`，不能作为名称。相对方向按牌面解释，不能只按 PCC 屏幕左右判断。`signal` 可作为登记节点，但不代表已实现信号机 ATP。

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

<a id="section-8"></a>

## 8. SAL运营线路、牌子与驾驶权移交

**[新增 · 2026-11-21]** 固定线路按次叫车，不是无限循环或时刻表ATO。线路首尾为station，可重复访问同一站；站间可绑定应答器与道岔，分支歧义需补中间路径点。没有自动刷车：需先建立STF编组和有效车辆段。

### 命令与选择

1. 面向首站出发方向创建Line1；必要时显式设置方向。
2. 先放首站station；沿运营方向站到图中应答器/道岔旁逐个bind。8格内多候选时输入choose编号，60秒有效；也可传设备UUID或station ID。
3. 每次新放station会按放置顺序追加。用list核对路径顺序，remove只删所列序号的访问。需要终到首站时再次绑定该station。
4. 在station的绑定命令末加reverse，表示该次停站后折返；不是任意位置自动多次折返。
5. preview持续两分钟：闭环绿色，否则红色；绿色不是空闲或MA。validate持久保存已验证状态，publish才允许叫车。
6. 线路有任务时不能编辑。编辑、相关站点变更或依赖失效需要重验；无关图变更不取消发布。
7. 部署depot、巡航和message牌，建立并绑定列车，再通过按钮或awake叫车。

```text
/sal route create Line1
/sal route direction Line1 east
/sal route bind Line1
/sal route choose 1
/sal route bind Line1 <deviceUUID>
/sal route bind Line1 <stationID> reverse
/sal route list Line1
/sal route remove Line1 <index>
/sal route preview Line1
/sal route preview Line1 off
/sal route validate Line1
/sal route publish Line1
```

### 牌子逐行格式

`[SAL]` = `[SkyATOL]`

#### depot

```text
[SAL]
depot
maintown
L1
```

车辆段名maintown和停车位L1分别占第三、四行。L2、L3各建独立牌，位置可分散；depot是停车位置，不是始发站。除awake外，牌须关联RailGraph已登记的轨道柱。

#### station

```text
[SAL]
station
15
Line1
```

station第三行为停站秒数，第四行为SAL运营线路；没有另填站名的位置。先create线路再放station，站名在PCC按线路及访问顺序显示。

#### awake

```text
[SAL]
awake
maintown
Line1
```

awake第三行为车辆段，第四行为线路；按钮给牌子或支撑方块供电的上升沿触发。awake无需放轨道下。持续供电和服务器启动不自动叫车；重复请求会合并。

#### property / v_target

```text
[SAL]
property
V_target
36km/h
```

property当前仅支持V_target，第四行速度；v_target简写把速度写第三行，第四行留空。支持km/h、kmh、m/s、m/t、b/t、mph，无单位为方块/tick；目标仍受车型、列车上限和ATP限制。

```text
[SkyATOL]
v_target
36km/h

```

#### message

```text
[SAL]
message
Skyworld
5
```

message第三行为字面文本，第四行为显示秒数；向本车乘客显示中央Title，新消息立即覆盖旧消息，不排队、不执行指令。

### 建车、叫车与执行

**[修改]** 琪露诺 — SAL

管理员坐在已组编、停稳的列车里设置home。home只绑定车辆段，不固定L1；无真人驾驶权的新车可手推进入归属库位，由STF低速接管并停车。候选车须观测新鲜完整、无TIMS保持、无真人驾驶权和其他任务。

```text
/st create demo 6
/sal train home maintown
/sal train home
/sal awake maintown Line1
/sal status
```

一次任务：出库→首站接客→按计划运行/停站→末站→预约可用库位→回库停稳。无人上车也完成一次任务；乘客可跟回库，想再坐一圈须站台叫车。首站同线路车辆尚未离站且符合条件时优先复用，取消回库、必要时安全换向并重计完整站停，不额外拉车。驶离首站或故障车不复用。

出入库基于RailGraph规划，不按游戏道岔当前开向裁剪候选；STCS确认进路后排岔，STA/STF执行。可先释放旧MA、停稳换向、确认后按实际领车位置重新规划。没有可用库位、空间或路径时保持等待。任务阶段不是实际速度。

### 手动与自动移交

自动转手动：仅已回库PARKED、完整停稳且无任务/故障保持的车可 `/sal release`；等待释放后在首/末车厢 `/st drive`。正在回库途中不能直接释放。

手动转自动：持驾驶权到depot，连续零速3秒→点击确认SAL纳管→30秒内手动输入 `/st release` 或点击第二按钮。成功后归属设为实际车辆段，进入自动待命，不立即发车。

直接释放、离车、断线、移动、换司机、离开库位或确认过期都不能完成移管；手动状态持久保存，不会马上被自动接管。普通司机需skyatolite.driver及skytrain.use；管理建线与home需skyatolite.admin。

```text
/sal release
/st drive
```

### 空车、故障与任务管理

```text
/sal stop <trainUUID>
/sal resume <trainUUID>
/sal finish <trainUUID>
```

stop保持停车；resume需要管理员核对停稳、完整性与图后恢复为先回库；finish在可信定位、完整停稳后释放MA并结束任务。命令参数是列车UUID，不能拿线路名代替。

STF区块票据不依赖乘客，SAL仅提交前方保活需求；STF异步加载并在所属区域线程加票。未就绪禁止牵引。整服空服暂停需单独关闭（pause-when-empty-seconds=-1）；区块票据不能驱动暂停的服务器。

TIMS自愈先保持停车，只在真实完整性恢复后有限重试（最多3次）；不改成员、不删车重生、不清车身占用。真缺车、异常间距、人工Trip需要人工检查。

偏离路径在PCC告警；停稳、完整且定位可信后申请待批SR。每段由管理员单独批准，不能自动无限续发SR；无可信定位不动车。

[SAL-MANUAL-HANDOVER](doc/SAL-MANUAL-HANDOVER.md) · [SAL-DEPOT-AND-RETIREMENT](doc/SAL-DEPOT-AND-RETIREMENT.md)

<a id="section-9"></a>

## 9. 可执行MA、默认FS与ATP

**[修改 · 2026-11-21]** 默认请求FS，不等于立即获批FS。手动司机显式取得驾驶权后请求FS；SAL虚拟司机同样必须取得有效可执行许可，未就绪/SB/恢复中保持制动。

FS按许可曲线监督速度与EoA；SH/SR默认上限40km/h，仍受更低的曲线限制。TR停稳后ack→PT，再release→SB，核对原因后重新申请。TIMS保持须先真实恢复完整性并确认。

正常进路首次提交后静默续租，STCS自动延长MA；无需每半秒反复发申请。路径、版本、控制权或租约失效后须重新建立有效请求，迟到心跳不能复活旧许可。

影子通道仅显式测试用；影子数据保持simulationOnly=true、executable=false，不能冒充正式许可。BYPASS、ISOLATED和RECOVERING各有门槛，不会因默认FS绕过。

释放MA只释放可安全释放的前方预约，车身占用继续保留。普通卸载、断线和超时不能证明出清；显式删车须有STF完整销毁回执并清理虚拟司机会话，才释放对应证据，其他列车不受影响。

```text
/stcs ma fs
/stcs ma status
/stcs ma ack
/stcs ma release
```

### 配置与局部建图

STCS拥有唯一物理图。新增/删除设备和轨道事件只触发局部更新；道岔开向变化不等于拓扑变更。rebuild优先执行管理员附近的已加载源，再处理其他已加载源，到卸载边界停止。

旧已确认连接在卸载时保留，可用于静态规划；从未确认的方向仍未知，已确认断轨不能因卸载变绿。占用、道岔物理状态和可执行许可另行检查。

默认60秒复核已加载轨道；配置范围1–86400秒，每批源1–64。健康复核不增加结构revision。SAL使用边依赖摘要，仅受影响线路重验；MA仍受全局图版本围栏限制。

```yaml
graph:
  review-interval-seconds: 60
  sources-per-tick: 4
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  check-exit-capacity: true
```

`look-ahead` ≠ `lock-distance` ≠ `max-authority-distance`

`FS lock = max(lock-distance, v²/(2a) + v×lead + buffer)`

`a = 1 m/s²; lead = 5 s; buffer = 25 m; v = m/s`

[RAILGRAPH-INCREMENTAL](doc/RAILGRAPH-INCREMENTAL.md) · [STCS-DYNAMIC-SWITCH-LOCK](doc/STCS-DYNAMIC-SWITCH-LOCK.zh.md) · [CONFIGURATION-SWITCHES](doc/CONFIGURATION-SWITCHES.md)

<a id="section-10"></a>

## 10. SkyPCC工务与运营地图

~~**[新增 · 2026-11-21]** 工务地图显示设备、工务归属、占用、MA与调度操作。切换“地图图层→运营线路”后使用独立地图/视野，不绘制应答器、道岔、原点等工务设备；下拉选择全部或单条SAL线路。~~

**[新增／修改]** 在同一张轨道图上勾选工务设备、SAL设备、运营线路与实时行车。点击「进入运营线路编辑模式」，输入转岔使用的Token；选择线路后点「编辑所选线路」，或选择至少一个已有车辆段创建草稿。

点线DRAFT＝规划中；长虚线VALIDATED＝验证且当前依赖有效；实线PUBLISHED＝发布且当前依赖有效。草稿直线连接有“未确认轨道路径”提示，不构成连通证明。

选择车辆段高亮全部登记停车位。总列位包括标记不可用的登记点；停放列车必须新鲜完整、无TIMS保持并停在唯一有效库位；运行中指持SAL任务，包括站停、暂停和恢复，不按速度为零排除。

其他/状态未知单列显示手动车、段外待命和缺失观测。空心库位不表示空闲许可；过期或来源失效时显示未知/不可用，不把旧位置画作当前位置，也不把缺数据当零车。

可点击停车位定位、列车名看详情；两图支持独立缩放/拖动、字号、主题和多世界选择。PCC中英法日四语言；STF中英法日俄五语言，荷兰语和文语手册不增加游戏语言。

写操作仍需开启web.control-enabled和Bearer令牌，web.require-https-for-control默认true；改false仅放宽HTTPS要求，不取消鉴权。令牌不在本分发包中提供。

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  require-https-for-control: true
```

[PCC-OPERATING-MAP](doc/PCC-OPERATING-MAP.md)

<a id="section-11"></a>

## 11. 声音与HMI

~~MA BossBar 仅对有效司机显示。影子限速位于计分板第二行，并以 `影子 MA | 信息 | ATP限速: xx km/h` 显示在 BossBar；无有效曲线显示 `--`。`settings.cab-ma-bar-range-meters` 默认 300 m，仅控制血条比例。~~

**[修改]** 默认请求FS，不等于立即获批FS。手动司机显式取得驾驶权后请求FS；SAL虚拟司机同样必须取得有效可执行许可，未就绪/SB/恢复中保持制动。 FS按许可曲线监督速度与EoA；SH/SR默认上限40km/h，仍受更低的曲线限制。TR停稳后ack→PT，再release→SB，核对原因后重新申请。TIMS保持须先真实恢复完整性并确认。

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
| shrinking | `minecraft:block.note_block.bit` | 运行中剩余滑动 MA 开始收缩 |
| low | `minecraft:block.note_block.pling` | 低余量警告 |

声音 ID 使用 Java 版 `namespace:path`，省略命名空间默认为 minecraft。自定义声音需客户端资源包支持。范围：volume 0..4、pitch 0.5..2、count 1..16、interval-ticks 1..200。

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

<a id="section-12"></a>

## 12. STA契约与HTTP接口

**[修改 · 2026-11-21]** STA是进程内Java服务契约，不是已经上线的Python/TCP RBC。Message和Packet是不同层次，接口族和会话/序号/时效都必须验证。

ATO私有Message6508；SUBSET_139 Packet1031/1032致敬31/32+1000。续租/库位停车/双向移交分别用私有Packet5115–5118，不冒充真实ATO标准编号。MA沿用Message1003/Packet1015，遥测Message1136。

v8 OperatingNetworkService提供只读显示快照（schemaVersion=1），不是新MA；v9 TopologyDependency提供边依赖校验。SAL发布运营数据，PCC后台缓存，HTTP线程不读实体。API路径版本、DTO版本和JAR版本分别编号。

```text
GET /api/v5/graph
GET /api/v5/trains
GET /api/v5/messages
GET /api/v5/railway-events
GET /api/v5/shadow-ma
GET /api/v5/config
GET /api/v5/events
GET /api/v6/operational-ma
GET /api/v7/station-areas
GET /api/v8/operating-network
POST /api/v5/switch
POST /api/v6/sr
POST /api/v6/ma/revoke
```

[STA-V8-ATO-PROTOCOL](doc/STA-V8-ATO-PROTOCOL.md) · [STA-V5-MIGRATION](doc/STA-V5-MIGRATION.md)

<a id="section-13"></a>

## 13. 数据与故障排查

**[修改 · 2026-11-21]** 新增SAL state.json保存牌子、线路、home、手动待命及任务，.bak用于备份；未完成移交确认不跨重启保留。写盘失败停止发车。正式occupancy-ledger.json与旧shadow-occupancy.json是不同账本，不能只删一个来绕过保护。confirmed-destructions.json是已确认销毁回执记录，升级保留。

### 重要文件

以下相对于服务器根目录：

| 路径 | 内容 |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`、`vehicles/` | 全局配置、声音与车型 |
| `plugins/SkyTrainFolia/trains.yml` | 编组、属性、保护模式 |
| `plugins/SkyTrainFolia/savedtrains.yml` | 模板 |
| `plugins/SkyTrainFolia/switches.yml` | 道岔登记 |
| `plugins/SkyTrainFolia/infrastructure.yml` | STF 基础设施/标定 |
| `plugins/SkyTrainFolia/stations.yml`（显示元数据；旧 automatic-signs.yml 不再读取） | 车站/自动牌子记录 |
| `plugins/STCS/markers.yml` | STCS 设施登记 |
| `plugins/STCS/railgraph.json` | 图快照，不是占用账本 |
| `plugins/STCS/occupancy-ledger.json` | 保留占用证据，不可为强行发 MA 随意删除 |
| `plugins/SkyPCC/config.yml` | 网页与控制凭据 |

当前图仍是一个逻辑图/JSON 导出，未实现“一条主线一个文件”的生产分片。跨线路物理边不能随意按线路名切断；离线分析导出副本，不编辑运行中图/账本。

### 常见现象

| 现象 | 优先检查 |
| --- | --- |
| ~~info 是 auto，station 无效~~ | ~~是否有司机、是否显式 release、保护模式、红石使能、牌柱关联及阻止原因~~ |
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

- 叫不出车：查publish、awake上升沿、车辆段名/线路名、手动待命、完整观测、TIMS和空闲任务；车头相反本身不应要求手工摆正。
- 站点断路：先查RailGraph及routingIssues，新增轨道在加载后局部重扫；不能因unresolved里有chunk_not_loaded就判断路。
- 回库保持：查可用库位、全列空间、路径、道岔确认、MA和实际故障原因，不连续盲目awake或清账本。
- 删车后仍占用：核对所删UUID、完整销毁回执及虚拟司机会话清理；缺失/未知实体不能直接视作安全删除。
- 网页运营数据不可用：核对SAL/STA版本、服务、图版本、快照时效；脚本需强刷，工务图和运营图数据语义独立。

<a id="section-14"></a>

## 14. 验收清单与已知边界

**[修改 · 2026-11-21]** 构建同时对26.2和26.3运行Java回归；本次新颜色/版本页与SAL帮助有专门验证。测试结果清单随分发包提供。

已完成：此前26.3单列无乘客接客→一圈→回库；26.2真实PCC能显示已发布line1、maintown两个库位，浏览器运营地图、三线型、筛选、统计、过期状态和363个四语言词条已通过。不能把这一结果算作所有配线、多列车和载客验收。

收件人仍须现场测：反向叫车、出入库过岔、首站复用、L1/L2竞争、全列清岔/折返、空服区块加载、Title覆盖、TIMS/掉线/重启、双步移交及删车释放仅影响目标UUID。

[VALIDATION](../VALIDATION.md)

<a id="section-15"></a>

## 15. 开发、源码与分发

**[修改 · 2026-11-21]** 源码归档包含五模块、共享代码、默认资源与测试，解压后在源码根运行Common/build.ps1并提供两个已准备的测试服务端目录和JavaHome。构建发布仅在双版本检查通过后更新Latest。分发ZIP不包含Minecraft服务端核心、世界、私有配置、令牌或证书。

```powershell
./Common/build.ps1 -ServerRoot <server26.2> -ServerRoot26_3 <server26.3> -JavaHome <jdk25>
node SkyPCC/tests/edge-line.cjs
node SkyPCC/tests/i18n.cjs
node SkyPCC/tests/operating-map-browser.cjs
./Common/package-source.ps1
```

原代码/文档MIT许可和图像单独授权均随包保留。STA契约、SAL动作、实体线程和STCS许可边界不因新提示配色改变。

[LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md) · [TrainCarts](https://github.com/bergerhealer/TrainCarts) · [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)

## 旧手册与已移除用法

**[删除 · 2026-11-21]** 下列为已移除的旧STF自动牌操作原文，删除线表示不可再照做；其他历史正文见旧手册。

~~[+stf]~~
~~station~~
~~5~~
~~continue 40kmh~~

~~/st property demo mode auto~~

~~[+stf]~~
~~spawn~~

~~[+stf]~~
~~destroy~~

[完整旧手册（历史基线，不作为新版操作指引）](doc/manual-baseline-20261120/README.zh.md)

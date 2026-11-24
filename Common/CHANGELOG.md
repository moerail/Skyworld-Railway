## SkyPCC 2.4.1 — 2026-11-24

- 修复 SVG 选中设备／轨道的巨大原生焦点外框；改为固定屏幕宽度的路径和圆环高亮，保留键盘焦点反馈。
- 文字缩放旁新增高亮大小（50%–250%，点击百分比复位）及颜色选择，保存在当前浏览器。
- 工务设备增加 Balise、END、ORIGIN、道岔子开关；总开关全选／全关，部分选择显示半选状态，图标、标签和道岔箭头同步过滤。

# Updates / 更新说明

## PCC2.4.0 / SAL0.5.0 / STA2.7.0 / STF5.0.2 / STCS4.3.5 — 2026-11-24

- **[修改]** PCC统一为一个RailGraph画布和摄像机，勾选工务设备、SAL设备、运营线路和实时行车；取消旧双画布切换。
- **[新增]** 既有Token授权的运营线路CRUD、既有设备/有向图边点选、停站/折返、至少一个车辆段绑定、草稿保存、依赖验证、版本发布及停用。
- **[新增]** SAL唯一写入的STA v10 RouteEditingService；HTTP异步网关、有界幂等、草稿版本和单调workspaceRevision围栏，避免旧缓存导致假冲突。
- **[修改]** 草稿与当前发布版分开，发车任务持久固定自己的routeSnapshot与停站参数；新增显示routeVersion。编辑不覆盖旧任务。
- **[新增]** 游戏/PCC或多个编辑者冲突同时冻结两侧，禁止保存/发布；跨重启保留，必须人工放弃或接管提案，解消不自动提交。
- 游戏编辑增加depot/close/resolve/retire/delete，设备choose检查草稿和图版本。只复用现有牌子；不创建虚拟站点或改写牌子归属。
- 更新七版手册、司机文档及中英契约指南；STCS静默复核继续保留。Java26.2/26.3各111项通过，浏览器统一图层/Token/保存发布/冲突/缓存回退及422个四语言词条通过。
- [操作与契约](doc/PCC-ROUTE-EDITING.md) · [English](doc/PCC-ROUTE-EDITING.en.md)。通用多段折返、上下行、单线整段预约与Python守护进程仍为设计。

## STCS 4.3.4 — 2026-11-24

- **[修改]** 正常局部建图和周期断轨复核不再默认输出控制台摘要。
- **[新增]** `graph.log-successful-reviews: false`；设为true恢复成功摘要，旧配置启动时补入默认值。
- 新增或变化的路由问题仍输出WARNING，最多列8项原因；相同旧诊断不重复刷屏，异常日志不受开关影响。未加载但有旧连接覆盖的诊断不冒充断轨。
- 不改变复核周期、图结构、占用、MA或实体操作。其余组件沿用2026.11.21版本组合。

## Distribution 2026.11.21 — STA2.6.1 / STF5.0.1 / STCS4.3.3 / SAL0.4.1 / PCC2.3.1

- **[新增]** 可独立解压分发的五插件ZIP，含七版详细手册、五语言司机手册、契约、图片、历史基线、源码和SHA256清单；检查解压后的相对文件及锚点链接。
- **[修改]** 按本轮最终实现重整原Manuel正文，破坏性更新和最终产物前置，新增/修改/删除/查询标记，废弃STF自动牌手顺用删除线保留。上下行、单线预约、多段折返和Python守护进程仍明确标为设计。
- **[修改]** 共享版本页加入SAL，五语言更正“默认影子MA、ATP不动作”为默认请求FS、可执行ATP已启用且行车仍需有效MA；不改变实际保护状态或许可检查。
- **[新增]** SAL指令按线路/叫车/任务/移交分组，所有文字与聊天确认统一蓝色 `[SkyATO-Lite]` 前缀。普通司机可看移交帮助，管理员项仍由原权限检查保护。
- **[修改]** STF灰色、STCS黄色指令主题。仅迁移旧默认前缀，自定义前缀保留；错误保持红色。STA/PCC提示主题不变。
- Java双版本回归各108项通过；详细验证范围和未验收项目见 [VALIDATION](../VALIDATION.md)。这些提示/文档修改不改变列车物理、驾驶权、MA或占用释放。

## SkyPCC 2.3.0 / SAL 0.4.0 / STA 2.6.0 — 2026-11-20

- 新增独立运营线路地图，隐藏工务设备，按运营线路筛选，以点线／长虚线／实线区分规划中、已验证和已发布。
- 选择车辆段后高亮停车点，展示总列位、停放列车、SAL运行任务及其他／未知车辆。缺失或过期观测不作为空闲证明。
- SAL持久保存验证状态，经新增STA只读运营快照供PCC后台缓存及HTTP/SSE显示；保留既有工务地图、MA和驾驶权行为。
- 同步四语言标签，补充契约、车辆段投影与浏览器回归。[操作及契约](doc/PCC-OPERATING-MAP.md)。

## STF 5.0.0 / SAL 0.3.0 / STA 2.5.0 — 2026-11-20

- 回库待命车可 `/sal release` 转手动，再 `/st drive`；持久保留手动状态，避免重新自动接管。
- 手动车在库位连续停稳3秒后提示确认纳管；确认后仅同一驾驶权显式 `/st release` 的一次性回执能完成移管。直接释放、离车、断线、移动与过期不纳管。
- 破坏性移除STF旧牌子自动起停、巡航/站停、自动刷车/销毁执行链及 mode automatic 入口；保留STA执行器、手动驾驶、ATP与工务设备。
- STA私有Packet 5117/5118提供停车会话释放与显式移管提交；[契约和迁移说明](doc/SAL-MANUAL-HANDOVER.md)。

## STCS 4.3.2 — 2026-11-20

- 修复出入库道岔节点的瞬时 `ROUTE_MISMATCH`：定位先按实际确认的道岔连接筛选端口，再比较运动切线，避免把转弯首帧投影到未连接支路。
- 未确认、转换中或故障状态不推定支路连通；车辆离开节点后不受身后道岔改变影响。真实驶入其他支路仍撤销许可，STA 接口与 SAL 路径规划不变。
- 补充正向/反向过岔的连续 FS、物理状态与命令状态区分、未知状态及真实偏离回归。

## STF 4.1.3 / SkyPCC 2.2.3 — 2026-11-19

- 自动驾驶司机栏彩蛋：琪露诺 / Cirno / チルノ / Чирно；英文与法文使用 Cirno。同步游戏侧栏与 PCC，真人姓名和运行模式不变。
- 配套 SAL 0.2.1、STA 2.4.1、STCS 4.3.1。此前出入库/续租/退役修复已通过 26.2、26.3 各 104 项测试并部署。
- 实服已清除有完整回执的 test10、test1-0 共 10 个残留占用单元；4 列在场列车的原有资源逐项保留。详见[验收记录](doc/SAL-DEPOT-AND-RETIREMENT.md)。

## SkyATO-Lite 0.2.1 / STA 2.4.1 / STCS 4.3.1 / STF 4.1.2 / SkyPCC 2.2.2 — 2026-11-19

- 出库/回库在 RailGraph 上选择合法最短路径，同时评估停稳换向；先释放旧许可再由 STA 换向，之后按实际领车位置重算。
- 进路首次提交与静默续租分离，MA 由 STCS 自动延长；持续按既定进路排前方道岔，取消每半秒重复申请事件。
- 首站尚未离站的同线路车辆优先复用，取消回库并刷新站停；出库换向、复用准备中的重复叫车不额外派车。
- 归属车辆可手推到 depot 停车点，由 STF 低速接管制动并进入 PARKED，停车不虚构司机或 MA。
- 显式删车时清除虚拟司机会话，完整删除回执可正常释放 STCS 对应占用及预约，SAL 同时结束任务；其他列车占用保留。
- 可执行 FS/SH/SR 在 PCC 显示为正式 MA/ATP，保留旧协议兼容；[契约、操作与验证边界](doc/SAL-DEPOT-AND-RETIREMENT.md)。

## STCS 4.3.0 / STA 2.4.0 / SkyATO-Lite 0.2.0 — 2026-11-19

- STCS 注册/删除设备局部建图，更新穿过新设备的旧边及邻接边；任务串行合并，未知或不可用目录不冒充删除。
- rebuild 优先管理员附近的已加载源，边界停止且保留未加载区域旧连接；拓扑维护不再主动加载区块。
- 默认每 60 秒复核旧路径中已加载的轨道片段，配置 graph.review-interval-seconds；放轨/拆轨触发局部更新。
- 健康复核不改变结构版本；新增 STA v9 边依赖摘要及 STCS routingIssues，区分已知卸载、未知方向与真实断轨。
- SAL 仅从 RailGraph 绑定轨道位置；无关图变化保留发布状态，受影响线路自动重验，运行路径改变保持待检查。
- STF 4.1.1、SkyPCC 2.2.1 配套，PCC 缓存同时刷新复核时间；MA 版本围栏、占用、TIMS 与实体线程职责不变。[契约及部署说明](doc/RAILGRAPH-INCREMENTAL.md)。
- 验证：26.2、26.3 各 100 项 Java 测试通过。已备份更新本地超平坦 26.2 服并重启；既有 line1 仍为 872 格闭环，加载区块重扫只检查 4 个源，保留 221 条边且版本保持 94。

## SkyATO-Lite 0.1.1 / STA 2.3.1 / STF 4.1.1 / STCS 4.2.1 — 2026-11-19

- 核对 ERA 官方 SUBSET-126 1.1.0、130 1.0.0、139 1.0.0；明确其参考编号为 Packet，保留原编号 + 1000 的致敬方式。
- STA v8 增加 AtoProtocol，私有 Message 6508 与接口族区分不同规范的 Packet；实际驾驶意图使用 139/1031、车辆反馈使用 139/1032。
- 其余驾驶操作、Title、区块保活、许可申请与道岔执行分配私有 Packet 5100–5107、5110–5114；现有 MA Message 1003 / Packet 1015 与遥测 Message 1136 不变。
- SAL 控制、默认 FS 与道岔执行接入带头部的类型校验；保留旧 Java 参数构造器及进程内服务入口，Intent/View JSON 增加必须校验的头部。
- 新增契约测试覆盖编号、命名空间、接口族、保留编号、JSON 与执行前拒绝；没有新增网络传输或改变物理执行权、MA、TIMS、SR 审批流程。
- [编号、官方来源与升级说明](doc/STA-V8-ATO-PROTOCOL.md)。SkyPCC 保持 2.2.0。
- 验证：26.2、26.3 各 97 项 Java 测试通过；新版五插件及 SHA256 已生成到 Latest。仅本地构建回归，未修改生产服务器。

## SkyATO-Lite 0.1.0 / STA 2.3.0 / STF 4.1.0 / STCS 4.2.0 / SkyPCC 2.2.0 — 2026-11-19

- 独立 SAL 运营线路模型；管理员附近设备绑定和多候选选择、红/绿粒子闭环预览、station/depot/awake/property/V_target/message 牌子。
- 列车归属车辆段而不固定车位；单次叫车、首站接客、终到回库及动态停车位预约。原子控制权、过期意图、请求序号和释放 fencing。
- STA v8 自动列车与 MA 契约；道岔物理执行也改用 STA 类型化执行契约，保留 STCS 占用、预约、开向确认和锁闭检查。
- 手动车新驾驶会话默认申请 FS；自动任务进入同一可执行保护。MA 边界与运营停车目标分离，车载目标速度受 ATP 限制。
- 中央 Title 向本车乘客显示，后到信息覆盖；STF 侧栏保留速度/工务信息并增加运营任务、虚拟司机和 TIMS。
- 空车继续使用 STF 区块票据，并异步预加载沿线区块；未就绪时保持。TIMS 仅在真实完整性恢复后限次自愈，不改成员名单或清空车身占用。
- 偏离路径发布 PCC 告警，停稳、完整和可信定位后申请待批 SR。每段由 PCC 管理员批准；批准明确授权该恢复车停稳后的 TR/PT 确认，不能绕过 TIMS。
- 实车回归修正站点处相邻图边切换、停车目标与 EoA 余量、停稳观测延迟导致首次 release 拒绝的收尾问题。
- 26.2/26.3 Java 回归和 26.3 单列空车接客→一圈→入库→SB/IDLE 验证；实际站场、多车、折返与故障注入仍需验收。生产服未修改。

## STF 4.0.12 / STCS 4.1.2 · FS → release → SR race fix — 2026-11-14

- 修复车端清除 FS 后、STCS 异步释放完成前，运动线程重新接收旧 FS 的竞态。旧 FS 重装会让新 SR 报 MODE_CONFLICT，并保留 FS 剩余 MA；第二次 release 恰好重置车端，因而表现为 SR 才开始生效。
- 释放采用两阶段确认：车端先回到 SB、清空旧 MA 并拒绝接收授权；STCS 撤销旧意图并发布快照后，使用本次释放凭据确认完成。等待期间拒绝新申请并保持制动；失败保留保持，可重试 release。延迟或重复回执不能解除后续释放或清除新 SR，释放期间发生的 TR/PT 和 TIMS 故障保持不变。
- Snapshot reading and ATP decision installation share the release lock. Regression coverage reproduces old-FS reinstallation, verifies new SR distance, both mode-switch directions, renewed old grants, failed/retried releases, late receipts and STCS publication ordering.
- **配套更新 STF 4.0.12 与 STCS 4.1.2**，不要只替换其中一个。保留 4.0.11 的 hotbar 修复和点击提示。实服验收待完成，服务器安装未修改。
- Validation: full-workspace builds passed **93 Java test entry points on each of Shiroha 26.2 and 26.3**, including the reproduced release race, scoped STCS release and SR distance checks. Both STF display adapters are included.

## STF 4.0.11 · SR/FS hotbar recovery and onboarding — 2026-11-14

- 修复等待 SR 审批、SB/PT 或其他牵引联锁期间尝试加牵引，会将快捷栏驾驶静默关闭的问题。原来的错误处理把操作被拒绝误当作驾驶权丢失，导致后续 SR 获批或 release 后申请 FS 仍无法用快捷栏操纵。
- 快捷栏牵引、换向及 EB 输入仅在驾驶权检查失败时关闭快捷栏；普通操作拒绝保留输入设备。获批后司机重新选档即可，无需重新上车；不重放先前被拒绝的牵引操作。驾驶权丢失、下车与掉线仍关闭输入。
- Add regression coverage for pending SR → approval → power and release → FS → power with the same driver/input session, rejected traction preserving brakes, no automatic replay, other interlocks and ownership loss. Live-server acceptance remains pending.
- FS 获批、停车且尚未开启热键驾驶时，库莉酱提醒先选快捷栏第 5 格（N），提供可点击的 `/st hotbar`。选错位置点击时，由库莉酱提示位置错误并要求选择第 5 格；不改变操纵状态。已开启时停止提示，TIMS/TR/PT 恢复提示仍优先。五语言文案可在 `driver-guide.yml` 自定义；旧词库自动回退内置新增条目。
- Verify the clickable command, all nine slot positions, FS-only onboarding, enabled-device suppression, throttling and TIMS priority. Onboarding does not enable controls or apply traction automatically.
- Validation: **89 Java test entry points passed on each of Shiroha 26.2 and 26.3**. The 4.0.11 JAR includes both display adapters. Server installations were not modified.

## Optional exit-capacity and browser HTTPS checks — 2026-11-12

- Add `ma.check-exit-capacity` (default `true`) to STCS. Disabling it permits partial/short exits when issuing FS/SH/SR grants, including dynamically calculated FS lock ranges. Preserve point eligibility, occupancy/resource conflicts and search/MA caps.
- Add `web.require-https-for-control` (default `true`) to PCC and publish `requireHttpsForControl` in the browser configuration response. Explicit `false` permits HTTP switch control, SR approval and MA revoke; control-enabled and Bearer authentication remain unchanged. Generate v4 request IDs with `crypto.getRandomValues` on HTTP while retaining IDs across pending polls.
- Add end-to-end FS/SH/SR capacity tests and browser tests for missing/enabled/disabled policy on HTTP and enabled policy on HTTPS. [Configuration guide](doc/CONFIGURATION-SWITCHES.md).

## 为了同志 / Ради товарищей — STF 4.0.10 · 2026-10-26

[中俄双语发布说明 / Описание на китайском и русском](doc/releases/suite-v4.0.10.md) · STCS 4.1.1 / STA 2.2.0 / SkyPCC 2.1.0。

- 将俄语作为第五种 STF 界面语言接入：`/st lang ru`，兼容 `ru_RU`、`ru-RU` 和 `russian`；补齐命令补全、共享帮助与版本页、玩家语言保存及恢复。
- 翻译驾驶台、侧栏、MA/ATP BossBar、保护模式、TIMS/MA 回复、站点停车标记及司机引导词库；保留 SB/FS/SH/SR/TR/PT、命令与协议标识。旧词库缺少俄语条目时使用内置默认值，不覆盖既有自定义项。
- 补充旧版命令、基础设施查询、告示牌反馈和输入错误的俄语呈现。错误对象保留原始诊断，玩家数据在拼接前与固定文案分别处理；其他语言沿用原有文本。
- Russian is a per-player STF preference. The SkyPCC browser interface has its own language selection; this change does not add Russian to that web frontend. Translation does not change train control, braking or authority rules.
- Validation: **88 Java test entry points passed on each of Shiroha 26.2 and 26.3**, including Russian catalog/format checks, player-preference round-trip, old word-bank fallback and editable Russian overrides. Both STF display adapters are packaged in the JAR. Live-server UI acceptance is pending; server installations were not modified.

## 驾驶员交接与 MA / Driver handover and MA — 2026-10-24

- TIMS 保持通知和四语言司机引导增加可点击的 `/st tims ack`。停稳后的 TIMS 引导优先于 TR/PT、换向器和 MA 提示；确认后恢复既有 `ma ack → release → fs` 流程。沿用 TIMS 完整性、停稳、驾驶权检查和提示节流；旧版自定义词库缺少新条目时使用内置默认值。

- 司机下车、掉线或释放驾驶权后撤销旧 MA，清除车载许可缓存，回到 `SB` 并保持制动。前方预约在停稳、完整定位和 TIMS 确认后自动释放；车身占用继续保留。驾驶会话直接更换也适用，下一位司机需重新申请 MA，无需补做 `release`。
- Make `/stcs ma fs` the primary FS request command and retain `/stcs ma demand` as a compatibility alias. Update command completion, cab actions, help and manuals.
- Pair STCS **4.1.1** with SkyTrainFolia **4.0.9** for this change; current companion sources are STA **2.2.0** and SkyPCC **2.1.0**. Source-data outages retain their existing conservative handling and are not treated as confirmed driver loss.
- Validation: **87 Java test entry points passed on each of Shiroha 26.2 and 26.3**, including moving/stopped driver loss, lease replacement, reboarding, retained occupancy and delayed forward-reservation clearance. Live-server acceptance remains pending; server installations were not modified.

## SH 停稳换向 — 2026-10-13

- STF 4.0.9 / STCS 4.1.1：SH 保留调车申请并在停稳换向后自动重算方向一致的 MA，司机不必重新申请。原方向授权不能用于反向行驶，旧预约保留至新授权接收确认。FS/SR、TIMS、租约、拓扑、占用、道岔与 EoA 检查保持有效。
- [行为、安装与实机验收说明](doc/releases/sh-reversal-2026-10-13.md)。未修改生产服，未加入 TIMS override。

## STCS 4.1.0 and current source update — 2026-10-09

- Current sources: **STCS 4.1.0 / STF 4.0.8 / STA 2.1.4 / SkyPCC 2.0.3**. The maintainer confirmed the local STCS 4.1.0 testing passed. Preserve GitHub's later TIMS monitoring, dispatcher MA revoke, stopped-train rerouting and dual 26.2/26.3 support; do not downgrade companion versions. Live acceptance of this merged combination remains separate from the earlier STCS test report.
- FS point eligibility follows `max(lock-distance-meters, v²/(2a) + v*lead-seconds + buffer-meters)` using fresh speed telemetry. Defaults are 1 m/s², 5 s and 25 m. Disable `ma.dynamic-lock.enabled` to restore fixed eligibility. Search/MA caps, SH/SR bounds, conflicts, point checks and throat clearance remain in force.
- RailGraph schema v4 compresses contiguous straight paths and retains full curved/slope and service geometry; legacy graphs are backed up before migration. Publish the explicit SkyATO design draft and existing incident reports without claiming those incidents resolved.
- Update seven root READMEs, four driver READMEs and the desktop dispatcher README. [STCS 4.1.0 details](doc/releases/stcs-v4.1.0-railgraph-storage.md) · [Dynamic lock configuration](doc/STCS-DYNAMIC-SWITCH-LOCK.zh.md).
- Final merged validation: 86 Java test entry points passed on each of Shiroha 26.2 and 26.3; 9 desktop Python tests passed; 60 testbench tests ran with 3 skipped. PCC edge-line/four-language checks and local documentation links passed. Generated packages, a duplicate driver manual and an accidental diff-output file are kept out of this commit.

## SkyTrainFolia v4.0.6 candidate / Shiroha 26.2 + 26.3

- Package the 26.2 and 26.3 train display packet adapters in one JAR. Select the matching adapter from the server Minecraft version at runtime; unsupported versions retain vanilla display sync.
- Preserve the tested 26.2 packet stream, and encode 26.3 entity position and movement with `PositionPath` and `VecDelta`.
- Enable owned-region movement on both versions. Compile and run isolated Java tests against both server cores. Live-server acceptance is pending.
- [Adaptation report / 双版本适配说明](doc/releases/skytrainfolia-v4.0.6-shiroha-26.2-26.3.md).

## SkyTrainFolia v4.0.5 candidate / 多语言司机引导词库

- Move Kuri-chan's driver guidance into editable `plugins/SkyTrainFolia/driver-guide.yml`. Each scenario and language has a list; one phrase is selected randomly when shown. Add localization/service wait, MA wait, SR approval wait, reclaimed driver control, and held emergency brake hints. Commands stay fixed in Java and retain state checks.
- [Release notes / 更新说明](doc/releases/skytrainfolia-v4.0.5-driver-guide-lexicon.md).

## SkyTrainFolia v4.0.4 candidate / 库莉酱司机引导

- Add four-language, clickable driver hints for cab claim, neutral reverser, fresh SB MA request, stopped Trip acknowledgement, and PT release. Hints are state-gated and throttled; they never operate the train automatically.
- [Release notes / 更新说明](doc/releases/skytrainfolia-v4.0.4-driver-guide.md).

## SkyPCC v2.0.1 candidate / 小版本候选

- Accept remote switch-control and SR-approval POST requests when `web.control-enabled` is true and the request has the configured Bearer token. The old loopback and localhost Origin/Host checks are removed; POST and JSON requirements remain. No STA/STCS protocol change.
- 默认仍监听 `127.0.0.1`。远程访问可使用 HTTPS 反向代理，或由管理员显式配置监听地址与传输层保护。错误令牌继续拒绝，返回 `CONTROL_AUTH_REQUIRED`。
- [Release notes / 更新说明](doc/releases/skypcc-v2.0.1.md). Production validation pending / 尚待生产服验证。

## Suite v4.0.2 pre-release / 预发布

STF/STCS 4.0.2, STA/SkyPCC 2.0.0. [Release notes / 发布说明](doc/releases/suite-v4.0.2.md). Live-server acceptance pending / 尚待服务器验收。

### STF low-speed warning adjustment / STF 低速预警调整

- Near-limit audio uses a configurable low-speed band: at ATP limits <= 40 km/h, enter at limit minus 5 km/h and clear at/below limit minus 8 km/h. Above that band, existing `enter-gap-kmh` / `clear-gap-kmh` settings still apply; new-install defaults are 15 / 18 km/h. Overspeed priority and ATP brake thresholds are unchanged.
- 低速音效阈值新增 `shadow-atp.warning.low-speed-limit-kmh`、`low-speed-enter-gap-kmh`、`low-speed-clear-gap-kmh`，默认 40 / 5 / 8。SH 的 40 km/h 限速包含在低速档中；35 km/h 开始提醒，降至 32 km/h 及以下解除，避免反复响停。已有普通阈值配置不覆盖；仅调整音效，不改变 ATP 制动。

### STCS 4.0.2 MA recovery / MA 服务恢复

- Add administrator/console command `/stcs admin ma restart` (`stcs.admin`) for a stopped MA service after an I/O failure. Back up the ledger and pending snapshot, force a real write, retain occupancy, reset demands and revalidate current inputs. Drivers must request MA again; recovery never releases brakes or changes ATP channels.
- Require fresh stationary reports for outstanding executable authorities. Reject unavailable sources, persistent write failures and non-I/O runtime faults; those faults still require investigation/server restart. Running service returns `ALREADY_RUNNING` without disruption.
- 新增管理员/控制台恢复指令，保留占用并备份账本；恢复后司机重新申请 MA，不自动缓解制动。旧有效许可对应列车必须有新鲜的停稳报告；数据源不可用、持续写入失败时拒绝恢复。其他运行时错误不允许用此指令绕过。
- Includes the 4.0.1 Windows file-replacement retry fix. Compatible with STF 4.0.0 / STA 2.0.0 / SkyPCC 2.0.0; no protocol change. / 包含 4.0.1 文件替换重试；仅替换 STCS，无协议变更。

### Included STCS 4.0.1 fix / 包含的修复（未单独发布）

- Retry an access-denied occupancy-ledger replacement up to five attempts, with 185 ms of total backoff. This covers temporary Windows file sharing conflicts; persistent permission failures and other I/O failures still stop MA allocation.
- Preserve the committed ledger and pending `.tmp` snapshot on failed replacement. Do not delete or truncate the live ledger to bypass a lock. Correct the failure message for the experimental Enforced channel.
- 针对 Windows 拒绝替换占用账本增加有限重试；重试仍失败则继续停发 MA，不清除占用。修正错误日志中旧的“无 ATP 动作”描述。STCS 4.0.1 与本轮 STF 4.0.0 / STA 2.0.0 / SkyPCC 2.0.0 配套，协议未变。

## Experimental M3 manual-train protection / M3 手动列车实验保护 - 2026-09-25

**Pre-release: `suite-v4.0.0`.** 73 Java test entry points and PCC edge-line / four-language resource checks passed before publication. The latest fixes have not completed another live-server acceptance run.

**预发布：`suite-v4.0.0`。** 发布前已通过 73 个 Java 测试入口及 PCC 线路归属、四语言资源检查；最近修复尚未完成新一轮服务器验收。

Release set: **STF 4.0.0 / STCS 4.0.0 / STA 2.0.0 / SkyPCC 2.0.0**. Built locally; live-server acceptance is still pending. Upgrade installed components together after backing up RailGraph and retained occupancy. Do not mix binaries.

- Added a separate STA v6 operational-authority service. Manual trains alone use `SB/FS/SH/SR/TR/PT`; `/stcs admin enforce true|false` explicitly changes the ATP channel while stopped. Shadow MA/EoA remains observational.
- Added conservative onboard MA/EoA supervision with configurable relaxed/strict braking profiles, last-confirmed EoA fallback within the same graph/control session, trip acknowledgement, and a latched EB for unresponsive ordinary overspeed. New grants are acknowledged before earlier forward reservations are released.
- Added bounded SH requests and dispatcher-approved SR to a selected infrastructure node, exposed through an authenticated PCC control and `/stcs sr` admin command. MA loss produces a deduplicated PCC warning. SR approval is a service/event operation, not an ETCS telegram; executable MA reuses private Message 1003 / Packet 1015.
- Cab ATP mode now displays translated **channel | unchanged operating code**, for example `Enforced | SR`. STF's built-in pseudo-ATO automatic trains are excluded, including passenger-entered MA/mode commands.
- Fixed Enforced driver feedback: near-limit/overspeed warning playback now accepts the active channel. The sidebar and BossBar identify actual ATP B7/EB intervention, with short localized reasons in the sidebar. Configurable one-shot service/emergency cues play on intervention transitions, not repeatedly during a stationary SB hold.
- SH/SR now apply B7 immediately above their configured mode speed ceiling, independently of the relaxed FS overspeed timer. EoA overrun sends a localized TR message and emergency cue to the driver; the sidebar and BossBar retain the ATP EB indication.
- SR target reachability is checked over the saved graph independently of the short rolling MA window. Targets beyond the initial window can be approved, but each issued grant remains bounded by locally verified occupancy and switch state. Graph gaps, unknown points and conflicts still prevent unsafe extension. Added SR route regression tests and localized SR/Trip command results in STF and PCC.
- This is experimental game software, not certified ATP, certified interlocking or ETCS compliance. A successful local build or a browser display is not a safety acceptance test.

本轮仅手动列车进入运行模式状态机；影子通道继续只读，自动列车保持 STF 内置伪 ATO 行为。强制保护需管理员在停车时显式启用，并经过实服验证。四插件及中英法日荷文档同步更新，不清空轨道图和占用账本。

## Breaking STA v5 / 破坏性协议升级 - 2026-09-23

Release set: **STF 3.0.0 / STCS 3.0.0 / STA 1.0.0 / SkyPCC 1.0.0**. Not yet deployed or published.

- Moved v2/v4 service contracts to v5; version-first decoding rejects legacy message IDs. PCC uses `/api/v5/` only.
- Added conceptual numbering: allocated MA Message 1003 / Packet 1015; telemetry Message 1136. Private removal/graph/status messages use 2001/2002/2003. Removal never grants clearance.
- Updated consumers, browser fixtures and multilingual manuals. STF standalone support remains intact.
- Added `/stcs integrity [train-name|uuid]`, an admin-only, in-memory observer of member distribution and inferred node passages. It does not clear occupancy, prove tail clearance or grant MA. [Diagnostic scope](doc/NODE-INTEGRITY.md).
- Validation: 63 Java test entry points, PCC JavaScript and two browser regression suites passed. Live-server validation of this release remains outstanding.
- No persistent ledger schema change, ATP actuation or FS activation. [Migration guide](doc/STA-V5-MIGRATION.md).

四插件配套升级，不兼容旧组合；编号仅为致敬，不代表 ETCS 编码或标准符合性。新增节点通过只读诊断，不释放占用。保留轨道图和占用账本，不通过清空数据升级。以下为先前版本的开发记录，其功能继续保留。

## STF 2.1.5 Baseline / 先前基线 — 2026-09-23

Component versions / 组件版本：STF **2.1.5**, STCS **2.2.1**, STA **0.8.1**, SkyPCC **0.8.1**.
Only STF's version changed in that batch. STCS source fixes below retained its existing version.
该批仅提升 STF 版本号；下述 STCS 源码修正当时沿用原版本。

### STF

- Added read-only onboard shadow speed curves using valid MA/EoA and vehicle B7 parameters. Defaults: stop 1 m before EoA and approach at no more than 5 km/h within 5 m, decreasing continuously to zero.
  新增有效 MA/EoA 与 B7 参数驱动的只读影子速度曲线；默认 EoA 前 1 m 零速、最后 5 m 最高 5 km/h，连续降至零。
- Replaced theoretical maximum-speed telemetry-age compensation with a source/current measured-speed estimate. This removes fictitious stationary travel and the associated low-speed refresh jumps. Actual MA shortening is not delayed or visually smoothed.
  遥测延迟改用源采样及当前实测速度估算，修正静止时虚构位移和低速刷新跳变；真实 MA 缩短仍立即反映。
- Displayed the same shadow limit on sidebar line two and the driver-only MA BossBar. Added Chinese, English, French and Japanese BossBar labels. Invalid inputs show `--`.
  计分板第二行与司机专用 BossBar 显示同一影子限速，支持中英法日；失效时显示 `--`。
- Added configurable near-limit audio with hysteresis and cooldown under `shadow-atp.warning` and `ma-sounds.near-limit`. Existing MA sounds are retained.
  新增带滞回与冷却的接近限速音效，配置位于上述两节，保留原有 MA 音效。
- Preserved optional STA integration. Session, lease, graph revision, source sequence, freshness and reversal checks guard curve inputs.
  保持 STA 可选集成，并检查会话、驾驶权、图版本、源序号、时效及换向。

### STCS And Tools / STCS 与工具

- Preserved the actual downstream stop reason when a short common exit is blocked by unknown/pending points, graph gaps or lookahead limits; a real track end still reports insufficient exit capacity. Added common-exit regression coverage.
  短 common 出口遇到未知/待处理道岔、图缺口或前视边界时保留真实原因；真实线路尽头仍可报出口容量不足。新增出口回归测试。
- Added read-only Python ledger inspection for matching RailGraph and shadow-occupancy files, including spatial/legacy resources, owner UUIDs, saved observations and unmapped evidence.
  Python 测试台新增只读账本核对，显示空间/旧整边资源、所属 UUID、历史观测和未映射证据。
- Documented the existing console-only, backed-up and audited manual clearance command. No automatic deletion, member reassignment cleanup or live ledger editor was added. The reported stale identity remains an investigation lead, not a proven automatic fix.
  补充现有控制台人工解除的备份与审计流程；没有新增自动删除、车厢归组清理或在线账本编辑器。旧身份残留仍是调查线索，不宣称自动修复。

### Validation And Limits / 验证与边界

- Local build: four plugins, 62 Java test entry points passed. Python: 60 discovered, 57 passed, 3 skipped. The owner reported a successful server test; this is not an exhaustive safety validation. No new PCC browser validation was run for this batch.
  本地构建四插件，62 个 Java 测试入口通过；Python 60 项中 57 通过、3 跳过。服主反馈测试通过，不代表完整安全验收；本批未新增 PCC 浏览器验收。
- Curves remain experimental, level-track, display-only estimates. No automatic ATP brake execution, FS activation or PCC curve publication. `shadow-atp.enforcement-enabled: true` is rejected. No ETCS/SUBSET-026 compliance claim.
  曲线仍是实验性平坡显示估算，没有自动 ATP 制动、FS 启用或 PCC 曲线报文；执行开关 true 被拒绝，不声称符合 ETCS/SUBSET-026。
- Existing configurations use defaults for omitted keys. Stop trains before `/st reload`. Do not clear occupancy merely because a chunk is unloaded or a resource fails to match an unrelated graph.
  旧配置缺项使用默认值，停车后重载；区块未加载或轨道图不配套，不是删除占用的依据。

[Curve details / 曲线说明](doc/SHADOW-ATP.md) · [Ledger workflow / 账本流程](../STCS/tools/testbench/OCCUPANCY-AUDIT.md)

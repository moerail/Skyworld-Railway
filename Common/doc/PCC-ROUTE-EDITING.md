# PCC统一图层与SAL运营线路编辑

**[新增／修改 · 2026-11-24]** 配套STA2.7.0、SAL0.5.0、PCC2.4.1、STF5.0.2、STCS4.3.5。
STCS4.3.4的默认静默复核开关继续保留。本功能不改变MA、ATP或物理执行权。

## 1．一张地图，独立图层

~~工务地图与运营地图分别使用画布和摄像机，切换后隐藏另一张地图。~~

现使用同一个RailGraph SVG、坐标和缩放视野，勾选显示：

- 工务设备与里程：应答器、道岔、原点、终点及标签。
- ATO-Lite设备：已登记station、depot、awake、速度和message牌。
- 运营线路：已保存的线路显示；选中后加粗、高亮并显示方向箭头。
- 实时列车与MA：列车、MA、占用与预约显示。隐藏图层不停止计算或改变许可。

轨道底图一直保留。车辆段下拉选择继续高亮全部登记停车位并显示现有统计。
点击应答器、道岔或车站可关联查看线路；多条线路共用时提示选择，不擅自改线。
编辑草稿未闭环或有歧义时用虚线显示意向连接，不能把它当成已证实的轨道连通。

## 2．进入编辑

1. 先在游戏中登记好RailGraph与SAL设备，尤其是depot。
2. 点击「进入运营线路编辑模式」，输入与转岔相同的控制Token。
3. 选择线路，点击「编辑所选线路」；或填写新线路名、至少选择一个车辆段，再「创建草稿」。
4. 点击应答器、道岔、轨道段或本线路station添加访问。图边引用是有方向的；它明确限制选定分支，之后仍按RailGraph验证，不按当前道岔开向选择。
5. 在有序列表中上移、下移、删除访问；station可设置此次停站秒数及停站后折返。同一站可重复访问。
6. 保存草稿→验证草稿→发布新版本。这三个动作分别进行。

Token只在编辑期间的页面内存中使用，不写入localStorage或URL，退出或卸载页面时清除。
每次POST仍验证Token、控制开关、请求来源及既有HTTPS策略。没有鉴权的只读查看不能执行编辑。

第一阶段**不创建网页虚拟设备、不改写游戏牌子**。station仍遵守第四行的线路归属；其他线路的station不能直接拿来共享。
新建另一名称的线路，需要先保存空草稿并退出编辑，在游戏中登记归属正确的station，再关闭游戏编辑会话、返回PCC继续编线。
property/message/awake仍使用现有牌子的实际配置；PCC不把这些牌子当成可行驶的拓扑节点。

## 3．草稿、发布版与运行任务

| 层次 | 行为 |
| --- | --- |
| 会话提案 | 页面点选后的UPDATE，只更新当前编辑提案，不等于保存草稿 |
| 已保存草稿 | SAVE递增草稿revision并取消其验证状态；允许未闭环，但必须绑定车辆段且引用现有设备 |
| 验证 | 验证有向路径、首尾停车点、闭环、折返及依赖；保存STA v9边依赖摘要 |
| 当前发布版 | PUBLISH再次检查当前依赖并生成新的publishedVersion；不会覆盖正在运行的任务快照 |
| 运行任务 | 发车时固定发布版、路径点和停站参数，持久保存自己的routeSnapshot |

发布版保存当前版本，旧任务保存自己需要的旧版；本阶段不提供任意历史版本回滚界面。
运营快照列车新增可选routeVersion，供车辆段列表区分任务使用的版本，旧构造器与既有字段保留。
修改草稿、停用线路不改变已有任务。新叫车必须使用有效且未停用的发布版，并检查指定车辆段确实在绑定列表内。

验证基于STCS依赖摘要：无关图变化不使验证失效，受影响或无法确认的依赖不能被直接发布。
实际拆轨、设备失效、定位/TIMS/MA故障仍会让运行任务保持或等待恢复，不能用“固定旧版”绕过物理变化。

## 4．车辆段约束

新建与保存的每条线路至少绑定一个已有车辆段，可以绑定多个；车辆归属仍是车辆段而不是固定库位。
`awake`牌第三行车辆段、第四行线路的组合必须在**当前发布版**允许的集合内。
没有可用列车、库位、空间或合法路径时保持原有拒绝/等待规则，不创建车辆、不强行出清。
车辆段仍被草稿或当前发布版绑定时，不能在游戏中删除它的最后一个停车位；运行任务使用的停车设备也不能被直接拆改。

## 5．两侧冲突冻结

SAL是唯一写入者；游戏命令和PCC共用RouteWorkspace。
每个编辑者有来源、actor、随机会话Token和5分钟租期；PCC约每20秒续租。
同一线路出现另一个编辑者，或提交基于过期草稿版本时，进入冲突：

1. 双方均冻结，拒绝UPDATE、SAVE、VALIDATE、PUBLISH、停用、删除和普通关闭。
2. 最后成功保存的草稿、发布版和已有任务保留；不自动覆盖、不倒退已经提交成功的版本。
3. 双方已提交到会话的提案仅作为冲突恢复记录持久保存，**不会成为已保存草稿或发布版**。
4. 重启、掉线、关闭浏览器、租期到期都不能自动解除已确认的冲突。

PCC显示双方提案供对照。管理员可选择放弃双方待存修改，或选择一方提案交给当前解消者继续编辑。
旧双方会话失效，选择提案本身不保存、不发布，仍需人工SAVE和验证发布。
也可以在解消后手工整理合并结果；没有自动合并。

非冲突会话重启后清除，已保存草稿和发布版保留。刷新页面会建立新的页面身份，原会话仍活动时可能触发冲突，应按上述流程人工处理。

## 6．游戏命令

```text
/sal route create Line1 maintown
/sal route depot Line1 maintown,otherdepot
/sal route bind Line1
/sal route choose 1
/sal route bind Line1 edge:<图边ID>
/sal route direction Line1 east
/sal route list Line1
/sal route remove Line1 3
/sal route preview Line1
/sal route validate Line1
/sal route publish Line1
/sal route close Line1
/sal route resolve Line1 DISCARD
/sal route resolve Line1 <actor>
/sal route retire Line1
/sal route delete Line1
```

只有一个车辆段时create可省略车辆段参数；否则必须显式指定。先建depot，再建运营线路。
游戏中第一次修改会开启游戏编辑会话，每条正常修改命令保存草稿；publish成功或close释放该侧会话。
游戏编辑完成后应close或publish，再进入PCC；仍活动的另一侧会触发冻结，不会被网页偷偷抢占。
多设备choose仍60秒有效，并检查草稿版本和图版本，防止旧选择覆盖新编辑。
list和preview只读，不抢占编辑权。实体牌子拆改另外保留运行任务引用检查。

## 7．STA v10和HTTP

新增`STA api.v10.RouteEditingService`，结构版本1。它是运营计划契约，不是新的行车许可或ATO标准报文；没有为此分配Message/Packet编号。

- Definition：name、dx/dz、有序Visit(deviceId、reverse、可选dwellSeconds)、depots。
- Catalog：来源会话、图revision、时间、线路版本、现有设备、车辆段，以及单调workspaceRevision。
- Request：requestId、action、line、actor、source、session、expectedRevision、definition、decision。
- Reply：结果、原因、会话、线路状态、预览几何、closed及workspaceRevision。

PCC通过异步网关调用SAL；HTTP线程只读取缓存或排队操作，不操作实体，也不读写SAL文件。
`GET /api/v10/routes`返回只读目录；`POST /api/v10/routes`鉴权后提交AUTH/CREATE/BEGIN/UPDATE/SAVE/VALIDATE/PUBLISH/HEARTBEAT/CANCEL/RESOLVE/RETIRE/DELETE。
HTTP来源固定为PCC，actor由网关加PCC前缀，不能冒充游戏管理员来源。
请求ID带有界幂等缓存，重复轮询不重放修改；请求体上限64KiB，队列有界。
前端用workspaceRevision拒绝较旧的目录缓存回退，防止刚保存后读到旧GET造成假冲突。

STCS继续独占RailGraph、资源和MA裁定。SAL推进任务，STA传递请求，STF在实体/区域所属线程执行。
PCC关页不影响已发布线路运行。上下行优先、单线整段预约、多段通用折返和Python计算守护进程仍只是设计，未在此实现。

## 8．迁移与验证

旧SAL state.json、RailGraph、编组和占用账本全部保留。旧发布线路导入为发布版1，依据已有awake归属或唯一车辆段补绑定；无法明确绑定的旧线路保留草稿，人工绑定后才能发布叫车。
旧任务复制原线路到自己的快照，启动后仍按原规则RECOVERY_REQUIRED等待管理员检查，不自动续跑。
升级先备份并整套替换五插件，完整重启；降级必须恢复配套数据备份。

回归覆盖不可变契约与JSON、草稿/发布版隔离、旧任务定义固定、双方冻结、跨重启恢复、人工接管提案、失效会话、请求幂等、无车辆段拒绝、有任务不能删除、图边消歧、HTTP异步网关和Token拒绝。
浏览器覆盖统一画布/勾选层、相同摄像机、Token、设备点选、未闭环虚线、保存/验证/发布、缓存回退围栏、冲突冻结与解消、数据过期和四语言。
实际多人同时点选、复杂场站行车与故障注入仍需现场验收；页面测试不能代替实车运行。


### 新增／修正：PCC 2.4.1 地图显示

文字缩放旁的 H− / H+ 调整选中高亮大小，点击百分比恢复 100%；色块调整颜色。大小和颜色保存在本浏览器，高亮不会因地图放大变粗。工务设备下可单独开关 Balise、END、ORIGIN 与道岔，标记、名称和方向箭头同步隐藏；总开关全选或全关，半选表示只显示部分类型。此筛选只影响显示。

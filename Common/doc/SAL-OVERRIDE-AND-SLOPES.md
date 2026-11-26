# SAL管理员脱困与坡道定位

**[新增／修正 · 2026-11-26 · SAL0.7.5 / PCC2.6.4]** 轨道坐标和方向统一取自同一份STF遥测，避免转弯时混用不同帧。定位暂态立即制动、默认复核2秒；节点容差0.75格、投影误差仍默认1.5格，均可有限配置。两份独立观测确认不同分支则提前恢复；不新建或扩大MA。原始故障持久保留并在PCC事件正文与/sal status直接显示。 [详细契约](SAL-LOCALISATION-TOLERANCE.md)

2026-11-26。配套STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.5 / PCC2.6.4。

## 管理员退出自动驾驶

```text
/sal override test2
/sal status
```

也接受完整列车UUID。要求skyatolite.admin；控制台可用。名称不唯一时必须用UUID。普通司机的/sal release仍只处理回库待命车，不因本功能获得运行任务的控制权限。

流程：保存手动保留状态→保持制动→确认完整新鲜车身观测和零速→经STA请求STCS释放MA→确认执行许可已撤销→经STA请求STF移交手动→等控制权释放的观测确认→移除SAL任务。运行任务结束，不继续载客或自动回库。管理员在线时收到完成提示，控制台记录管理员标识和列车UUID。未完成时/sal status显示等待阶段和原因。

旧虚拟司机会话因重启消失时，不伪造原驾驶权。使用现有OperationalAuthorityService保护通道变更撤权，确认STCS快照有效、新鲜、没有该车可执行MA或待批SR后才能交还手动。TIMS阻止普通MA释放时同样使用这个撤权通道；可执行许可被撤销，但不确定占用和相应锁闭保留。STCS不可用或确认失败则等待。

STF核对原owner，只有停车待命允许空owner；必须确认整列停稳。手动移交不确认TIMS、不解除Trip。STF按原有释放流程清除旧车载许可，TR/PT状态保留，TIMS监视器和车身账本不清。管理员命令不会授予新的MA。

完成后坐到驾驶端：

```text
/st drive
```

按正常流程处理仍存在的TIMS／TR／PT和驾驶方向；不能因override完成就认为列车已获准开动。SAL记录手动保留，释放驾驶权或经过depot不会马上重新纳管。恢复SAL仍走depot停靠、随机六位码确认、司机/st release的明确移交流程。

无SAL任务但处于PARKED的列车，也可由管理员override为手动；该停车状态的MA本应已结束。无任务的普通手动车不能通过此命令夺取他人驾驶权。移动中只保持制动等待，旧状态／缺失成员不视为停稳。

override意图和已确认撤权阶段持久化。重启不会重新发车；已确认撤权可继续手动移交，未确认的旧会话须重新取得STCS确认。已有占用账本不删除，不重置。

## STA边界

AutomaticTrainService新增handoffToManual(train,owner,administrator)，保留缺省UNSUPPORTED以兼容旧提供者。带Header入口沿用既有RELEASE Packet，不引入新的真实ETCS/ATO编号。调用方SAL负责管理员鉴权和STCS撤权确认，STF负责车辆owner、停车证据和实体线程上的控制移交。它不是任意清锁或替换驾驶员的公共入口。

## 坡道修正

旧SAL将实体x/y/z向下取整传给RailQuery。该契约要求的是实际轨道方块坐标。坡道上矿车实体可能跨入另一整数高度，floor(entityY)不等于railY，导致STA查询空、SAL误判任务偏离。

新版复用现有STA v5 TelemetryService中STF上报的railX/railY/railZ。核对生产者会话、列车ID、世界、编组数量和1秒新鲜度，再查询STCS，随后用实体连续位置投影计算进度。不在SAL访问区块或操作实体，不扩大1.5格定位误差，不把未知位置当作有效路径。

坡顶／边界相邻边选择规则保留。缺失观测、未来／过期数据、图版本错误、真实离轨仍拒绝。已有生产日志只能表明进入了偏离恢复；本轮离线回归复现并修正了一个能导致坡道误判的代码缺陷，不声称已完成该生产坡道实车验收。

## 生产测试

1. 配套替换STA、STF、SAL，保留数据；STCS与PCC版本不变。本轮不代操作生产，也不启动本地测试服。
2. 卡住的test2执行/sal override test2，观察/sal status直到完成。确认PCC仍保留真实车身占用，然后正常/st drive。
3. 未完成时记录阶段；不要删除账本来完成override。TIMS／Trip保持应仍存在，按正常驾驶规程处理。
4. 重新明确纳管后低速经过同一坡道，分别测试上坡、下坡和坡顶。记录时间、列车UUID、线路发布版；若再次恢复，保留/sal status里的具体定位原因。
5. 验证行驶中override先停车，MA未确认释放前不交控制；手动保留不会自动重捕获。

[使用手册](../../SkyATO-Lite/MANUAL.zh.md) · [生产测试](../../SkyATO-Lite/TEST.zh.md) · [English](SAL-OVERRIDE-AND-SLOPES.en.md) · [验证记录](../../VALIDATION.md)

# SkyTrainFolia 4.0.6：Shiroha 26.2 / 26.3 双版本适配

日期：2026-09-29。本候选版将两版显示包适配器打入同一个 SkyTrainFolia JAR，启动时自动选择。当前配套源码版本为 STCS 4.0.4、STA 2.1.1、SkyPCC 2.0.1。

## 产物

- `artifacts/SkyTrainFolia-4.0.6.jar`：本地构建输出的单个 JAR，内含 `TrainDisplayPacketAdapter_26_2` 与 `TrainDisplayPacketAdapter_26_3`。
- 启动时按 `Bukkit.getMinecraftVersion()` 选择适配器；其它版本使用原版显示同步。未选中的版本实现不会被加载。
- 本次协议适配未改动 STA、STCS、SkyPCC 与 shared 的核心逻辑；它们的当前版本更新随套件一并纳入。

## 隔离验证

| 核心 | 构建与测试 | 包装后的 SkyTrainFolia JAR 协议测试 |
| --- | --- | --- |
| Shiroha 26.2 | 四插件编译通过；80 个 Java 测试入口通过 | 通过 |
| Shiroha 26.3 | 四插件编译通过；80 个 Java 测试入口通过 | 通过 |

26.3 协议测试覆盖真实报文对象的整列 Bundle、`PositionPath` 绝对基准、`VecDelta` 相对位移、原版包抑制和回交、实体移除、旧/新矿车分支及长序列精度。26.2 保留原协议测试。两版侧栏和拉杆所调用的关键 NMS 方法签名一致；通用测试在两版依赖下均通过。

构建时 JDK 25.0.4 在关闭服务端依赖 JAR 的 ZipFileSystem 时打印 `AccessDeniedException` 清理告警，但编译退出码为 0，产物及测试均通过。

## 尚待实机验收

尚未把本次产物复制到测试服插件目录，也未做真实客户端乘车、整列显示、区域边界移动、驾驶台侧栏和拉杆红石输出验收。上线前应在 26.2 与 26.3 测试服完成这些检查。

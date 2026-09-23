# STA v5: Breaking Protocol Migration

## Release Set / 版本组合

STF 3.0.0, STCS 3.0.0, SkyworldTrainAPI 1.0.0, SkyPCC 1.0.0.
Plugin versions and protocol versions are separate: STA 1.0.0 provides protocol v5.

插件版本不等于协议版本。STA 1.0.0 提供 v5 协议；四个组件需要配套更新。STF 单独安装仍受支持。

## Identifiers / 编号

| Namespace | ID | Meaning | Reference |
| --- | --- | --- | --- |
| Message | 1003 | Allocated shadow MA | ETCS Message 3 + 1000, conceptual only |
| Packet | 1015 | MA payload identifier | ETCS Packet 15 + 1000, conceptual only |
| Message | 1136 | TELEMETRY_REPORT | ETCS Message 136 + 1000, conceptual only |
| Message | 2001 | TRAIN_REMOVED | Private STA lifecycle event; NOT clearance |
| Message | 2002 | TRACK_REPORT | Private STA graph localisation |
| Message | 2003 | AUTHORITY_STATUS | Private STA waiting/inactive state, no MA packet |
| Packet | 2001 | Physical observation | Private STA payload |
| Packet | 2002 | Graph position | Private STA payload |

Message and Packet have independent namespaces. The same number in different namespaces is not a collision. Only the three explicitly referenced identifiers are an ETCS homage; the private 2000-series is not an ETCS assignment.

Message 与 Packet 是独立命名空间。上述三个致敬编号只参考概念，不复制标准字段、二进制编码、单位、时序或安全保证；2000 系列为 STA 自定义编号。

`StaMessage` handles the three telemetry/lifecycle kinds, not every service in the suite. Its JSON header requires `M_VERSION=5` before interpreting `NID_MESSAGE`. Old Message 1003 meant removal; it is deliberately rejected rather than interpreted as new MA. MA travels through `ShadowAuthorityService.Snapshot(version=5)`: each authority carries `NID_MESSAGE` and `NID_PACKET`. An allocated authority has 1003/1015; WAITING/INACTIVE has 2003/null. This is a Java/JSON application schema, not an ETCS packet encoder.

遥测/生命周期由 `StaMessage` 编解码，MA 仍由独立服务快照承载。MA 条目字段是私有 Java/JSON 数据，不是可发送给真实 ETCS 设备的报文。

## API and HTTP / 接口

- Telemetry, graph, tracking, driver desk, authority and switch contracts now reside in `net.skyworld.sta.api.v5`. Former v2/v4 classes and services are removed; rebuild extensions against the new STA JAR.
- Supporting v1 DTOs/services and v3 member-observation/event contracts remain where unchanged. Their presence does not make old plugin binaries compatible.
- PCC endpoints are `/api/v5/graph`, `/trains`, `/messages`, `/railway-events`, `/shadow-ma`, `/config`, `/events` and `/switch`, each under `/api/v5`. Old routes are removed.
- PCC train snapshots require `schemaVersion=5`; shadow snapshots require `version=5`. The bundled UI rejects incompatible snapshots and invalid MA identifiers.
- STF core remains STA-independent. Integration types stay inside optional STA adapters.

旧 v2/v4 服务和旧 HTTP 路由不再保留兼容别名；第三方扩展需重新编译并更新 URL。保留的 v1 DTO/v3 成员观测契约不是旧整套插件的兼容层。

## Upgrade / 升级

1. Stop the server and back up plugin JARs, configuration, RailGraph and all occupancy ledgers.
2. Replace all installed suite components with this release set. Remove the superseded JARs from the plugin directory; do not leave two versions installed.
3. Keep configuration and persistent data. This change does not change RailGraph or occupancy file schemas. Never clear a ledger merely to complete an upgrade.
4. Restart fully; do not use hot reload. Hard-refresh PCC and update external consumers to v5.
5. Verify versions, driver acquisition, MA demand/release, shadow HMI, PCC status/events and turnout requests on a test server first. Verify retained unknown occupancy remains blocked after restart.

先停服备份，再配套替换 JAR、完整重启与刷新网页；保留所有账本与轨道图。先在测试服验证版本、驾驶权、MA 申请/释放、HMI、PCC、道岔以及重启后的保留占用。回退时使用升级前的配套 JAR 与备份，不混装。

## Scope / 边界

No real ATP braking, FS activation or certified interlocking is introduced. Shadow MA and speed curves remain non-executable. Train removal does not prove that a track is clear; unknown/unloaded states remain uncertain. The node-passage integrity monitor is diagnostic only and does not release reservations or prove complete detection. This is preparation for M3 development, not M3 acceptance.

本次不开放真实 ATP 制动或 FS，不宣称符合 SUBSET-026。列车删除不证明区段空闲，节点通过诊断不自动解除占用；M3 仍须另行实现与验收。

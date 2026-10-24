# SkyRail Suite 3.0.0: STA v5 Protocol Transition

## Matching Components / 配套组件

| Plugin | Version |
| --- | --- |
| SkyTrainFolia | 3.0.0 |
| STCS | 3.0.0 |
| SkyworldTrainAPI | 1.0.0 |
| SkyPCC | 1.0.0 |

## Breaking Changes / 非兼容变更

- Telemetry, graph, driver desk, authority and switch service contracts move from v2/v4 to `net.skyworld.sta.api.v5`. External Java consumers must rebuild.
- PCC HTTP/SSE routes move to `/api/v5/`. Old routes and old message versions are not supported. Refresh the browser after upgrading.
- Conceptual numbering: allocated shadow MA Message 1003 / Packet 1015; telemetry Message 1136. Private lifecycle removal, graph reports and waiting/inactive authority status use Message 2001, 2002 and 2003 respectively.
- Message and Packet namespaces are independent. These identifiers are an homage, not ETCS encoding or a SUBSET-026 compliance claim. Train removal is not proof of track clearance.

遥测、图、驾驶台、MA 和道岔契约统一至 v5，PCC 改用 `/api/v5/`。旧插件组合、旧路由和旧消息版本不兼容；第三方 Java 扩展需重新编译。编号仅为概念致敬，不代表 ETCS 兼容。删除列车不等于占用出清。

## Diagnostics / 诊断

`/stcs integrity [train-name|uuid]` (`stcs.admin`) reports member distribution and inferred node passages. It is read-only and in-memory: it neither clears occupancy nor proves a complete axle-counter event chain. A restart resets the diagnostic baseline, not the durable ledger. Stationary trains have no occupancy-expiry timer.

新增管理员节点完整度诊断，显示成员跨边分布与推断通过。它不清占用、不批准 MA，不是完整虚拟计轴器；重启不清账本，停车不触发占用超时释放。

## Upgrade / 升级

1. Stop the server and back up JARs, configuration, RailGraph and occupancy ledgers.
2. Replace the installed suite components with the four matching versions above. Remove superseded JARs from the plugin directory. STF can still be installed alone without STA.
3. Keep existing RailGraph and occupancy data; their on-disk schemas are unchanged by this release.
4. Restart fully, refresh PCC and update external clients to v5. Test driver acquisition, MA demand/release, retained occupancy and turnout requests before production use.

停服备份，配套替换已安装组件，不混装旧 JAR。保留配置、轨道图和占用账本，完整重启并刷新 PCC。先在测试服验证驾驶权、MA 申请/释放、重启保留占用及道岔操作。不要删除账本来完成升级。

## Validation and Limits / 验证与边界

- Four JARs built; 63 Java test entry points passed, including standalone STF reflection/loading and STA adapter integration.
- PCC JavaScript tests and four-language/shadow browser regressions passed, including old-snapshot and wrong-MA-ID rejection.
- This release still requires live-server validation. It prepares interfaces for M3; it does not complete M3.
- MA/EoA and onboard speed curves remain experimental and shadow-only. No automatic ATP braking, FS activation or certified interlocking. Unknown/unloaded infrastructure must not be treated as clear.

已通过本地构建及回归，仍需实服验收。M3 尚未完成，不开放 FS 或自动 ATP 制动，不声称铁路安全认证。

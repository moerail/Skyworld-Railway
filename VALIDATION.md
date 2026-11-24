# SkyRail Suite 2026.11.24 — validation / 验证记录

## 2026.11.24 — PCC 2.4.1 display patch / 显示补丁

- Browser regression passed: all four infrastructure child filters, partial parent state, marker/label/arrow visibility, no native SVG outline, exact selected-edge geometry, highlight size/colour, fixed stroke width after zoom, local storage, and existing route-editing regressions.
- 430 four-language keys and edge-line geometry checks passed. Live HTTP assets matched the source after deployment and restart. No Java logic changed for this display patch; the111-entry-point per-core run below belongs to the preceding feature implementation.
- Documentation update: seven language manuals, five driver guides and five module entry points. Historical manual snapshots stay unchanged. Local links are checked by `node Common/validate-docs.cjs`.


## 2026.11.24 — unified map and route editor / 统一地图与线路编辑

STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.0.

- Java: **111 test entry points on each of Shiroha26.2 and26.3**. New immutable editing contract/JSON, persistent draft/publication separation, conflict freeze/restart/manual adoption, revision fencing, idempotency, bound-depot requirement, active-task deletion rejection, explicit directed-edge branch selection and async HTTP gateway tests.
- Browser: one SVG/shared camera, independent layers, wrong/correct token, graph/SAL picking, dashed incomplete proposal, explicit save/validate/publish, preserved publication, stale-catalog fencing, two-sided freeze/manual resolution, stale data and language switching. **422 four-language keys** and edge-geometry checks pass.
- Final Java binaries use the tested sources; final PCC web assets are packaged after browser verification and checked against source hashes.

2026-11-24已备份部署并重启本地26.2测试服。真实接口检查通过：错误Token拒绝、正确Token登录、临时草稿创建、提案与保存隔离、未闭环不能发布；游戏控制台线路命令与PCC会话冲突后双方拒绝保存/发布；再次完整重启后冻结仍在，人工解消后旧会话失效，再删除临时线路。

最终真实浏览器核对同一SVG、四个图层、line1方向、maintown两个库位及原Token登录/退出，无JavaScript异常。原line1保留发布版1并绑定maintown。30条正式账本的占用/预约集合和旧账本资源逐项一致，没有清空账本。临时线路已清理，最终测试服保持运行。

这些验证不代表复杂场站、多列车实车行驶、真实多人设备改动或所有故障注入已经验收。

[中文操作与契约](Common/doc/PCC-ROUTE-EDITING.md) · [English](Common/doc/PCC-ROUTE-EDITING.en.md)

## 2026.11.21 historical baseline / 以下为历史基线

## Final binaries / 最终二进制

STA2.6.1 · STF5.0.1 · STCS4.3.3 · SAL0.4.1 · PCC2.3.1.

This patch adds themed command presentation, the full `[SkyATO-Lite]` prefix, grouped SAL help, SAL in version output and corrected default-FS/executable-ATP wording. It does not change physical execution, authority decisions, driving leases or occupancy release rules.

本补丁更新提示配色、SAL完整前缀与分组帮助、五插件版本列表，以及默认FS/可执行ATP说明；不改变实体执行、许可裁定、驾驶权和占用出清规则。

## Automated checks / 自动检查

- Java compilation and assertions: **108 test entry points on each of Shiroha26.2 and26.3**. Includes both STF protocol adapters, five-language version output, grey/yellow shared command themes, blue SAL prefix, error colouring, driver/admin help and rich-text handover prompts.
- PCC: operating-map browser regression, four-language text/placeholder checks (**363 keys**), edge geometry and JavaScript syntax checks.
- Distribution: seven current manuals and preserved historical editions; local file/anchor links and referenced images checked after extraction; five plugin hashes matched to the build manifest; all delivered files listed in MANIFEST.sha256.

## Existing live checks / 已有实服验证

2026-11-21: the final five-plugin set was deployed to the local26.2 test server and started successfully. Console checks of `/st version zh`, `/sal help` and `/stcs help 1 zh` confirmed SAL in the version list, corrected default-FS/ATP wording, the blue full `[SkyATO-Lite]` prefix, grey STF and yellow STCS help. PCC reports AVAILABLE, RailGraph97 and published line1. All30 formal-ledger records retained identical occupied/reserved sets; legacy occupancy resources also remained unchanged. This was command/startup verification, not a new physical driving or in-game click-through test.

2026-11-21最终组合已部署并启动26.2测试服，控制台确认五插件版本、FS/ATP提示和三套配色；PCC与line1正常，30条正式账本及旧占用资源逐项保留。未把控制台检查当作新的实车或玩家按钮验收。

2026-11-20: the local26.2 test server loaded the previous compatible set, and actual HTTP/SSE/browser checks displayed published `line1` and the two registered `maintown` berths. Restart preserved all30 formal-ledger entries and their occupied/reserved resource sets, plus the legacy occupancy resources. No whole-ledger reset was used.

Earlier26.3 validation completed one empty-train pickup→loop→depot. This is not proof that all layouts, simultaneous trains or passenger services work.

2026-11-20已验证26.2真实PCC显示、线路与库位，以及重启保留原有账本；此前26.3完成单列空车一圈。不能据此声称所有站场、多车、载客或本轮聊天按钮均已现场验收。

## Still requires local acceptance / 仍需现场验收

Reverse-facing dispatch, depot turnout movements, first-station reuse, competing berths, whole-consist clearance/reversal, unloaded-region transitions, empty-server pause settings, Title replacement, genuine TIMS failures, two-step handover and train-specific removal/occupancy release.

反向叫车、出入库道岔、首站复用、库位竞争、全列清岔/折返、跨卸载区、空服暂停、Title、真实TIMS故障、双重移交确认及删车对应占用释放仍需收件人实线验收。

## Not shipped as runtime / 未实现的设计

The up/down direction rule, atomic whole-single-track-section reservation, general intermediate multi-reversal search and a Python planning daemon remain **design only**. Existing Python desktop/viewer tools are not that planning daemon.

[Design / 设计](Common/doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md) · [Manual / 手册](Common/README.zh.md)

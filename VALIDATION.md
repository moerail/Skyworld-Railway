# SkyRail Suite 2026.11.26 — validation / 验证记录

**[UPDATED · SAL 0.8.0]** Bounded intermediate-reversal pickup/return routing is shipped (up to4 reversals); unbounded shunting, direction-policy proposals and a Python planner remain separate design work. [Runtime contract](Common/doc/SAL-MANEUVERS-AND-HANDOVER.en.md).

## 2026-11-26 — SAL 0.8.0 / STF 5.1.4 handover, maneuvers and manual handles

- Separate consent-code issue and successful prompt delivery; retry failed delivery, refresh expiry and align stop tolerance with STF. Explicit two-step consent and physical commit gates remain.
- Shared bounded RailGraph planner for pickup/depot return: intermediate and initial reversals, actual rear as new leading end, multi-edge whole-body clearance, separate MA legs and confirmed release/reverse handshakes. No global deadlock solver or device-relocation feature.
- Preserve manually selected traction while chunks load; effective traction prohibition and protection braking remain. Existing TIMS, driver-loss, explicit release and Trip behavior is unchanged.
- Add maneuver geometry/state-machine and chunk-handle regressions; extend handover delivery/expiry checks. No test server startup or production operation.

## 2026-11-26 — SAL 0.7.7 / STCS 4.3.6 concurrent return traffic

- Retain selected return plans/centres during live throat traffic; stop treating other running SAL jobs as permanent access obstructions. Whole destination clearance, exclusive berth selection and stored/faulted/manual blockers remain.
- Automatic point alignment cannot enter the dispatcher handshake to withdraw a different train’s MA. Explicit PCC dispatcher and controlled self-reroute behavior retain their guards.
- Maintain established route heartbeats before fleet geometry/dispatch work. No change to the 1500ms watchdog, STA APIs, ATP or occupancy protection.
- Add two-automatic-train runtime conflict/continuation regression and split route/destination geometry checks. Production raw events were lost after restart; no claim of exhaustive production diagnosis, deployment or server startup.

## 2026-11-26 — STF 5.1.3 automatic cab handles

- Replace fixed P2/B4 automatic driving with configurable P4 departure and B6 service braking; taper traction near cruise, retain B7 final hold. Recompute the normal stop envelope with a reaction allowance.
- SAL/STA interfaces and STCS authority remain unchanged; existing ATP, vehicle physics, TIMS, Trip and distance-fence enforcement are retained. Manual driving is unaffected.
- Regression integrates every bundled vehicle profile for departure clearance and docking, plus handle bounds, cruise taper, ATP caps and invalid telemetry. No server operation or production deployment.

## 2026-11-26 — SAL0.7.6 return rechecks and route-heartbeat retention

- Keep accepted-route heartbeats while braking for berth checks, obstacles or replacement selection. No new path/authority is created during a hold.
- Classify target/slot/centre/space/body failures; retain targets for a default2s recheck. Persistent body uncertainty faults; real changes replan only while stopped. Geometry and whole-body margins remain strict.
- Detailed status, throttled issue logs, completion/last-return-issue logging and explicit dispatch-history label. Existing STF/STA/STCS/PCC versions unchanged.
- Regression uses the actual silent MA scheduler and1500ms watchdog, plus point-setting invariance, fit/unknown-body distinction and stop/recheck outcomes. No local server startup or production deployment.
- Final build passed125 Java test entry points on each of26.2 and26.3, including ReturnBerthGuardTest. PCC/STA/STCS/STF behavior and resources are unchanged in this patch; prior browser tests are not claimed as rerun. Documentation links and extracted distribution hashes are validated during packaging.


## 2026-11-26 — SAL0.7.5/PCC2.6.4 coherent localisation and bounded recheck

- Query STCS with railXYZ and motionXYZ from one telemetry frame, then continuously project actual position; do not mix an older rail anchor with a newer orthogonal tangent.
- Configurable finite1.5block projection /0.75block endpoint /2000ms recheck defaults. Brake throughout uncertainty, retain plan, renew accepted lease only. Independent wrong-branch observations beyond the node zone recover early; same observation rereads do not count twice. STCS/ATP remain authoritative.
- Persist original failure/time, show it in status/operating projection/PCC event text, add bounded context and one confirmed-recovery console notice. No new API control or protocol numbering.
- Regression: coherent curve frame, slope, transient recovery, timeout, repeated evidence, wrong branch, scaled graph, plan replacement, config limits, persistence and safe event text. No production operation or test-server startup.
- Final build passed124 Java test entry points on each of26.2 and26.3. PCC browser regression passed with visible original cause, context tooltip and HTML-safe rendering;476 four-language keys and edge-line checks passed. Source links and extracted package hashes are validated during packaging. The production route at693/70/-707 still requires user driving acceptance.


## 2026-11-26 — slope rail anchoring and administrative manual override

STA2.9.2 / STF5.1.2 / STCS4.3.6 / SAL0.7.4 / PCC2.6.3.

- Reproduce entity-floor versus actual rail-block mismatch on slopes. SAL consumes existing same-session fresh STF railXYZ telemetry through STA and keeps continuous projection/real-deviation checks.
- Admin /sal override by name/UUID: persist manual reservation, brake, confirm STCS withdrawal/inactive permission, STF handoff, observed completion. No ledger clearing or TIMS/TR/PT acknowledgement. Handle old leases after restart via existing protection-channel revocation; unavailable/active permission is not success.
- STA adds a default handoffToManual method on the existing RELEASE Packet; STF verifies stopped/owner and retains safety holds. SAL command remains admin-only.
- New regressions cover slope coordinate mismatch, telemetry freshness/identity/graph, override stop/owner/pending/failure/restart/permission proof, TIMS retention and ordering. No server deployment/startup or production acceptance.
- Final offline build passed123 Java test entry points on each of26.2 and26.3, including ManualOverridePolicyTest, ManualOverrideFlowTest and TrainLocalisationTest. PCC assets are unchanged; prior browser results below are not a new live run. Package link/hash validation runs on the extracted distribution.


## 2026-11-26 — SAL0.7.3 dispatch rejection diagnostics

- Explain eligibility, scope, forward/reverse attempts, ETA cap, unknown/stale/off-graph other-train envelopes, known-body obstacles and stale graph/computation results. Preserve reasons through pending requests and timeout history.
- Eligibility and obstacle predicates are unchanged; other-train TIMS hold alone is not a global obstacle. Diagnostic-only unfiltered path comparison never grants MA or becomes an executable plan.
- STA2.9.1/PCC2.6.3 unchanged; replace SAL only for this compatible set. No server startup/deployment or production root-cause claim.

Regression checks eligibility wording, bounded summaries, stale/unknown/off-graph blockers versus unrelated TIMS hold, ETA and restricted-scope reasons in the actual worker. Production test1/test2 being PARKED and TIMS CONFIRMED does not identify the remaining gate without current body/route-policy data.

Final offline build passed120 Java test entry points on each of26.2 and26.3. Both diagnostics regressions passed. PCC resources and STA contracts are unchanged in this patch; previous browser validation is recorded below, not claimed as rerun. Documentation links and extracted distribution hashes are checked during packaging.

## 2026-11-26 — shortest operational paths and confirmed track previews

- SAL0.7.2/PCC2.6.3 with STA2.9.1: shortest directed distance replaces strict multi-path rejection. Stable equal-cost choice, positive same-position loops, explicit-waypoint constraints and existing direction/transition/closure checks.
- Validation, task planning, entry rotation and previews use the same planner. Unfinished draft prefixes follow sampled rails; unknown connections remain estimated.
- Regression covers detours, ties with reordered graph arrays, explicit branch, positive loops, broken routes and bent partial-preview geometry. No production deployment or test-server startup.
- Final build passed119 Java entry points on each of26.2 and26.3. Updated PCC browser regression,474 translation keys and edge-line checks passed. Documentation links and extracted package hashes are validated during packaging.


## 2026.11.26 — directed validation diagnostics and grip sorting

STA2.9.1 / SAL0.7.1 / PCC2.6.2, STF5.1.1 / STCS4.3.5. Both cores passed119 Java entry points. New regression covers directed endpoint labels/IDs, immutable JSON contract, rejected validation and replay. Browser checks real mouse drag with no intermediate UPDATE, preserved full visits, keyboard reorder, Escape cancellation, touch-action policy, failing-pair highlighting/jump/clear, plus prior polling/mobile/auth/conflict behavior. All474 translation keys pass. Headless Edge touch injection failed to acknowledge/start a gesture; actual phone/tablet drag remains unverified, not a claimed pass. No server deployment, restart or production edit.

## 2026.11.26 — PCC2.6.1 editor refresh and real pointer regression

- Fixed narrow layouts clearing the editor's has-selection state on every train poll, despite an active SAL editor/depot panel.
- SAL devices and route paths are excluded from map pointer capture; noninteractive draft previews no longer cover station targets.
- Stable semantic rendering retains unchanged map/list DOM across telemetry timestamps. Catalog GET requests do not overlap; temporary failures retain local proposals and raw inputs while freshness checks disable writes.
- Browser regression uses real CDP mouse press/release held across multiple telemetry/catalog polls, checks narrow-screen visibility, unchanged DOM identities, typed dwell preservation through unavailable/recovered catalogs, existing publication/conflict fencing. This covers the actual input path that previous synthetic click tests missed.
- No test-server restart or production deployment. Offline build remains118 Java test entry points per core; PCC browser,467 translation keys and edge-line checks pass.


## 2026.11.26 — public stops, circular entry and shared fleet dispatch

STA2.9.0 / STF5.1.1 / STCS4.3.5 / SAL0.7.0 / PCC2.6.0.

- Final offline build passed **118 Java test entry points on each of Shiroha26.2 and26.3**. New tests exercise non-greedy matching, finite preference/cap, aging, stable visits/closure ambiguity, B-C-A-B rotation, schema migration/legacy-awake fencing, pure worker path scoring and commit position/direction/member fences.
- PCC fixture browser regression passed with public stops, policy persistence, copyable entry and dispatch queue/decision display, retaining token, conflict, layer and highlight checks. **467 four-language translation keys** pass.
- No local test-server deployment/restart and no production access. The previous local26.2 server was shut down normally at the user's request. Existing production L5-1/L4-1 cars were not inspected, modified, remanaged or dispatched by this build.
- This is offline algorithm/contract/browser regression, not physical driving acceptance, congestion benchmarking or fault-injection certification. ETA is approximate. Follow [the Chinese production test](SkyATO-Lite/TEST.zh.md) for real departures, reverse approaches, berth return, duplicate calls and optional cross-group fallback.
- [Chinese contract](Common/doc/SAL-SHARED-DISPATCH.md) · [English contract](Common/doc/SAL-SHARED-DISPATCH.en.md). All historical validation entries below describe earlier rounds, not new actions.

## 2026.11.25 — direction-gated default MA and relaxed manual consent

STF5.1.1 / SAL0.6.1 with STA2.8.0 / STCS4.3.5 / PCC2.5.0.

- Both26.2 and26.3 passed114 Java test entry points. Updated regressions cover neutral acquisition versus forward/reverse selection, existing protection holds, body-over-marker checks near both ends, adjacent-track rejection, random six-digit code shape, explicit request without the passive delay, code replacement, wrong driver/lease/code, movement/expiry and one-shot explicit-release fencing.
- The default request trigger is STF's manual-driver scheduler; STCS continues to arbitrate the resulting MA. SAL's relaxed manual consent captures the current stopped centre, retains the marker association and does not change automatic return parking geometry.
- Deployed and restarted the local26.2 test server. Live API remained AVAILABLE, graph97, line1 publication1; all30 formal-ledger occupied/reserved sets and signs/homes/route workspace matched the stopped-server backup. Console help confirmed both handover command forms. No live driver interaction was fabricated; in-game direction selection and partial-body consent still need player acceptance testing.
- Updated the standalone Chinese SAL manual, multilingual manuals and handover/centre-parking notes; local Markdown links validated.


## 2026.11.25 — centre-aligned multi-berth storage / 中心停车与同股道多库位

Compatible set: STA2.8.0 / STF5.1.0 / STCS4.3.5 / SAL0.6.0 / PCC2.5.0.

- **114 Java test entry points on each of Shiroha26.2 and26.3** passed. New coverage: immutable full-body feedback, front/middle/rear references, centre capture after reversing, stale or cross-world members, along-track scaled/curved storage spines, unknown and duplicate boundaries, single-ended deep-first selection, double-ended shortest approaches, centre clamping, oversized near berth with a larger usable alternative, obstruction before/after a target and another usable entrance.
- PCC browser regression passed for berth capacity, curved boundaries and planned-centre display, plus existing token editing, draft/publication separation, conflict freeze, infrastructure sublayers and configurable highlights. **452 four-language keys** passed.
- Deployed to the local26.2 server and restarted on2026-11-25. Live HTTP assets match source. Real browser checked two maintown berth capacities (L1:21blocks, L2:45blocks), both double-ended, visible boundaries and editor authentication/exit, with no JavaScript exceptions.
- All30 formal-ledger occupied/reserved sets matched the stopped-server backup. Depot signs, homes and route workspace were retained; line1 remains published version1. No active SAL jobs existed during this deployment. No ledger reset, automatic train relocation or route publication was performed.
- This is algorithm/contract/browser regression and real-server startup verification. It is **not** a new physical multi-train depot driving or all-fault-injection acceptance run. Old front-aligned parked trains require manual repositioning for centre capture.

[中文设计、配置与契约](Common/doc/SAL-CENTRE-STABLING.md) · [English design and contract](Common/doc/SAL-CENTRE-STABLING.en.md)


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

The up/down direction rule, atomic whole-single-track-section reservation, unbounded general shunting and a Python planning daemon remain **design only**. Existing Python desktop/viewer tools are not that planning daemon.

[Design / 设计](Common/doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md) · [Manual / 手册](Common/README.zh.md)

STF 5.1.3 automatic handles: full offline build passed 126 Java test entry points on Shiroha 26.2 and 126 on 26.3. Documentation validation: 100 Markdown files / 1058 local links, no errors. Log: Common/logs/automatic-handles-20261126.log. No server startup, deployment or production acceptance performed.

SAL 0.7.7 / STCS 4.3.6 concurrent returns: complete offline build passed 128 Java test entry points on Shiroha 26.2 and 128 on 26.3, including AutomaticTrafficConflictTest and ReturnTrafficTest. Documentation check: 102 Markdown files / 1084 local links, no errors. Log: Common/logs/return-traffic-final-20261126.log. No server operation or production acceptance performed.

SAL 0.8.0 / STF 5.1.4 final release: full offline build passed 131 Java test entry points on Shiroha 26.2 and 131 on 26.3. Includes ManeuverPlannerTest, ManeuverControlTest (post-reversal localisation failure/timeout), ChunkHandleRetentionTest and extended DepotHandoverTest. Source changes precede the final SAL compilation. Documentation: 104 Markdown files / 1111 local links, no errors. Log: Common/logs/maneuver-handover-release-20261126.log. No test server startup, production deployment or live acceptance performed.

GitHub source publication check (2026-11-26): no Java changes since the final 131/131 offline build. Re-ran PCC real-browser route editor regression, 476-key i18n/placeholder/storage checks and JavaScript syntax validation successfully. Corrected obsolete design-only descriptions and synchronized current version tables; generated binaries, local server data and credentials remain excluded from Git. Browser log: Common/logs/github-operating-map-20261126.log.

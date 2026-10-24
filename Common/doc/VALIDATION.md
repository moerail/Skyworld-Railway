# Migration Validation: 2026-09-14

## 2026-09-23 STA v5 Breaking Release

- Rebuilt STF 3.0.0, STCS 3.0.0, STA 1.0.0 and SkyPCC 1.0.0: 63 Java test entry points passed.
- Protocol tests verify MA 1003/1015, telemetry 1136, removal 2001, graph report 2002, rejection of old versions and refusal to decode old removal 1003 as a telemetry message.
- Standalone STF classloading/reflection and with-STA adapter tests passed.
- PCC edge-line and 265-key four-language tests passed; Edge browser test passed four languages, desktop/mobile, follow/selection, persistence and pending switch control.
- Shadow PCC browser regression passed MA colouring/EoA, invalid message ID rejection, old snapshot rejection, recovery, stale expiry, turnout confirmation and desktop/mobile themes. Updated its fixture to serve i18n assets and use the infrastructure inspector control.
- No deployment or real-server mixed-version/startup validation performed. Deprecation/native-access warnings remain from the existing server API/dependencies.
- Persistent RailGraph/occupancy formats are unchanged. Live M3/FS protection is not enabled by this release.

## Passed

### 2026-09-23 Follow-up (STF 2.1.5)

Four plugins rebuilt; 62 Java test entry points passed, including shadow-curve continuity,
stationary/constant-speed report refresh, HMI row ordering, four-language BossBar formatting,
near-limit audio hysteresis, input validity and no-STA classloading.
Python: 60 cases discovered, 57 passed, 3 optional cases skipped.
The server owner reported a successful test. No new live-server or PCC browser run was
performed by the coding agent; this does not establish FS safety or resolve every retained-occupancy cause.

### Historical Directory-Migration Run

- Four JARs rebuilt from source using JDK 25 and the prepared local Shiroha/Folia 26.2 dependency tree.
- 52 Java test entry points passed with assertions enabled, including the formerly separate MaSoundSettingsTest.
- PCC edge-line.cjs and i18n.cjs passed (265 language keys).
- PCC i18n-browser.cjs passed with headless Edge: desktop/mobile, four languages, theme, selection/follow, infrastructure inspector, expanded log and pending control.
- Python unittest discovery: 58 cases discovered, 55 passed, 3 optional real-graph regressions skipped because STCS_TEST_GRAPH was not supplied.
- 209 migrated production Java/resources and Java test files compared byte-for-byte equal to their original files. Test directory layout changed, not their Java contents.
- Source scan found no hard-coded author home paths or non-empty default PCC control token. This is a targeted scan, not a comprehensive security/rights audit.

## Known Test Gap

The preserved historical pcc-events-test.cjs timed out waiting for exactly three `.event-row` elements. It is not counted as passing. The other historical browser scripts were preserved but not executed during this migration. Run/update them separately with `npm run test:browser:legacy`; the default current browser test is `npm run test:browser`.

## Boundaries

The compiler still reports existing deprecated Bukkit APIs and third-party native/Unsafe warnings. These were not refactored during directory migration. No live server was started, no plugin was deployed, and no railgraph/occupancy data was edited. Local dependency access required permission outside the tool sandbox; this is distinct from a missing source dependency.

Source ZIP and documentation checks do not establish asset rights, license compatibility, absence of every secret or ATP safety. Review these before publishing.

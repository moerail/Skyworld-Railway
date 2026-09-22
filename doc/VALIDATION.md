# Migration Validation: 2026-09-14

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

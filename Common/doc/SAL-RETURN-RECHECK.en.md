# SAL return-berth rechecks and lease heartbeat

**[FIX · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** Concurrent returns retain their selected path and berth; STCS handles temporary throat traffic by limiting/extending MA. Automatic point alignment cannot withdraw another train’s MA. Destination clearance, stored-train obstacles and real-fault checks remain. [New return rules](SAL-RETURN-TRAFFIC.en.md)

2026-11-26: SAL0.7.6; STA2.9.2/STF5.1.2/STCS4.3.5/PCC2.6.4 unchanged.

Return holds for berth validation, obstacles or waiting for a replacement berth previously omitted route renewal. STCS expires its automatic-path heartbeat after1500ms, explaining a code path that can cause repeated AUTOMATIC_ROUTE_UNAVAILABLE/reallocation. A boolean berth check also mislabelled transient body-path failures and insufficient space as topology changes. Physical switch setting alone does not change berth construction.

All these return holds brake and maintain the already accepted, current-revision route lease. Renewals cannot create a route, revive an expired lease or override ownership/version checks. The original target/plan is retained during a default2000ms recheck. If checks recover, continue the same target under normal MA/ATP conditions. Persistent unknown body geometry becomes FAULT after stopping, rather than repeated topology replans. Confirmed berth/space changes reselect only after the window and a complete stop. No whole-body/boundary/clearance rule is weakened.

SAL config `depot.recheck-ms` defaults2000, accepts integer100–10000; missing keys use defaults. Status/operating reasons identify missing targets, invalid slots, off-road centres, unknown bodies, or body length/centre/bounds/tolerance for insufficient space. Each task logs an actual recheck issue at most once per5s; completion logs its final berth and last issue. Persistent FAULT may legitimately lose MA afterward; it does not keep reallocating new authority.

`/sal status` explicitly separates current tasks from recent dispatch history. An empty current task list followed by ASSIGNED history is not contradictory and does not prove uninterrupted driving.

Regression uses the actual silent-renewal scheduler with a1500ms lease watchdog while holding a transient berth check for2s; it also checks switch-state invariance, real space versus unknown-body classification, restoration, moving-target prohibition and fault/replan outcomes. No local server startup or production operation. Production may contain other failures; report the new kind/detail, graph revision, train UUID, versions and timestamps if pauses persist.

[Chinese rules and production test](SAL-RETURN-RECHECK.md) · [Validation](../../VALIDATION.md)

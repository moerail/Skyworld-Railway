# Handover, intermediate reversals and manual handles · SAL 0.8.0 / STF 5.1.4

**[ADDED / CHANGED · 2026-11-26]** Depot pickup and return now share bounded, segmented maneuver routing. Depot consent prompts retry and refresh; manual power selection survives temporary chunk-loading inhibition. Compatible companions: STA 2.9.2, STCS 4.3.6, PCC 2.6.4. No API identifiers change.

## Depot consent

~~A 0.000001 speed threshold, one-shot delivery and a non-refreshing code~~ are replaced with STF's 0.001 blocks/tick stopped threshold and separate issue/delivery tracking. Fresh complete body observations, manual driver ownership, TIMS and protection-mode checks remain. Any part of the train must cover a registered depot marker for three continuous seconds; centre alignment is not required for manual consent.

Undelivered prompts retry no faster than once per second; a delivered code is not repeatedly displayed. Unconfirmed codes expire after30seconds and refresh while eligible. Confirmed consent also expires if explicit driver release does not follow within30seconds. Queued old prompts and old codes are fenced.

Use /sal handover for an immediate code and a specific readiness rejection, then /sal handover CODE (or the chat button), then explicitly /st release. Seat loss, disconnect and ordinary release are not consent. STF rechecks the stopped train and release receipt at commit. No TIMS, Trip or occupancy protection is cleared.

## Shared maneuver planner

~~Forward-only or one initial reversal, with no intermediate turnback~~ becomes directed-graph search with explicit reversal actions and multiple individual driving legs. Pickup and centre-aligned depot return use the same planner; published operational routes and their explicit station reversals remain unchanged.

The new leading point after reversal is the old rear. Whole-body geometry may span non-branching balises, but every planned reversal must fit on an unambiguous continuous road with at least2blocks checked beyond the body at each end and explicit reverse edges. Planned stops add0.25blocks docking slack. Short roads, missing reverse geometry, unresolved connections and occupied clearance are rejected.

Bounds: up to4 reversals,8192 expanded states and256 unique directed edges per leg; pickup shares its batch deadline, depot choice shares a200ms deadline. Budget exhaustion never submits a partial solution. An already found complete legal ordinary route may be retained without an optimal-cost guarantee; otherwise the search reports its limit. Cost includes travel plus an8second estimate per reversal; existing pickup caps, fleet preferences, berth exclusivity, full destination fit and deep-first single-ended storage remain.

Each leg is submitted independently through STA to STCS. At a turnback: brake, confirm stopped complete-body clearance and expected new head, await old-MA release acknowledgement, request reversal through STA, then require fresh REVERSED feedback and the next leading-end location before requesting the next MA. A release does not clear body occupation. Missing feedback/location confirmation faults after10seconds. TIMS and TR/PT holds prevent reversal. Congestion remains STCS-controlled; automatic point setting does not withdraw another train's MA.

Transient maneuver plans do not auto-resume after restart. Existing RECOVERY_REQUIRED/manual confirmation and cancellation of uncommitted pickup requests remain. This is bounded routing, not a universal reachability proof or automatic deadlock resolver.

## Manual hotbar power

The old CHUNKS_LOADING branch directly cleared powerNotch, explaining P0/B0 in PCC while braking handles survived. It now keeps selected cab handles and inhibits actual traction through the existing loading hold and protection braking. When chunks and normal permissions are ready, current selected input applies. A driver's later neutral/brake selection takes precedence.

It does not restore traction cleared by TIMS, driver loss, explicit MA release or manual input, and cannot clear Trip.

## Upgrade / acceptance

Stop and back up, replace SAL with0.8.0 and STF with5.1.4. Preserve state, routes, devices and ledgers. No ledger reset is required. Test depot consent with any body part above the marker, prompt retry/expiry, a pickup and return requiring an intermediate turnback, a short/occupied turnback road, restart holding, and hotbar power across chunk boundaries with normal authority.

This release does not implement device relocation or global deadlock resolution. Offline regressions do not replace production acceptance.

[中文](SAL-MANEUVERS-AND-HANDOVER.md)

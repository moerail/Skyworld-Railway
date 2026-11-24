# Unified PCC map and SAL route editor

**[ADDED / CHANGED · 2026-11-24]** STA2.7.0 / SAL0.5.0 / PCC2.4.1 / STF5.0.2 / STCS4.3.5.
The default-off routine-review logging option from STCS4.3.4 remains available.

## Map and authentication

~~Infrastructure and operating maps use separate canvases and cameras.~~

One RailGraph SVG and camera now have independent checkboxes for infrastructure devices/mileage, registered SAL devices, operating routes, and live trains/MA. Track geometry remains visible. Hiding a layer changes no control or authority state.
Select a depot to highlight its registered berths and inspect existing fleet counts. Selecting a device highlights associated routes and their directions; multiple matching routes require a choice.

Choose **Enter route editor** and enter the existing switch-control token. The token stays in page memory only and is cleared on exit/unload. Every POST rechecks authentication, control-enabled, origin and the existing HTTPS policy.
Read-only viewing does not authorise editing. Browser writes are queued to an asynchronous service; HTTP threads never touch entities or SAL files.

## Edit a route

1. Register the physical RailGraph and SAL signs first, especially depot berths.
2. Authenticate, select a line and **Edit selected route**, or enter a new name and select at least one existing depot before **Create draft**.
3. Click balises, points, directed graph segments or matching SAL stations to append visits. An explicit edge constrains the chosen branch; current physical point setting does not determine the plan.
4. Reorder/remove visits and set per-visit dwell or reversal at a station. Repeated station visits are supported.
5. **Save draft**, **Validate draft**, then **Publish version**. Incomplete/ambiguous draft connections are dashed and explicitly estimated, not proof of track connectivity.

This phase reuses existing devices. It creates no virtual stations and rewrites no signs. A station still belongs to the line named on its fourth sign line; another line cannot silently share it.
For a new line name, create its empty depot-bound draft, close the PCC edit, register matching station signs in game, close the game edit, then return to PCC. Existing property/message/awake signs retain their physical configuration.

## Versions and running tasks

- UPDATE changes a session proposal only. It is not a saved draft.
- SAVE increments draft revision and invalidates its validation. A draft may be unclosed, but needs a depot and existing references.
- VALIDATE checks the directed loop, station boundaries, reversal rules and topology dependencies.
- PUBLISH checks dependencies again and replaces the current publication with a new numbered definition.
- Each dispatched job persists its own routeSnapshot, visits, dwell values and publication version. Editing or publishing never replaces an existing job's plan.

The current publication and task-owned older snapshots are retained; this phase has no arbitrary historical-version rollback UI. The read-only operating Train DTO adds optional routeVersion while preserving the previous constructor.
Unrelated graph changes preserve validation through STA v9 dependency checks. Changed/unavailable dependencies cannot be published directly. Actual track, device, localisation, TIMS or MA failures still cause the existing hold/recovery process.

Every saved line must bind at least one registered depot. An awake depot/line pair must be permitted by the current publication. Retiring blocks new calls but keeps existing tasks. Deleting a line with tasks is rejected. A depot's last registered berth cannot be removed while the depot remains bound; physical devices referenced by jobs keep their existing removal guards.

## Conflict freeze and manual resolution

SAL RouteWorkspace is the sole writer for both game commands and PCC. Sessions have an actor, source, random edit token and five-minute lease; PCC renews approximately every20seconds.
A second editor of the same line, or a stale draft-revision submission, freezes editing for both sides. UPDATE/SAVE/VALIDATE/PUBLISH/RETIRE/DELETE and ordinary close are rejected.
Already committed drafts/publications are not rolled back. Pending proposals persist only as conflict recovery records, never as saved drafts or published routes. Restart, disconnection, closing a page and lease expiry do not clear a confirmed conflict.

An administrator must discard both pending proposals or adopt one proposal into a new session owned by the resolver. Old tokens cease to work. Resolution itself saves or publishes nothing. The administrator can manually revise the adopted proposal before saving and validating it.
Non-conflicted sessions are cleared on restart; saved data remains. A refreshed page has a new editing identity and may conflict with its previous still-active session; resolve explicitly.

## Game commands

```text
/sal route create Line1 maintown
/sal route depot Line1 maintown,otherdepot
/sal route bind Line1
/sal route choose 1
/sal route bind Line1 edge:<edgeID>
/sal route direction Line1 east
/sal route list Line1
/sal route remove Line1 3
/sal route preview Line1
/sal route validate Line1
/sal route publish Line1
/sal route close Line1
/sal route resolve Line1 DISCARD
/sal route resolve Line1 <actor>
/sal route retire Line1
/sal route delete Line1
```

create may omit the depot only if exactly one depot exists. Register depot first.
The first modifying game command begins a game session and ordinary successful changes save a draft. publish or close ends that game session. list/preview are read-only.
The60second nearby-device chooser checks both graph and draft versions. Finish game editing before opening PCC to avoid a legitimate conflict.

## STA v10 and HTTP

RouteEditingService is an operational planning contract with schemaVersion1, not MA or a newly numbered ATO Message/Packet.
Definition carries name, dx/dz, ordered Visit(deviceId, reverse, optional dwellSeconds) and depots. Catalog carries producer session, graph revision, time, line versions, devices, depots and monotonic workspaceRevision. Requests carry requestId/action/line/actor/source/session/expectedRevision/definition/decision. Replies include state, preview and closure status plus workspaceRevision.

- GET /api/v10/routes: cached public catalog, without edit-session tokens.
- POST /api/v10/routes: authenticated AUTH/CREATE/BEGIN/UPDATE/SAVE/VALIDATE/PUBLISH/HEARTBEAT/CANCEL/RESOLVE/RETIRE/DELETE.

The gateway fixes the PCC source and prefixes the actor, so HTTP cannot impersonate a game source. Requests are bounded to64KiB with bounded idempotent job caches. Repeated polls do not replay an edit. The browser rejects older catalog revisions after a successful write, avoiding false conflicts caused by cached GET responses.
STCS alone owns topology, resource arbitration and MA; SAL owns plans/jobs; STA connects services; STF performs physical actions on the owning entity/region thread. Closing PCC does not stop service.

## Migration and validation

Preserve state.json, RailGraph, train data, destruction receipts and all occupancy ledgers. Legacy publications import as version1, using matching awake depots or the only depot. Ambiguous/unbound legacy lines remain drafts until explicitly bound and published. Old tasks receive their own route snapshots and still restart as RECOVERY_REQUIRED.
Update all five plugins together and fully restart. Downgrade only with matching data backups.

Tests cover immutable contracts/JSON, draft/publication isolation, pinned definitions, persistent two-sided freezes, manual proposal adoption, stale leases/revisions, idempotency, missing-depot and active-task deletion rejection, explicit-edge branch selection, asynchronous gateway and token rejection. Browser tests cover layers/camera, authentication, device picking, dashed drafts, save/validate/publish, delayed-catalog fencing, conflict resolution, stale data and four languages.
Real simultaneous administrators, complex layouts and physical failure injection still need on-site acceptance.

Up/down preferences, whole single-track reservations, general multiple-reversal search and a Python planning daemon remain design only.


## **[ADDED / FIXED · PCC 2.4.1] Map selection and filters**

~~Browser-native focus outlines around selected track or devices.~~ Selection now follows the actual track polyline or a small device ring with screen-sized strokes. Map zoom cannot inflate the outline; mouse selection and keyboard focus retain visible feedback.

Beside the A− / A+ text controls, **H− / H+** adjusts highlight size (50%–250%). Click its percentage to reset to100%; use the colour swatch to change colour. Size and colour are stored in this browser; controls still work if storage is unavailable.

Infrastructure devices has **Balise, END, ORIGIN and Switch** children. The parent selects or clears all; children can be toggled independently, with an indeterminate parent for a partial selection. Markers, labels and switch-direction arrows are filtered together. Base track and SAL layers remain independent.

These are display settings only: they do not edit RailGraph, devices, routes, MA or occupancy. Use Ctrl+F5 when updating an old browser page.

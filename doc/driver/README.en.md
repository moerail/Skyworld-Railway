# SkyRail Driver Manual

[中文](README.zh.md) | [English](README.en.md) | [Français](README.fr.md) | [日本語](README.ja.md) · [Project home](../../README.md)

For ordinary players aboard a manual train. Baseline: STF/STCS 4.0.0, STA/SkyPCC 2.0.0. Administrators handle train creation, track construction and plugin configuration.

> **In an emergency, use `/st eb`.** Shadow MA and ATP limits remain advisory. The separate experimental `Enforced` channel can brake a manual train only after an administrator explicitly enables it and STCS issues an executable MA. Do not assume it is enabled or live-validated. This is a game guide, not a real railway rulebook.

## A Few Terms Before You Drive

**MA tells you how far you are currently authorized to move; EoA is the end of that authority; ATP limit is the current permitted speed.** Claiming driving control, submitting a request and receiving a valid MA are separate steps.

| Term | Meaning for the driver |
| --- | --- |
| MA · Movement Authority | Authority for a bounded part of the track ahead. It can change with conditions and is not a complete route to your destination. Shadow MA is observational. |
| EoA · End of Authority | The endpoint of that authority; stop before it. More track beyond it does not grant permission to continue. |
| ATP · Automatic Train Protection | Train protection. SkyRail's Shadow channel only advises; the admin-enabled Enforced channel provides experimental brake supervision. |
| ATO · Automatic Train Operation | Automatic driving. STF's sign-driven pseudo-ATO trains are separate from this manual-driving state machine. |
| HMI / PCC | HMI is the onboard display; PCC is the dispatch web interface. Interpret authority and status together with their channel and validity. |
| P / N / B / EB | Power notch, controller neutral, service-brake notch and emergency brake. B7 is the highest service-brake notch. |
| Balise | A track-graph position reference used for line and mileage calibration; the marker itself does not authorize movement. |

Mode codes stay unchanged: `SB` Stand By, `FS` Full Supervision, `SH` Shunting, `SR` Staff Responsible (dispatcher approval in SkyRail), `TR` Trip hold after an overrun, and `PT` Post Trip after stopping and acknowledging. These are simplified experimental SkyRail modes.

## Manual-Train Operating Modes

The sidebar's ATP mode is two fields: **channel | operating mode**, for example `Enforced | SR`. Channel names are translated; `SB/FS/SH/SR/TR/PT` remain unchanged across languages. After `/st drive`, the manual train starts in `SB` (brake hold) in the enforced channel. `/stcs ma demand` requests `FS`; `/stcs ma sh` requests a bounded shunting permission after stopping; `/stcs ma sr` requests SR after stopping and waits for PCC or administrator approval to a selected balise, switch, origin or end. A pending request is not permission to move. If EoA overrun trips the train to `TR`, stop, use `/stcs ma ack` to enter `PT`, then `/stcs ma release` before demanding a new authority. The shadow channel never enforces these modes. Contact dispatch on lost MA; stop at the last confirmed EoA.

**This state machine applies only to manual trains.** STF's built-in pseudo-ATO automatic trains keep their existing sign-driven behaviour. Boarding one or entering MA/mode commands does not turn it into an `FS/SH/SR` train.

In the enforced channel, SH/SR default to **40 km/h**, configurable by the administrator. Exceeding the ceiling beyond a small detection tolerance applies B7 without the relaxed FS overspeed delay. Near EoA, follow the lower ATP limit; 40 km/h is not a guaranteed speed throughout the authority.

An approved SR target may be farther away than the current MA. Each rolling grant is capped at 120 m by default, updates with conditions ahead and cannot authorize travel beyond the approved target. **Drive only within the currently valid MA.** Target approval does not mean every point and section along the route is open. Saved geometry may cover unloaded chunks, but unknown points, uncertain occupancy or graph gaps can prevent extension.

## 1. Board and Claim Control

Use a registered manual train that you are allowed to drive. Do not casually use `/st drive` aboard an automatic service: it is not a dedicated supervisor-only mode.

1. Sit in a minecart and remain aboard.
2. Use `/st lang en`; optionally `/st speedunit kph` for km/h.
3. Enter `/st drive`, wait for confirmation and check the train name.
4. Enter `/st b7`, apply service braking and verify that speed reaches zero.
5. At standstill, select `/st forward` or `/st backward` as required.

Forward/backward refers to the train, not the direction your camera faces. For a first test, use P1 briefly on an administrator-provided clear test track to check direction.

Every new boarding requires `/st drive` again. Other controls do not implicitly claim ownership. Passengers do not automatically become drivers; contact the current driver or an administrator if the train is occupied. Basic commands require `skytrain.use`; MA requests also require `stcs.ma`. Both default to ordinary players, but servers can change permissions.

## 2. Does Your Server Use MA?

**STF alone:** manual traction, braking, cab and hotbar controls work, but there is no STCS MA, EoA or shadow ATP curve. Check track and points according to local rules; do not expect automatic collision protection.

**With STCS/STA:** after claiming control, stopping and selecting direction, enter:

```text
/stcs ma demand
```

This requests an authority; it does not guarantee allocation. Check the HMI's remaining MA, EoA, allocation reason and infrastructure state before departing under server rules. Driving control is separate from MA; MA is neither destination routing nor automatic driving.

Waiting, unallocated, expired, unknown position, `SWITCH_UNKNOWN` and `RESOURCE_CONFLICT` are not proof of clear track. Stay stopped and contact dispatch/an administrator. Do not isolate protection, clear ledgers or drive past EoA to bypass a problem. An unavailable STCS on a server that normally uses it is not permission to switch to standalone operation.

## 3. Move and Stop

| Command | Meaning |
| --- | --- |
| `/st p1` to `/st p4` | Power notches; higher numbers request more traction, not a fixed speed |
| `/st n` | Controller neutral: no traction and controller brakes released; the train may coast |
| `/st b1` to `/st b7` | Service-brake notches; higher numbers request stronger braking |
| `/st eb` | Emergency braking; stopping still takes distance |
| `/st neutral` | Reverser neutral; not the same as `/st n` and not a brake |

When cleared to start under local rules, enter `/st p1` separately and watch the speed before increasing power. Brake early, adjust B1/B3 or other notches as necessary, and hold B7 once stopped. Do not wait until the EoA or station stopping point to begin braking.

**Power notches release controller braking; N releases controller braking and controller EB too.** Neither overrides an independent protection brake hold. N is not a reset button that keeps the brakes applied.

Shortcuts such as `/p1`, `/n`, `/b7` and `/eb` also exist; use `/st ...` if another plugin conflicts. Brake to a standstill before reversing; do not use reverse traction to stop.

## 4. Cab and Hotbar

After claiming control, `/st cab` opens the graphical cab. `/st hotbar` toggles hotbar driving; **select slot 5 before enabling it**.

| Slot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Notch | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

Enabling it does not change the current handle or release brakes. Subsequent slot changes apply the selected notch; number keys help avoid accidental scroll-wheel inputs. While enabled, the swap-hands key (default F) applies EB instead of swapping items.

Enter `/st hotbar` again to disable it; slot 5 is not required for disabling. Disabling an input method does not stop the train or release driving control. Brake to a stop first. `/st eb` remains available for emergencies.

## 5. Read the Displays

- Sidebar: actual speed, the current channel's ATP limit on line two, line/mileage, reverser, handle, driver and protection state. Enforced intervention also shows ATP B7/EB and its reason.
- Driver-only BossBar: the current channel's remaining MA and ATP limit, plus ATP B7/EB during enforced intervention. Other passengers do not receive this driver MA bar.
- EoA is the End of Authority, not your destination. Valid calibration can provide line/mileage; otherwise an edge/offset or unavailable indication may appear.
- `--`, unallocated and expired mean unavailable information, not unlimited speed or MA. The default shadow curve reaches zero 1 m before EoA; within the final 5 m it is at most 5 km/h and continues down to zero. Server settings may differ; you must still brake manually.
- Sounds may indicate allocation, sudden MA changes, release or approaching the limit. Sounds and thresholds are configurable and may be muted; silence is not permission to proceed.

Near-limit and overspeed alarms work in both Shadow and Enforced; overspeed takes priority. Entering TR in Enforced sends a driver message and emergency-brake cue. The TR ATP EB indication remains visible after stopping. Service and emergency intervention cues are separately configurable. Resolve the cause before resuming; for TR, follow the TR → PT → SB procedure above rather than trying to reset it with N.

## 6. Finish or Handle an Emergency

For a normal handover, stop fully and keep brakes applied. With STCS, you may use `/stcs ma release` to release forward reservations, then `/st release` to hand back driving control, then dismount.

**The two release commands differ:** `/stcs ma release` requires a stop in `Enforced`, returns to `SB` with B7 brake hold and releases forward reservations; in shadow it retains the legacy non-braking behaviour. It never releases driving control or clears train-body occupancy. `/st release` relinquishes driving control and applies EB; it does not resume automatic operation.

Dismounting, disconnecting, dying or losing the driving-seat entity revokes control and applies EB, without transferring control to a passenger. After boarding again, repeat `/st drive`, check direction/braking and, with STCS, confirm or request MA again.

For an emergency, immediately use `/st eb` and notify dispatch. Resume only after stopping, resolving the cause and rechecking authority. If `RECOVERING` or brake hold remains, contact an administrator; do not repeatedly apply power or isolate protection to bypass it.

## 7. Troubleshooting

| Symptom | Action |
| --- | --- |
| No train found | Sit in a registered train; an ordinary loose minecart may not belong to STF |
| Driving declaration required | Remain aboard, use `/st drive` and check confirmation |
| Power selected but no movement | Check reverser, EB/brake hold and mode; do not simply select P4; report the HMI |
| MA requested but still waiting | Request acceptance is not allocation; inspect the reason and contact dispatch |
| Permission denied | Ask an administrator to check `skytrain.use` / `stcs.ma`; ordinary drivers do not need admin access |

Report the train name/service number, time, location, recent actions and an HMI screenshot. `/st help` and `/st version` show help and versions. Do not delete trains or occupancy records to resolve a driving problem.

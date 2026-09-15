# Basic Automatic Signs (STF 2.1.3)

This update covers automatic target speed, lead-cart station alignment and local
station detection without STCS line calibration. It does not add occupancy
protection, MA enforcement or a new driver-authority state machine.

## Target Speed

Place this four-line sign using the existing STF rail/sign mounting rules:

```text
[+stf]
property
V_target
60km/h
```

`[+stf]` is always enabled; `[stf]` requires the existing redstone activation.
Only `V_target` is accepted by this property-sign implementation. Manual trains
ignore it. Other properties remain command-only rather than permitting signs
to change control mode, identity or vehicle profiles.

Administrators can set and read the same property:

```text
/st property test V_target 60km/h
/st property test get V_target
```

Values without a unit are blocks per tick. `0.4`, `8m/s` and `28.8km/h` are
equivalent at 20 ticks per second and one block per metre. Negative, non-finite
and values above 100 blocks per tick are rejected. The target is persisted with
the train properties. It is not a replacement for the train/profile speed cap.
Setting it alone does not start a stopped train.

A station with an explicit departure speed keeps that speed for its departure;
otherwise it inherits `V_target`, falling back to `settings.station-launch-speed`
(default `0.4`). An encountered property sign changes subsequent automatic
cruise targets; a station's explicit departure speed still takes precedence.

## Station Alignment

Since 2.1.3 the virtual driver uses remaining-distance feedback for service
braking, tapering the planned deceleration from B4 towards B1 near the marker.
Below the curve it coasts instead of repeatedly braking and reapplying P1.
Discrete brake selection has a small deadband. A genuine undershoot enters
low-speed recovery with separate power-on and power-off thresholds. Insufficient
stopping distance still requests B7; station dwell and HOLD retain B7.
These changes affect the virtual driver's handle selection only, not ATP or
manual driving. The search range and vehicle profile still need to permit timely
station detection; late discovery cannot guarantee a smooth stop.

The stopping reference is the centre of the leading cart in the direction of
travel, aligned with the station rail and its configured offset. Half the train
length is no longer added. Reverse travel uses the opposite leading cart.
Existing station wait, departure and redstone syntax remains unchanged.

## Unmarked Track

Add or adjust this key in `plugins/SkyTrainFolia/config.yml`, inside the existing
`settings` section, then restart normally:

```yaml
settings:
  station-local-look-ahead-blocks: 256.0
```

The local search runs at most once every 200 ms and follows current rail shapes,
including curves and the selected turnout branch. It needs no balises, line name,
origin, end or STCS connection. It discovers station signs for the virtual driver,
which continues to operate through traction and braking notches.

The range is in blocks, clamped to 0-1024; zero disables local search. Search stops
at unloaded rails or rails outside the current Folia thread's ownership. It never
forces chunk loading. This is a maximum search range, not a guaranteed braking
distance. Optional STA/STCS graph advice retains its separate
`station-look-ahead-blocks` setting.

This is a basic pseudo-ATO convenience feature, not ATP or collision protection.
It does not detect another train ahead. Use conservative speeds and sufficient
loaded track for testing.

## Server Acceptance Tests

1. On an unmarked straight track, run an automatic train towards an active
   station. Confirm advance braking without any line calibration.
2. Repeat with short and long consists, then reverse travel. The leading cart,
   not the consist midpoint, should align with the station stopping reference.
3. Put a curve before the station and repeat. Also put a station on an unselected
   turnout branch: it must not become the local stopping target.
4. Run through the property sign with `0.4`, `8m/s` and `28.8km/h`. Check equivalent
   cruise targets, then test `60km/h` within the configured vehicle speed cap.
5. Run a manual train over the same sign: its controls must remain unchanged.
6. Test a station with and without an explicit departure speed. Verify explicit
   speed precedence and inherited target respectively.
7. Reduce the local range, then set it to zero on a test without graph advice.
   Confirm advance detection follows the configured range. Do not use a short
   range to judge high-speed braking safety.

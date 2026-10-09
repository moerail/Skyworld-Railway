# STA 2.1.1 — desktop dispatcher map candidate

This test-server update adds a read-only `shadow.get` request to STA Remote/1. The response is emitted only when STCS provides a fresh shadow snapshot for the current RailGraph revision. It does not grant MA or change interlocking authority.

The desktop client now:

- distinguishes confirmed line assignments (thick, line-colored tracks) from unassigned sidings (thin tracks);
- draws occupied sections red, uncertain/frozen sections dashed amber, and shadow reservations cyan; stale or unavailable occupancy appears as a dashed base graph;
- marks switches with a bold black number on yellow and a state-colored direction arrow;
- draws a direction arrow on each positioned train and shows selectable train rows with position, speed, driver, line, mileage and MA details;
- retains the four languages and day/night themes.

Build `SkyworldTrainAPI-2.1.1.jar` and `SkyRail-Dispatcher-2.1.1.pyz` or `.exe`. Replace only the STA JAR on a stopped test server. Its existing `plugins/SkyworldTrainAPI/config.yml`, PKCS#12 keystore and administrator tokens remain valid. Restart the server with the keystore password environment variable set, then rebuild and relaunch the desktop client. Do not deploy the old 2.1.0 desktop client with the new display requirements; it never requests `shadow.get`.

Acceptance: verify fresh occupancy colors against STCS and SkyPCC for a stopped train and an allocated shadow reservation. Stop STCS or sever the connection and verify the graph returns to dashed/unknown, rather than leaving a red or cyan band looking current. Confirm switch direction after a state change, train selection and arrow orientation on both travel directions, line weight, and all four languages in both themes.

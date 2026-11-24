# Cab sidebar: next-switch opening direction

The cab sidebar keeps its 15-row budget. The existing next-switch row now displays
`下一道岔: S1 [开向: 左]` instead of the switch mileage/coordinates. The following
distance row stays unchanged. Labels are available in Chinese, English, French,
Japanese and Russian. When there is no next switch, the row shows `--`; missing observations show an
unknown direction, and mismatched physical/commanded positions show changing.

STCS `queryTrack` adds these fields alongside the existing switch name, position,
mileage and distance:

| Field | Values |
| --- | --- |
| `nextSwitchState` | `STRAIGHT`, `DIVERGING`, `PENDING`, `UNKNOWN` |
| `nextSwitchDirection` | `STRAIGHT`, `LEFT`, `RIGHT`, `UNKNOWN` |

Left/right are relative to the approaching train, including a trailing approach.
An approach from a disconnected trailing port or incomplete port geometry has an
unknown direction. The navigation display uses the observed physical position;
it does not substitute the saved graph state or the last command when physical
state is unavailable. Existing operational-target lookup is unchanged.

The STA `RailNetworkService.Navigation` record carries the same two new fields,
so both typed STA navigation and the direct reflective STCS bridge show them.
The previous Navigation constructor remains available and defaults these fields
to unknown for older providers. STF also accepts an older STA without these
accessors, preserving the existing navigation/distance and displaying unknown.
Use the rebuilt **STA, STCS and SkyTrainFolia** together for the new display.

中文说明：道岔名称后显示开向，距离仍在下一行；不增加记分板行数。
直、左、右均按列车接近方向解释，未证实的物理状态不会用历史状态代替。
该显示只说明道岔观测，不能替代当前有效的 MA。

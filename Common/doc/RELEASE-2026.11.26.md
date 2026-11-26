# SkyRail Suite — 2026-11-26 source release

## Compatible versions / 配套版本

| Module | Version |
| --- | --- |
| SkyworldTrainAPI / STA | 2.9.2 |
| SkyTrainFolia / STF | 5.1.4 |
| STCS | 4.3.6 |
| SkyATO-Lite / SAL | 0.8.0 |
| SkyPCC | 2.6.4 |

## Included changes / 本轮内容

- Shared SAL stations and pickup fleet, centre-aligned multi-berth depots, explicit handover and administrator override; precise dispatch and localisation diagnostics.
- Stable concurrent depot-return plans; automatic point alignment waits for another train's MA instead of withdrawing it.
- Bounded pickup/return maneuver search: up to4 reversals, whole-consist clearance and independently authorised legs through STCS and STA→STF.
- Retry/expiry handling for depot consent prompts. Manual hotbar power selection survives chunk-loading inhibition; actual traction remains prohibited until safe.
- Configurable P4 departure/B6 service-brake defaults, with existing ATP/TIMS protection retained.
- PCC editor polling/selection fixes, failing-segment diagnostics, real-track route highlights and draggable visit ordering.
- Updated seven-language suite manuals, five-language driver guides, module documentation, contracts, Chinese SAL manual and production tests.

旧版 station/awake 格式与共享车池变更详见[迁移契约](SAL-SHARED-DISPATCH.md)。新增途中折返、纳管和手动牵引行为见[中文说明](SAL-MANEUVERS-AND-HANDOVER.md)／[English](SAL-MANEUVERS-AND-HANDOVER.en.md)。本版未实现全局死锁自动解除、设备迁移事务、上下行偏好、整段单线预约或 Python 规划守护进程。

## Validation and installation / 验证与安装

- Full offline build:131 Java test entry points passed on Shiroha26.2 and131 on26.3; [validation record](../../VALIDATION.md).
- PCC real-browser editor regression and476 four-language translation keys passed. No Minecraft server was started or deployed for this source push.
- Stop and back up before replacing JARs. Preserve trains, route/device state and occupancy ledgers. Existing active SAL jobs require recovery confirmation after restart.
- [中文生产测试](../../SkyATO-Lite/TEST.zh.md) · [中文使用手册](../../SkyATO-Lite/MANUAL.zh.md) · [English suite manual](../README.en.md).

Generated JAR/ZIP files, local server installations, build logs and credentials are excluded from Git. Build/package scripts and prerequisites are documented in the suite manuals; the distribution packager checks extracted local links and file hashes.

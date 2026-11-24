# SkyTrainFolia / STF 5.0.2

[中文](../Common/README.zh.md) · [English](../Common/README.en.md) · [Français](../Common/README.fr.md) · [Nederlands](../Common/README.nl.md) · [Русский](../Common/README.ru.md) · [日本語](../Common/README.ja.md) · [文語體](../Common/README.ja.classical.md)

## Current role / 当前职责

Train entities, physics, chunk tickets, manual driving, TIMS and executable ATP. / 列车实体、物理、区块票据、手动驾驶及保护执行。

## **[ADDED / CHANGED · 2026-11-24]**

New driving sessions request FS by default; authority is still required before motion. SAL actuation goes through STA and the owning entity/region thread. Legacy automatic signs and mode auto/automatic are removed. SAL handover requires confirmation and explicit release of the same driving lease. / 默认申请FS并等待有效MA；自动驾驶实体操作经STA；旧伪ATO移除；纳管须确认并释放同一驾驶权。

[English driver guide](../Common/doc/driver/README.en.md) · [中文驾驶说明](../Common/doc/driver/README.zh.md) · [Handover](../Common/doc/SAL-MANUAL-HANDOVER.md) · [Cab direction](../Common/doc/CAB-SWITCH-DIRECTION.md)

## Compatible set / 配套版本

STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1. Stop and back up before replacing plugins; preserve trains, graph, route state and occupancy ledgers. / 停服备份后更新，保留列车、图、线路与占用数据。

[Validation / 验证](../VALIDATION.md) · [Breaking changes / 破坏性更新](../README.md#breaking-changes--破坏性更新)

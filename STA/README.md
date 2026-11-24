# SkyworldTrainAPI / STA 2.7.0

[中文](../Common/README.zh.md) · [English](../Common/README.en.md) · [Français](../Common/README.fr.md) · [Nederlands](../Common/README.nl.md) · [Русский](../Common/README.ru.md) · [日本語](../Common/README.ja.md) · [文語體](../Common/README.ja.classical.md)

## Current role / 当前职责

Typed contracts between components; STA owns no train entities. / 模块间契约，不持有列车实体。

## **[ADDED / CHANGED · 2026-11-24]**

v5 telemetry and RailGraph, executable MA/ATP contracts, v8 SAL operation/protocol, v9 graph dependencies, v10 route-editing service. / 遥测与轨道图、正式MA/ATP、SAL任务、图依赖和线路编辑契约。

[Architecture](../Common/doc/ARCHITECTURE.md) · [ATO Message/Packet contract](../Common/doc/STA-V8-ATO-PROTOCOL.md) · [v10 route editor](../Common/doc/PCC-ROUTE-EDITING.en.md)

## Compatible set / 配套版本

STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1. Stop and back up before replacing plugins; preserve trains, graph, route state and occupancy ledgers. / 停服备份后更新，保留列车、图、线路与占用数据。

[Validation / 验证](../VALIDATION.md) · [Breaking changes / 破坏性更新](../README.md#breaking-changes--破坏性更新)

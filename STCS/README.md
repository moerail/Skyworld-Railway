# STCS 4.3.5

[中文](../Common/README.zh.md) · [English](../Common/README.en.md) · [Français](../Common/README.fr.md) · [Nederlands](../Common/README.nl.md) · [Русский](../Common/README.ru.md) · [日本語](../Common/README.ja.md) · [文語體](../Common/README.ja.classical.md)

## Current role / 当前职责

Infrastructure topology, occupancy, route arbitration, switch locking and MA. / 工务拓扑、占用、排路、道岔锁闭和MA。

## **[ADDED / CHANGED · 2026-11-24]**

Loaded-only local RailGraph updates retain confirmed connections in unloaded chunks. Rail review defaults to60seconds; routine console logging is off by default. Graph changes revalidate only affected SAL routes. Explicit train removal releases scoped evidence after complete destruction receipts; other trains retain protection. / 局部增量更新保留未加载旧连接；默认60秒复核、常规日志默认关闭；仅重验受影响线路；显式删车按销毁证据出清。

[Incremental graph](../Common/doc/RAILGRAPH-INCREMENTAL.md) · [Deletion clearance](../Common/doc/DELETION-CLEARANCE.md) · [Configuration switches](../Common/doc/CONFIGURATION-SWITCHES.md)

## Compatible set / 配套版本

STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1. Stop and back up before replacing plugins; preserve trains, graph, route state and occupancy ledgers. / 停服备份后更新，保留列车、图、线路与占用数据。

[Validation / 验证](../VALIDATION.md) · [Breaking changes / 破坏性更新](../README.md#breaking-changes--破坏性更新)

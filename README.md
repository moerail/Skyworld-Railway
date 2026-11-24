# SkyRail Suite — 2026.11.24

**[ADDED / FIXED · PCC 2.4.1]** Screen-sized selection highlights with H− / H+, reset and colour controls beside text scaling; infrastructure child filters for Balise, END, ORIGIN and switches. / 高亮固定屏幕线宽，文字缩放旁可调整大小、复位和颜色；新增应答器、END、ORIGIN、道岔子图层。

Module guides / 模块说明： [STF](SkyTrainFolia/README.md) · [STCS](STCS/README.md) · [STA](STA/README.md) · [SAL](SkyATO-Lite/README.md) · [PCC](SkyPCC/README.md)


**TRAIN Runs Asynchronously In Network**

Minecraft/Folia railway operation, signalling and control suite. / 基于Minecraft/Folia的铁路运行、列控与调度套件。

![SkyRail Suite](Common/logo.png)

## Unified map and route editor / 统一地图与线路编辑

**[ADDED / 新增 · 2026-11-24]** One RailGraph map with checkbox layers; existing-token editor, separate drafts/publications, pinned task versions, mandatory depot bindings and persistent two-sided conflict freeze. / 同一地图勾选图层、既有Token编辑、草稿与发布版隔离、任务固定版本、车辆段必选、两侧冲突冻结且人工解消。

[中文操作与契约](Common/doc/PCC-ROUTE-EDITING.md) · [English guide and contract](Common/doc/PCC-ROUTE-EDITING.en.md)

## Detailed manuals / 详细手册

- [中文](Common/README.zh.md)
- [English](Common/README.en.md)
- [Français](Common/README.fr.md)
- [Nederlands](Common/README.nl.md)
- [Русский](Common/README.ru.md)
- [日本語](Common/README.ja.md)
- [文語體・詳細技術取扱（日文併記）](Common/README.ja.classical.md)

Each manual starts with breaking changes and final deliverables, followed by installation, driving, commands, infrastructure, SAL sign layouts, depot operation, handover, MA/ATP, PCC and recovery. **[ADDED / CHANGED / REMOVED / QUERY]** labels identify this release; ~~strikethrough~~ preserves removed instructions. Previous manuals are archived and explicitly historical.

各语言手册以破坏性更新和本轮产物开篇，更新正文和操作步骤；**[新增／修改／删除／查询]** 高亮新版变动，~~删除线~~ 保留已废弃用法。历史原文单独归档。

## Breaking changes / 破坏性更新

- **[REMOVED / 删除]** STF legacy automatic station/spawn/destroy/property execution and ~~`mode auto` / `mode automatic`~~. Use SAL for automatic operation. Old signs remain but no longer execute. / STF旧伪ATO和自动牌执行链移除，自动运营统一经SAL。
- **[CHANGED / 修改]** ~~Default shadow MA; ATP inactive~~ → new driving sessions request FS by default with executable ATP. Movement still requires valid MA. Explicit shadow testing remains. / 默认请求FS并启用可执行保护，有效MA获批前保持制动。
- **[CHANGED / 修改]** SAL depot handover requires confirmation followed by explicit release of the same driving lease. `/st release` alone does not consent. / 库位纳管须先确认，再显式释放同一驾驶权。
- **[CHANGED / 修改]** RailGraph scans loaded chunks locally, preserving confirmed unloaded connections. Never clear ledgers or mix old STA binaries for migration. / 局部扫描保留未加载旧连接，升级不清账本、不混用STA。

## Final compatible set / 最终配套版本

| File | Responsibility / 职责 |
| --- | --- |
| `SkyworldTrainAPI-2.7.0.jar` | Contracts and telemetry / 契约与遥测 |
| `SkyTrainFolia-5.0.2.jar` | Physical execution, manual driving, TIMS/ATP / 实体执行与保护 |
| `STCS-4.3.5.jar` | RailGraph, occupancy, MA and routing / 图、占用、许可与排路 |
| `SkyATO-Lite-0.5.0.jar` | Services, calls, dwell, depot and handover / 运营任务与移交 |
| `SkyPCC-2.4.1.jar` | Infrastructure and operating maps / 工务与运营地图 |

**[ADDED / 新增]** Blue `[SkyATO-Lite]` prefix and grouped help; grey STF and yellow STCS. Version output includes SAL and current ATP wording. / SAL蓝色前缀、分组帮助，STF灰色，STCS黄色；修正版本提示。

**[ADDED / 新增]** PCC unified layers and operating editor, DRAFT/VALIDATED/PUBLISHED line styles, depot highlighting and parked/operating/unknown lists. / PCC统一图层及运营编辑、三种状态线型、车辆段停车位高亮及车辆列表。

**Design only / 仅设计**: up/down preferences, whole single-track-section reservations, general multi-reversal search and Python computation daemon. [Design / 设计](Common/doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md). These are not installed runtime features.

## Distribution / 分发包

**Extract the whole ZIP** before opening documents. Keep its directory structure: all local links and images stay inside it. / **完整解压ZIP后**再打开文档，保留目录结构，链接和图片使用包内相对路径。

```text
SkyRail-Suite-2026.11.24/
  README.md                 Start here / 入口
  Common/README.*.md         Seven manuals / 七版完整手册
  Common/doc/               Contracts, migration and historical manuals
  plugins/                  Five JARs + SHA256.json
  Source/                   Source archive / 源码包
  VALIDATION.md             Validation scope / 验证记录
  MANIFEST.sha256            All-file checksums / 全文件校验
```

Java25 and adapted Shiroha/Folia26.2 or26.3. Stop and back up, replace the five plugins together, merge configuration, restart. No Minecraft core, world, private token, certificate or production configuration is distributed.

环境为Java25及已适配Shiroha/Folia26.2或26.3。备份停服、整套替换、合并配置、重启；不含MC核心、世界、私有令牌、证书或生产配置。

[Validation / 验证](VALIDATION.md) · [Changelog / 更新记录](Common/CHANGELOG.md) · [Code licence / 代码许可](Common/LICENSE) · [Image permissions / 图片许可](Common/ASSET-LICENSE.md)

Game implementation inspired by railway concepts; not certified railway equipment or interoperable ETCS. / 游戏内铁路控制实现，不是认证铁路设备或标准ETCS互操作产品。

Acknowledgements: [bergerhealer](https://github.com/bergerhealer), [TrainCarts](https://github.com/bergerhealer/TrainCarts), [BKCommonLib](https://github.com/bergerhealer/BKCommonLib). TC/BKCommonLib are not runtime requirements.

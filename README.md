# SkyRail Suite

<p align="center">
  <img src="logo.png" alt="SkyRail Suite" width="64" height="64">
</p>

**完整手册 / Full Manuals / Volledige handleidingen:** [中文](README.zh.md) | [English](README.en.md) | [Nederlands](README.nl.md) | [Français](README.fr.md) | [日本語](README.ja.md)

面向 Minecraft / Folia 异步服务器架构的列车运行、铁路基础设施、列控与调度显示套件。

**TRAIN** **R**uns **A**synchronously **I**n **N**etwork

A train-operation, railway-infrastructure, shadow train-control and dispatching-display suite for Minecraft / Folia.

Een suite voor treinbediening, spoorweginfrastructuur, schaduwtreinbeveiliging en verkeersleidingsweergave in Minecraft / Folia.

SkyRail Suite 延续 SkyTrain Suite 开发线；现有插件名、指令和数据目录不变。

## 组件 / Components / Onderdelen

| Plugin | Version | Responsibility |
| --- | --- | --- |
| SkyTrainFolia (STF) | 2.1.3 | Trains, driving, physics, signs and cab HMI |
| STCS | 2.2.1 | RailGraph, localisation, occupancy and shadow MA/EoA |
| SkyworldTrainAPI (STA) | 0.8.1 | Versioned inter-plugin contracts, telemetry and events |
| SkyPCC | 0.8.1 | Web track diagram, inspectors, event log and turnout control |

## 2.1.3 更新 / Update / Bijgewerkt

- **中文：** V_target 自动属性牌、无标定 Station 前视、领车停车对位，以及按剩余距离调整的进站制动。MA/EoA 仍为影子模式。
- **English:** V_target property signs, unmarked-track station detection, lead-cart stop alignment and distance-feedback station braking. MA/EoA remain shadow-only.
- **Nederlands:** V_target-borden, stationdetectie zonder lijnkalibratie, stoppen op de voorste mijnkar en remregeling op resterende afstand. MA/EoA blijven in schaduwmodus.

## 开始使用 / Getting Started / Aan de slag

从 [Releases](https://github.com/moerail/Skyworld-Railway/releases) 下载同一发布中的四个 JAR。当前适配基线：**Java 25，Shiroha / Folia 26.2**。安装前备份并先在测试服验证；完整安装、权限、指令、配置、教程与构建方法见上方五语手册，每份均可独立阅读。

Download the four matching JARs from Releases. Current baseline: **Java 25, Shiroha / Folia 26.2**. Back up and test before deployment. Each manual above includes installation, permissions, commands, configuration, tutorials and build instructions.

Download de vier bij elkaar horende JAR's via Releases. Huidige basis: **Java 25, Shiroha / Folia 26.2**. Maak een back-up en test vóór installatie. Elke handleiding hierboven bevat installatie, rechten, commando's, configuratie, voorbeelden en bouwinstructies.

## 开发版边界 / Alpha Limitations / Alfabeperkingen

- **中文：** MA/EoA 目前仅为影子计算与显示，ATP 不据此施加制动。司机失能 EB、手动 EB 与 RECOVERING 制动保持是独立的实际控制功能。本项目不是认证铁路安全系统。
- **English:** MA/EoA are currently calculated and displayed in shadow mode; ATP does not brake in response. Driver-loss EB, manual EB and RECOVERING brake hold are separate active functions. This is not a certified railway safety system.
- **Nederlands:** MA/EoA worden momenteel alleen in schaduwmodus berekend en weergegeven; ATP grijpt hierbij niet remmend in. Noodremming bij verlies van de machinist, handmatige noodremming en de remvasthouding in RECOVERING zijn afzonderlijke actieve functies. Dit is geen gecertificeerd spoorwegveiligheidssysteem.

## 致谢 / Acknowledgements / Dankwoord

**中文：** 感谢 [bergerkiller](https://github.com/bergerkiller) 及 [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)、[TrainCarts](https://github.com/bergerhealer/TrainCarts) 的贡献者，为 Minecraft 插件生态与矿车铁路玩法所做的长期工作。TrainCarts 的牌子接口和运行流程为本项目提供了重要参考。SkyRail Suite 是独立项目，不要求安装 BKCommonLib 或 TrainCarts，也不代表二者的官方版本、认可或完整兼容实现；相关 TrainCarts 版权与许可声明继续保留。

**English:** Thank you to [bergerkiller / Berger Healer](https://github.com/bergerhealer) and the contributors to [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) and [TrainCarts](https://github.com/bergerhealer/TrainCarts) for their long-standing work on Minecraft plugins and minecart railways. TrainCarts' sign interfaces and operating workflows have been important references for this project. SkyRail Suite is an independent project: it does not require either plugin, is not an official or endorsed version of either, and does not claim complete compatibility. The applicable TrainCarts copyright and license notice is retained.

**Nederlands:** Met dank aan [bergerkiller / Berger Healer](https://github.com/bergerhealer) en de bijdragers aan [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) en [TrainCarts](https://github.com/bergerhealer/TrainCarts) voor hun jarenlange werk aan Minecraft-plugins en spoorwegen met mijnkarretjes. De bordinterfaces en bedieningsprocedures van TrainCarts waren belangrijke referenties voor dit project. SkyRail Suite is een onafhankelijk project: geen van beide plugins is vereist, het is geen officiële of door hen onderschreven versie en volledige compatibiliteit wordt niet geclaimd. De toepasselijke copyright- en licentievermelding van TrainCarts blijft behouden.

## 授权 / License / Licentie

Code and documentation: [MIT](LICENSE), `Copyright (c) 2026 Skyworld Minecraft Server contributors`.

维护联系 / Maintainer / Beheerder: [moerail](https://github.com/moerail).

Logo 与角色图片不适用代码 MIT 授权。SkyPCC 内置吉祥物已获准随包分发，这不等于将图片按 MIT 开放。

Logos and character artwork are outside the code's MIT license. Distribution of the bundled SkyPCC mascot is permitted; this does not MIT-license the artwork.

Logo's en personageafbeeldingen vallen niet onder de MIT-codelicentie. Verspreiding van de meegeleverde SkyPCC-mascotte is toegestaan; hierdoor valt de afbeelding niet onder MIT.

## 在真实服务器上体验 / See It in Action

SkyRail Suite 正在 **Skyworld Minecraft Server** 中运行和持续测试。欢迎加入服务器，或打开 SkyPCC 实时大屏，看看玩家建设的铁路和运行中的列车。

SkyRail Suite is running and being tested on **Skyworld Minecraft Server**. Join us in-game or explore the live SkyPCC dashboard to see our player-built railway network in operation.

> 这是实际部署与持续测试的展示，不代表所有功能已完成或不存在缺陷。目前 MA/EoA 仍处于影子阶段，不执行 ATP 制动监督。
>
> This is a live deployment, not a guarantee of completeness or reliability. MA/EoA currently operate in shadow mode without ATP braking supervision.

### 加入服务器 / Join the Server

**Minecraft Java Edition 26.2** · 无需安装铁路客户端模组 / No railway client mod required

| 服务 / Service | 地址 / Address |
| --- | --- |
| 生存服 / Survival server | `mc.skywor1d.cn` |
| 铁路测试服 / Railway test server | `irvine-filth.tun.ply.gg` |
| **SkyPCC 实时大屏 / Live railway dashboard** | **[打开大屏 / Open Dashboard](http://mc.skywor1d.cn:8765/)** |
| 皮肤站 / Skin & authentication service | [skin.deviy.cn](https://skin.deviy.cn) |

**测试服会删档，请勿存放需要长期保留的建筑或物品。**  
**The test server may be wiped. Do not use it for builds or items you want to preserve.**

登录请按服务器要求配置正版验证或外置登录；需要帮助可通过下方社区联系管理员。  
Configure official or external authentication as required by the server. Contact our community for help joining.

### 社区 / Community

- **QQ 群：204749370**
- **Telegram:** [Skyworld MC](https://t.me/skyworld_mc)
- **Discord:** [Join our Discord](https://discord.gg/bzM3tfm)
- **OOPZ:** [加入频道 / Join channel](https://oopz.cn/i/LExN5k) · `381998378`

### 关于 Skyworld / About Skyworld

**自 2013 年起，一直都在。**

目前开放生存服；博物馆服正在筹备中，计划保存并展示自 2014 年以来能够找回的世界存档，包括 LL 小镇。

**Still here, since 2013.**

Our survival server is open. A museum server is being prepared to preserve and showcase recoverable worlds dating back to 2014, including LL Town.

服务器不提供锁箱或圈地保护，请自行保管物品、尊重他人的建筑与劳动。经查实的恶意破坏将被封禁。

There is no chest-locking or land-claim protection. Look after your belongings and respect other players' work. Confirmed malicious griefing results in a ban.

### 支持服务器 / Support the Server

欢迎自愿支持日常网络与电力开支：  
Optional contributions towards our network and electricity costs:

**[爱发电 / Support Skyworld](https://afdian.com/a/skyworld)**

**[支持本人 / Buy me a coffee](https://buymeacoffee.com/moerail)**

我们的宗旨是让每个玩家在这里玩得开心。  
Our goal is simple: a place where everyone can enjoy playing.

*Somehow, this fucking thing is still here.*  
*Since 2013.*

## 联系作者 / Contact 
- **邮箱 / Email：me@saionjirin.com; saionjirin@outlook.com**
- **QQ群 / QQ Group: 204749370**
- **Telegram: [@saionjirin](https://t.me/saionjirin)**

# SkyRail Suite

<p align="center">
  <img src="logo.png" alt="SkyRail Suite" width="64" height="64">
</p>

**完整手册 / Full Manuals / Volledige handleidingen:** [中文](README.zh.md) | [English](README.en.md) | [Nederlands](README.nl.md)

面向 Minecraft / Folia 的列车运行、铁路基础设施、影子列控与调度显示套件。

A train-operation, railway-infrastructure, shadow train-control and dispatching-display suite for Minecraft / Folia.

Een suite voor treinbediening, spoorweginfrastructuur, schaduwtreinbeveiliging en verkeersleidingsweergave in Minecraft / Folia.

SkyRail Suite 延续 SkyTrain Suite 开发线；现有插件名、指令和数据目录不变。

## 组件 / Components / Onderdelen

| Plugin | Version | Responsibility |
| --- | --- | --- |
| SkyTrainFolia (STF) | 2.1.0-alpha.7 | Trains, driving, physics, signs and cab HMI |
| STCS | 2.2.0-alpha.1 | RailGraph, localisation, occupancy and shadow MA/EoA |
| SkyworldTrainAPI (STA) | 0.8.0 | Versioned inter-plugin contracts, telemetry and events |
| SkyPCC | 0.8.1 | Web track diagram, inspectors, event log and turnout control |

## 开始使用 / Getting Started / Aan de slag

从 [Releases](https://github.com/moerail/Skyworld-Railway/releases) 下载同一发布中的四个 JAR。当前适配基线：**Java 25，Shiroha / Folia 26.2**。安装前备份并先在测试服验证；完整安装、权限、指令、配置、教程与构建方法见上方三语手册，每份均可独立阅读。

Download the four matching JARs from Releases. Current baseline: **Java 25, Shiroha / Folia 26.2**. Back up and test before deployment. Each manual above includes installation, permissions, commands, configuration, tutorials and build instructions.

Download de vier bij elkaar horende JAR's via Releases. Huidige basis: **Java 25, Shiroha / Folia 26.2**. Maak een back-up en test vóór installatie. Elke handleiding hierboven bevat installatie, rechten, commando's, configuratie, voorbeelden en bouwinstructies.

## 开发版边界 / Alpha Limitations / Alfabeperkingen

- **中文：** MA/EoA 目前仅为影子计算与显示，ATP 不据此施加制动。司机失能 EB、手动 EB 与 RECOVERING 制动保持是独立的实际控制功能。本项目不是认证铁路安全系统。
- **English:** MA/EoA are currently calculated and displayed in shadow mode; ATP does not brake in response. Driver-loss EB, manual EB and RECOVERING brake hold are separate active functions. This is not a certified railway safety system.
- **Nederlands:** MA/EoA worden momenteel alleen in schaduwmodus berekend en weergegeven; ATP grijpt hierbij niet remmend in. Noodremming bij verlies van de machinist, handmatige noodremming en de remvasthouding in RECOVERING zijn afzonderlijke actieve functies. Dit is geen gecertificeerd spoorwegveiligheidssysteem.

## 致谢 / Acknowledgements / Dankwoord

**中文：** 感谢 [bergerkiller / Berger Healer](https://github.com/bergerhealer) 及 [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)、[TrainCarts](https://github.com/bergerhealer/TrainCarts) 的贡献者，为 Minecraft 插件生态与矿车铁路玩法所做的长期工作。TrainCarts 的牌子接口和运行流程为本项目提供了重要参考。SkyRail Suite 是独立项目，不要求安装 BKCommonLib 或 TrainCarts，也不代表二者的官方版本、认可或完整兼容实现；相关 TrainCarts 版权与许可声明继续保留。

**English:** Thank you to [bergerkiller / Berger Healer](https://github.com/bergerhealer) and the contributors to [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) and [TrainCarts](https://github.com/bergerhealer/TrainCarts) for their long-standing work on Minecraft plugins and minecart railways. TrainCarts' sign interfaces and operating workflows have been important references for this project. SkyRail Suite is an independent project: it does not require either plugin, is not an official or endorsed version of either, and does not claim complete compatibility. The applicable TrainCarts copyright and license notice is retained.

**Nederlands:** Met dank aan [bergerkiller / Berger Healer](https://github.com/bergerhealer) en de bijdragers aan [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) en [TrainCarts](https://github.com/bergerhealer/TrainCarts) voor hun jarenlange werk aan Minecraft-plugins en spoorwegen met mijnkarretjes. De bordinterfaces en bedieningsprocedures van TrainCarts waren belangrijke referenties voor dit project. SkyRail Suite is een onafhankelijk project: geen van beide plugins is vereist, het is geen officiële of door hen onderschreven versie en volledige compatibiliteit wordt niet geclaimd. De toepasselijke copyright- en licentievermelding van TrainCarts blijft behouden.

## 授权 / License / Licentie

Code and documentation: [MIT](LICENSE), `Copyright (c) 2026 Skyworld Minecraft Server contributors`.

维护联系 / Maintainer / Beheerder: [moerail](https://github.com/moerail).

Logo 与角色图片不适用代码 MIT 授权。SkyPCC 内置吉祥物已获准随包分发，这不等于将图片按 MIT 开放。

Logos and character artwork are outside the code's MIT license. Distribution of the bundled SkyPCC mascot is permitted; this does not MIT-license the artwork.

Logo's en personageafbeeldingen vallen niet onder de MIT-codelicentie. Verspreiding van de meegeleverde SkyPCC-mascotte is toegestaan; hierdoor valt de afbeelding niet onder MIT.# SkyRail Suite

面向 Minecraft / Folia 的列车、轨道基础设施、影子列控与调度显示套件。

[English](README.en.md) / [Nederlands](README.nl.md)

本目录是从当前 SkyTrain Suite 开发线整理出的独立源码工作区。**项目总名使用 SkyRail Suite，现有插件名、Java 包名、指令、数据目录和版本暂不改变**，避免仅为了整理目录而破坏兼容性。原开发目录与服务器数据不受影响。

> 当前 MA/EoA 仍是影子功能，ATP 不根据它施加制动。司机失能 EB 和 RECOVERING 制动保持是另外的实际控制功能。

## 目录结构

```text
SkyRail Suite/
  SkyTrainFolia/       列车、驾驶、物理、牌子、HMI
    src/main/java/
    src/main/resources/
    src/test/java/
  STCS/               基础设施、RailGraph、定位、影子 MA
    src/main/java/
    src/main/resources/
    src/test/java/
    tools/            图查看器与离线 Python 测试台
  STA/                共享 API 插件与契约测试
    src/main/java/
    src/main/resources/
    src/test/java/
  SkyPCC/             网页服务、前端与测试
    src/main/java/
    src/main/resources/web/
    src/test/java/
    tests/
    package.json
  shared/             编译进各插件的共用命令界面代码，不是第五个插件
  doc/                架构、构建、迁移与发布说明
  README.en.md        独立可读英文完整手册
  README.nl.md        独立可读荷兰语完整手册
  build.ps1           从源码构建四个插件并执行 Java 回归
  package-source.ps1  生成干净源码 ZIP，不携带服务器和构建输出
```

## 当前版本

| 插件 | 版本 |
| --- | --- |
| SkyTrainFolia | 2.1.0-alpha.7 |
| STCS | 2.2.0-alpha.1 |
| SkyworldTrainAPI (STA) | 0.8.0 |
| SkyPCC | 0.8.1 |

二进制文件名自动从各模块 `src/main/resources/plugin.yml` 读取，不在构建脚本内重复维护版本号。本次目录整理不改业务逻辑，也不新增运行依赖。

## 构建

需要 JDK 25，以及单独准备的、与本版本匹配的 Shiroha/Folia 26.2 服务端依赖。Minecraft 服务端及其第三方库不随源码分发。

在本目录使用 PowerShell：

```powershell
./build.ps1 -ServerRoot '/path/to/prepared-server' -JavaHome '/path/to/jdk-25'
```

也可设置 `JAVA_HOME` 后省略 `-JavaHome`。依赖默认取自服务端的 `versions/26.2/shiroha-26.2.jar` 和 `libraries/`；如文件位置不同，可传 `-ServerJarRelativePath`，但路径兼容不代表不同服务端实现兼容。

构建顺序为 STA → STF → STCS → PCC，不依赖旧版套件 JAR。Java 测试自动运行。输出进入 `artifacts/`，中间文件进入 `target/`，均已被 Git 忽略。脚本不启动服务器、不部署、不修改服务端文件。

## 网页与 Python 测试

```powershell
cd SkyPCC
npm install
npm test
npx playwright install chromium
npm run test:browser
```

`test:browser` 是当前界面回归；`test:browser:legacy` 保留以前的 UI 场景，其历史断言可能需要独立维护。也可通过 `PLAYWRIGHT_CHANNEL=msedge` 使用本机 Edge。测试输出仅写入模块 `target/`。

从仓库根目录执行离线算法测试：

```powershell
python -m unittest discover -s STCS/tools/testbench -p 'test_*.py'
python STCS/tools/testbench/railgraph_testbench.py
```

测试台使用 Python 标准库，图形界面需要 Tkinter。它不会连接游戏服务器；用“打开轨道图”载入自己的导出副本。三个可选实图回归默认跳过，可用 `STCS_TEST_GRAPH` 指向具有对应场景的导出副本。不要把生产服图、场景或占用账本提交到仓库。

## GitHub 发布

代码与文档采用 MIT 许可证，版权署名为 `Copyright (c) 2026 Skyworld Minecraft Server contributors`。维护联系账号：[moerail](https://github.com/moerail)。根目录 `LICENSE` 提供完整授权文本，构建时也会加入四个插件的 JAR。

Logo 与角色图片不包含在代码的 MIT 授权范围内，具体范围见 `ASSET-LICENSE.md`；公开包含图片的包前仍需核对图片授权。TrainCarts 原有版权与 MIT 声明保持不变，并继续随 STF 的 JAR 和源码分发。暂不列出调试参与者名单。

```powershell
git init -b main
git status --short
git add .
git diff --cached --stat
git diff --cached
# 确认许可证、图片授权、待提交文件和敏感信息后再提交
git commit -m "Prepare SkyRail Suite source tree"
# 在 GitHub 建立空仓库，然后使用自己的实际远程地址
git remote add origin https://github.com/YOUR_ACCOUNT/SkyRail-Suite.git
git push -u origin main
```

以上是待执行工作流，不表示已创建远程仓库或已推送。不要直接上传整个旧 Minecraft Plugin 工作区；新目录仅保留源码、默认配置、必要资源与测试。

若仅向其他开发者分享源码：

```powershell
./package-source.ps1
```

生成 `dist/SkyRail-Suite-source-<timestamp>.zip`。压缩包不包含 `.git`、JAR、服务器数据、依赖库、缓存或构建输出。

## 说明文档

- [完整英文手册](README.en.md) / [完整荷兰语手册](README.nl.md)：可单独发送，不依赖其他文档。
- [架构与维护边界](doc/ARCHITECTURE.md)
- [发布与授权检查](doc/PUBLISHING.md)
- [迁移记录](doc/MIGRATION.md)
- [本次验证结果与已知旧测试缺口](doc/VALIDATION.md)

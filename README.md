# SkyRail Suite

面向 Minecraft / Folia 的列车、轨道基础设施、影子列控与调度显示套件。

[English](README.en.md) / [Nederlands](README.nl.md)：可单独发送，不依赖其他文档。

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

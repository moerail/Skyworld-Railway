# 本地目录结构 — 2026-10-09

主仓库从 `SkyRail Suite` 改名为 `Skyrail_Suite`，Git 历史和远端配置保留。

```text
Minecraft Plugin/
├── Skyrail_Suite/
│   ├── SkyTrainFolia/
│   ├── STCS/
│   ├── STA/
│   ├── SkyPCC/
│   ├── Common/
│   │   ├── README*.md、CHANGELOG.md、LICENSE、logo.png
│   │   ├── doc/               手册、设计、发布说明
│   │   ├── shared/            四插件共用源码
│   │   ├── build.ps1、package-source.ps1
│   │   ├── artifacts/         构建产物及旧 Latest 历史
│   │   ├── dist/              源码包、桌面调度台包
│   │   ├── target/            编译及测试中间文件
│   │   └── logs/
│   ├── Latest/                四个最近成功构建的插件 JAR、SHA256.json
│   └── README.md              导航入口
├── Archive/Skyrail_Suite/     旧版源码、发布包、早期资料及临时副本
└── Test_Server/              保留原位
```

`Latest` 只在 26.2、26.3 两套测试全部通过后更新。之前的插件包和校验文件移至 `Common/artifacts/latest-history`，不删除历史构建。生成的 JAR、ZIP、测试文件继续由 Git 忽略。

构建脚本从仓库根目录调用 `Common/build.ps1`；源码打包调用 `Common/package-source.ps1`。STA 桌面调度台源码仍在 `STA/tools`，桌面包输出至 `Common/dist`。文档中的相对链接已经按新位置调整。

主仓库根目录保留 `.git`、`.gitignore` 和 `.gitattributes` 作为 Git 必需元数据。旧版本归档保留其原有目录名称及 Git 历史。目录移动清单位于仓库外 `Archive/Skyrail_Suite/directory-moves-2026-10-09.json`，可以据此还原位置。

本次只整理本地目录和相应路径，尚未提交或推送目录结构变更。

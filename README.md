# Skyrail Suite

当前主仓库。四个插件独立存放，套件共通资料集中在 `Common`。

| 文件夹 | 内容 |
| --- | --- |
| [SkyTrainFolia](SkyTrainFolia/) | 列车实体、运动、驾驶与车载显示 |
| [STCS](STCS/) | 轨道图、占用、MA 与道岔控制 |
| [STA](STA/) | 插件间 API 与桌面调度工具 |
| [SkyPCC](SkyPCC/) | 网页调度界面 |
| [Common](Common/) | 完整手册、更新记录、共享代码、构建与打包脚本 |
| `Latest`（本地构建后生成） | 最近一次双版本验证通过的四个插件 JAR 和校验值；不纳入源码提交 |

完整手册：[中文](Common/README.zh.md) · [English](Common/README.en.md) · [Français](Common/README.fr.md) · [日本語](Common/README.ja.md) · [Nederlands](Common/README.nl.md)。[项目首页](Common/README.md) · [更新记录](Common/CHANGELOG.md) · [MIT 许可](Common/LICENSE)。

## 最新修改：驾驶员交接与 MA — 2026-10-24

- 司机下车、掉线或释放驾驶权后，自动撤销旧 MA，车载模式回到 `SB` 并保持制动。前方预约在确认停稳、定位和列车完整性后自动释放，车身占用继续保留。
- 下一位司机重新 `/st drive` 后申请 `/stcs ma fs`，无需替上一位执行 `ma release`。`/stcs ma demand` 保留为兼容别名；帮助、命令补全和驾驶室按钮统一使用 `fs`。
- 当前源码版本：STCS **4.1.1**、SkyTrainFolia **4.0.9**、STA **2.2.0**、SkyPCC **2.1.0**。本次交接修改需配套更新 STCS 与 SkyTrainFolia。
- Shiroha 26.2、26.3 各 **87 项 Java 测试通过**，实服验收待完成。[司机手册](Common/doc/driver/README.zh.md)

从本目录运行构建，测试全部通过后会自动更新 `Latest`：

```powershell
& .\Common\build.ps1 `
  -ServerRoot '..\Test_Server\STF_Test_26.2' `
  -ServerRoot26_3 '..\Test_Server\STF_Test_26.3' `
  -JavaHome 'C:\Program Files\Java\jdk-25.0.4'
```

旧版套件、旧版单插件与早期发布包已集中保存在上级 `Archive/Skyrail_Suite`。服务器安装、世界数据和其他独立项目保留原位。目录整理记录位于 [Common/doc/DIRECTORY-LAYOUT.zh.md](Common/doc/DIRECTORY-LAYOUT.zh.md)。

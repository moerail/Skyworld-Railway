# 发布前检查

## 尚需作者决定

1. 代码许可证已确定为 MIT，版权署名为 `Copyright (c) 2026 Skyworld Minecraft Server contributors`，维护联系账号为 GitHub 的 `moerail`。根目录 LICENSE 与 ASSET-LICENSE.md 会进入源码包和四个插件的 JAR。仍需核对第三方代码来源与授权兼容性。
2. 图片许可。PCC 的 day_logo.png、night_logo.png 是运行资源，本次保留；公开前确认角色形象、字体、商标及图片制作来源允许这样分发。
3. 第三方代码。现有 `SkyTrainFolia/src/main/resources/META-INF/NOTICE-TrainCarts.txt` 已记录自动牌子语法、红石与牌柱行为参考的 TC 提交 `9813810aa7e751d3a00d3d087186d44f6e90df10`，并附 MIT 声明。本次逐字保留且继续打入 JAR/源码包。它不是整个套件的总许可证；其余代码来源仍需核对，不能据此宣称全部源码原创或已完成许可审计。
4. 仓库公开范围。可以先建立私人仓库给开发者评审，完成授权确认后再公开。

## 不上传

- Minecraft 服务端、第三方依赖 JAR、构建 JAR 和历史 ZIP。
- 生产世界、railgraph.json、occupancy-ledger.json、玩家 UUID/名单、服务器日志。
- PCC 实际 token、SSH key、环境变量文件和本机绝对路径。
- node_modules、target、artifacts、dist、Python 缓存、编辑器设置。

默认配置里的空 control-token 可以提交。不要把服务器实际 config.yml 覆盖进 src/main/resources。

## 提交流程

在新目录初始化 Git，确认 `.gitignore` 生效后审查全部暂存差异，再提交。没有确认前不要直接 `git push`。本次整理不自动创建 GitHub 仓库、不创建远程地址、不推送。

发行版二进制可在验证后单独上传 GitHub Releases；不要作为日常源码提交。不要因构建产生旧产物就把整个 artifacts 目录打进发行包，应按当前版本选择四个 JAR。

## 源码压缩包

`package-source.ps1` 按白名单打包根文件、四模块、shared 和 doc，并排除缓存/产物。压缩包不是对源码版权、密钥或资产来源的认证；发布前仍需人工检查。

## 开源贡献

建议提交 PR 时说明影响模块、协议兼容性、测试与实服复现。列控相关修改必须说明未知/过期/卸载如何处理，不能为了通过演示删除占用证据。目录重命名不应顺带修改 plugin.yml 身份或持久化字段。

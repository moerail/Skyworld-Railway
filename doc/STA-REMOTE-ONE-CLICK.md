# STA 桌面调度台：Windows 服务端一键配置

第一次使用建议阅读 [完整入门 README 与 FAQ](../STA/tools/README.md)，包含地址选择、双击启动、令牌丢失替换和常见报错处理。

本工具配置 **STA Remote 调度连接**。它生成服务端证书、管理员令牌、STA 配置和新的启动入口；不改动原有 `run.bat`，也不修改 STF、STCS、SkyPCC 的配置或数据。适用于 STA 2.1.2 和 Windows `run.bat` 服务端。

## 使用前

1. 停止 Minecraft 服务端，并备份整个服务端目录。已配置过 STA Remote 的服务器不要重新运行首次配置脚本；脚本会拒绝覆盖现有证书和凭据。
2. 确认 `plugins` 中装有 `SkyworldTrainAPI-2.1.2.jar`，服务端目录中有 `run.bat`，机器上有 JDK 的 `keytool.exe`。脚本优先读取 `JAVA_HOME`，也能识别 `run.bat` 中带引号的完整 `...\bin\java.exe` 路径，最后尝试 PATH 中的 `keytool.exe`。
3. 在**服务端机器上**双击 [`STA/tools/setup_sta_remote.cmd`](../STA/tools/setup_sta_remote.cmd)，输入包含 `run.bat` 的服务端文件夹。

本机使用调度台时，第二个问题直接按 Enter，连接仅监听 `127.0.0.1`。其他电脑需要连接时，填入该电脑可以访问的**服务端地址**，脚本将监听 `0.0.0.0`。随后只放行管理员所需的 TCP 端口 `8766`，并限制可访问的来源地址；不要把个人令牌公开。

也可以直接运行 PowerShell 脚本：

```powershell
& .\STA\tools\setup_sta_remote.ps1 -ServerRoot 'D:\Skyworld\Skyworld\2-MainServer'
```

跨电脑连接示例：

```powershell
& .\STA\tools\setup_sta_remote.ps1 -ServerRoot 'D:\Skyworld\Skyworld\2-MainServer' -BindAddress 0.0.0.0 -ClientHost 'your-server.example.com'
```

可选参数有 `-AdminId`（默认 `dispatcher1`）、`-Port`（默认 `8766`）、`-JavaHome` 和 `-LaunchScript`（默认 `run.bat`）。在 Windows PowerShell 中运行；若系统阻止脚本执行，可通过 `.cmd` 入口运行。

## 配置完成后

1. 将窗口显示的**个人令牌**单独保存到可信的密码管理器。它只显示这一次，配置文件只保存令牌的 SHA-256 摘要。
2. 使用服务端新生成的 `start-sta-remote.cmd` 启动 Minecraft。以后都通过这个入口启动；原有 `run.bat` 仍由它调用。启动入口与首次配置必须使用同一个 Windows 用户账户，因为证书密码由该账户的 Windows DPAPI 保护。
3. 将 `plugins\SkyworldTrainAPI\SkyRail-Dispatcher-profile.json` 复制到调度台 `sta_dispatcher.py`、`.pyz` 或 `.exe` 的**同一文件夹**。这个 JSON 只含连接地址、端口、证书 SHA-256 指纹和管理员 ID，不含令牌。调度台启动后会自动填好这些字段；输入个人令牌再连接。也可用 `--profile <文件路径>` 指定其他位置。
4. 服务端日志应出现 `STA Remote TLS listening`。无法连接时，检查监听地址、端口、防火墙、连接资料和令牌。证书指纹不一致时不要忽略提示，核实服务器证书和复制过来的资料。

首次配置会备份已有 `plugins\SkyworldTrainAPI\config.yml`，新增 `remote-server.p12` 和 `.sta-remote-password.dpapi`。请把服务端目录备份当作敏感数据保管，不要上传证书私钥或密码文件。脚本不修复 STCS 的 `shadow-occupancy.json`；如果此前因掉电损坏，应先按 STCS 日志处理该文件，再启动全套插件。

## 当前测试范围

脚本已在临时 Windows `run.bat` 服务端目录验证证书生成、配置更新、旧配置备份、启动入口、连接资料和重复执行保护。正式服务器上的网络连接与实际调度命令仍需在部署后检查。

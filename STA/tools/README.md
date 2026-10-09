# STA 远程调度大屏：从零配置与常见问题

本次小版本为 **STF 4.0.7 / STCS 4.0.5 / STA 2.1.3 / SkyPCC 2.0.2**，Python 调度台为 **2.1.3**。停车后，调度转岔会先取得车端停车保持确认，再释放相关 MA 预约、转岔并重算 MA，司机无需先 release。网页和 Python 均新增“撤销所选列车 MA → TR”：手动列车运行或停车都进入 TR 并制动；运行中的旧预约保留到全列确认停稳，车身占用始终保留。影子通道也执行此项主动调度停车命令。司机停稳后执行 `/stcs ma ack`、`/stcs ma release`，再重新申请 MA。失败／超时不代表撤销完成。需配套更新已安装插件并刷新客户端，保留原证书和令牌；实服验收待完成。 [更新说明](../../doc/releases/suite-v4.0.7.md)

适用于 STA 2.1.3 桌面调度台、Windows 服务端和 `run.bat` 启动方式。下面以服务端目录 `D:\Skyworld\Skyworld\2-MainServer` 为例；如果你的目录不同，请替换为实际路径。

**已经成功配置过的服务器，直接看第 4 节启动调度台。令牌丢失看第 6 节，不要重复运行首次配置。**

## 1. 先分清哪些文件放在哪台电脑

| 文件 | 放在哪里 | 用途 |
| --- | --- | --- |
| `setup_sta_remote.cmd` 和 `setup_sta_remote.ps1` | 服务端电脑，同一文件夹 | 首次配置入口，两个文件必须一起复制 |
| `generate_sta_admin_token.bat` | 可信的 Windows 电脑 | 新增管理员或重新生成令牌 |
| `start-sta-remote.cmd` 和 `start-sta-remote.ps1` | 首次配置后自动生成在服务端根目录 | 以后用这个 CMD 启动服务端 |
| `SkyRail-Dispatcher-2.1.3.pyz` | 使用大屏的电脑 | 打包后的 Python 调度台，便于单独复制 |
| `sta_dispatcher.py` | 源码仓库的 `STA\tools` | 从完整源码启动调度台；不要只复制这一个 PY 文件 |
| `SkyRail-Dispatcher-profile.json` | 放到调度台启动文件旁边 | 自动填入地址、端口、证书指纹、管理员 ID |

服务端需要安装 `SkyworldTrainAPI-2.1.3.jar`。桌面显示的轨道、列车和调度功能还依赖服务端相关组件正常运行。配置脚本只负责 STA Remote 连接，不会安装四个插件，也不会修复 STCS 数据。

使用 `.pyz` 或源码的电脑需要 **Python 3.11 或更新版本，包含 Tkinter**。如果拿到的是已打包好的 Windows `.exe`，客户端无需另装 Python。

## 2. 地址到底填什么？

| 调度台的位置 | 首次配置时填写的连接地址 | 说明 |
| --- | --- | --- |
| 与 Minecraft 服务端在同一台电脑 | 直接按 Enter | 使用 `127.0.0.1`，只允许本机连接 |
| 同一局域网的另一台电脑 | 服务器的局域网 IPv4，例如 `192.168.1.100` | 在服务端电脑运行 `ipconfig` 查看实际地址 |
| 从互联网连接 | 服务器公网 IP 或实际解析到服务器的域名 | 需要防火墙放行；路由器后面的服务器通常还需端口转发 |

**不要填写 `0.0.0.0`。** 它表示服务端监听所有网卡，不能作为客户端连接目标。填写实际 IP 或域名后，首次配置脚本会自动使用 `0.0.0.0` 作为监听地址。

地址中不要加 `http://`、`https://` 或 `:8766`。地址和端口分别填写。这条连接使用 STA 的 TLS/TCP 协议，不是网页服务；Minecraft 游戏端口和调度端口也是两回事。

## 3. 首次配置服务端

### 方法 A：双击配置，最省事

1. 停止 Minecraft 服务端，备份服务端目录。
2. 把本目录的 `setup_sta_remote.cmd` 和 `setup_sta_remote.ps1` 一起复制到服务端电脑，例如：

   ```text
   D:\Skyworld\Skyworld\2-MainServer\setup\sta-remote\setup_sta_remote.cmd
   D:\Skyworld\Skyworld\2-MainServer\setup\sta-remote\setup_sta_remote.ps1
   ```

3. 双击 `setup_sta_remote.cmd`。
4. 询问服务端文件夹时，输入 `D:\Skyworld\Skyworld\2-MainServer`，不要填 `plugins` 或 `setup` 子目录。若使用了预先写好目录的 CMD，此问题会跳过。
5. 按第 2 节填写连接地址。
6. 成功后，**先保存窗口里的 `Personal token`，再关闭窗口**。这是调度台登录用的个人令牌，只显示一次；建议存入密码管理器。
7. 回到服务端根目录，双击新生成的 `start-sta-remote.cmd` 启动 Minecraft。
8. 查看日志，确认出现 `STA Remote TLS listening`。

此后每次启动 Minecraft 都使用 `start-sta-remote.cmd`。它会准备证书密码，然后调用原来的 `run.bat`。使用与首次配置相同的 Windows 账户启动。

### 方法 B：在 PowerShell 中一条命令配置

在放有 `setup_sta_remote.ps1` 的文件夹打开 PowerShell。以下命令用于首次配置；三个示例只选择适合自己的一种执行。

仅本机连接：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup_sta_remote.ps1 -ServerRoot 'D:\Skyworld\Skyworld\2-MainServer'
```

局域网连接，把示例 IP 换成服务器实际 IP：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup_sta_remote.ps1 -ServerRoot 'D:\Skyworld\Skyworld\2-MainServer' -BindAddress 0.0.0.0 -ClientHost '192.168.1.100'
```

指定 JDK、管理员 ID 和端口，把 JDK 路径和域名换成真实值：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup_sta_remote.ps1 -ServerRoot 'D:\Skyworld\Skyworld\2-MainServer' -JavaHome 'C:\Program Files\Java\jdk-25.0.4' -AdminId dispatcher1 -Port 8766 -BindAddress 0.0.0.0 -ClientHost 'your-server.example.com'
```

脚本会备份已有 STA `config.yml`，拒绝覆盖检测到的证书、启动入口或已有管理员配置。看到已有配置提示时，先检查现有文件，不要删除文件来强行重跑。

## 4. 启动远程大屏

### 推荐：使用打包好的 PYZ

在调度电脑上新建一个文件夹，例如 `D:\SkyRail-Dispatcher`，放入：

```text
D:\SkyRail-Dispatcher\
  SkyRail-Dispatcher-2.1.3.pyz
  SkyRail-Dispatcher-profile.json
```

其中 JSON 从服务端这个位置复制：

```text
D:\Skyworld\Skyworld\2-MainServer\plugins\SkyworldTrainAPI\SkyRail-Dispatcher-profile.json
```

在调度台文件夹打开终端，运行：

```powershell
python .\SkyRail-Dispatcher-2.1.3.pyz
```

如果 Windows 安装了 Python 启动器，也可以使用：

```powershell
py -3 .\SkyRail-Dispatcher-2.1.3.pyz
```

打开窗口后，检查自动填好的服务器地址和管理员 ID，在“个人令牌”中粘贴令牌，点击“连接”。客户端不会保存令牌，重新启动后需要再次输入。

### 使用完整源码

保留仓库目录结构，在仓库根目录运行：

```powershell
python .\STA\tools\sta_dispatcher.py
```

JSON 放在 `STA\tools` 中。源码入口会引用同目录模块及仓库内的 RailGraph 模块，不能只拿走 `sta_dispatcher.py` 一个文件。

### 想要双击启动大屏

把下面内容存成 `启动调度台.cmd`，与 PYZ 放在一起。保存时确认扩展名是 `.cmd`，不是 `.cmd.txt`。

```bat
@echo off
cd /d "%~dp0"
python ".\SkyRail-Dispatcher-2.1.3.pyz"
set "DISPATCH_EXIT=%errorlevel%"
if not "%DISPATCH_EXIT%"=="0" pause
exit /b %DISPATCH_EXIT%
```

如果你的电脑只能通过 `py -3` 启动 Python，将上面的 `python` 改为 `py -3`。也可以把 `python` 换成带双引号的 `python.exe` 完整路径。

### 有多台服务器

分别保存连接资料，并在启动时指定：

```powershell
python .\SkyRail-Dispatcher-2.1.3.pyz --profile 'D:\SkyRail-Dispatcher\profiles\main-server.json'
```

每台服务器要使用对应的证书指纹、管理员 ID 和令牌。

### 大屏显示怎么调整（2.1.2）

顶部“字号”可选 8–24，手工输入后按 Enter；它同时调整界面和地图文字。右侧页签可切换“调度”“线路”“运行事件”。调度内容较长时使用右侧滚动条。

“线路”列出当前世界已确认的线路。默认正线与侧线都不额外涂色，用线宽区分；选中线路后点“设置临时颜色”选择颜色，点“恢复无着色”清除。设置只对当前程序会话有效，关闭重开恢复默认。线路颜色不会盖过占用／预约图层。

标签会自动避让；密集区域实在放不下时，会提示隐藏数量，滚轮放大或右键拖动地图后重新排布。

“运行事件”显示与网页 PCC 同源的事件记录，时间来自服务端事件，按调度电脑本地时区显示。可勾选“仅看警告”。这一功能需要 STA 2.1.2 服务端，旧版会提示升级。升级只需停服替换 STA JAR，保留配置、证书、连接资料和令牌，不需要重新 setup。

## 5. 连接资料、令牌和证书有什么区别？

| 内容 | 用在哪里 | 能否发给调度员 |
| --- | --- | --- |
| 服务器地址、端口 | 调度台连接字段 | 可以 |
| 证书 SHA-256 指纹 | 调度台核实服务器身份 | 可以，通过可信渠道传递 |
| 管理员 ID，例如 `dispatcher1` | 调度台管理员字段 | 可以 |
| 个人令牌 / Personal token | 调度台个人令牌字段 | 只交给对应管理员 |
| 令牌 SHA-256 摘要 | 服务端 `remote.admins` | 保存在服务端配置；不能拿它当令牌登录 |
| `remote-server.p12` | 服务端证书私钥 | 不要发给客户端 |
| `.sta-remote-password.dpapi` | 服务端启动入口解密证书密码 | 保留在服务端；与创建它的 Windows 账户关联 |

连接 JSON 不含令牌。不要手工向 JSON 添加 `token` 字段，调度台会拒绝不符合格式的资料。

## 6. 窗口关了，令牌丢失怎么办？

原令牌无法从摘要或连接 JSON 中恢复。生成新的令牌，并替换对应管理员的摘要即可，无需重新生成证书。

1. 双击 `generate_sta_admin_token.bat`。
2. 管理员 ID 输入原来的 `dispatcher1`，或实际使用的 ID。
3. 保存输出的 `Client personal token`，这是新的登录令牌。
4. 停止服务端，打开：

   ```text
   D:\Skyworld\Skyworld\2-MainServer\plugins\SkyworldTrainAPI\config.yml
   ```

5. 找到 `remote` 下的 `admins`，把该 ID 的旧摘要替换为生成器输出的 64 位摘要。只替换对应行，保留其他管理员：

   ```yaml
   remote:
     # 这里保留原来的 enabled、bind-address、port、keystore 等配置
     admins:
       dispatcher1: "这里替换为生成器输出的64位SHA-256摘要"
   ```

   上面是结构说明，不要整段覆盖原配置；占位文字不能用于登录。YAML 缩进使用空格，不要使用 Tab。

6. 用 `start-sta-remote.cmd` 启动服务端。
7. 调度台输入新个人令牌连接。新配置加载后，旧令牌失效。

新增调度员也使用此生成器，但填写新的 ID，在 `admins` 下增加一行。新调度员在客户端填写自己的 ID 和令牌；连接 JSON 中的 `admin` 也可以改成新 ID。

## 7. 常见问题 FAQ

### 提示 `Invalid client host`

最常见原因是填写了 `0.0.0.0`。请按第 2 节填写实际 IP 或域名。不要加协议头或端口。此项检查发生在配置写入前，可以修正地址后重试。

### 提示找不到 `setup_sta_remote.ps1`

CMD 和 PS1 必须放在同一文件夹，文件名必须与 CMD 中引用的名称一致。不要把 `setup_sta_remote.ps1` 改成其他名字，也检查是否被保存成 `.ps1.txt`。

### 提示找不到 `run.bat`

服务端目录要填包含 `run.bat` 的根目录。如果实际文件叫 `start.bat`，使用 PowerShell 方法并追加 `-LaunchScript start.bat`。该参数只填根目录内的文件名。

### 提示找不到 `keytool` 或要求 `-JavaHome`

安装的 Java 需要包含 JDK 工具。脚本会先检查 `JAVA_HOME`，然后尝试识别 `run.bat` 中带引号的完整 Java 路径，最后检查 PATH。最直接的方法是在命令中添加 `-JavaHome '实际JDK目录'`，填到 JDK 根目录，不要填到 `bin` 或 `java.exe`。

### 提示已有配置，不能再次 setup

首次配置脚本用来创建新连接，不能当作日常启动脚本。正常启动用 `start-sta-remote.cmd`；丢令牌看第 6 节；修改地址看下面的对应问题。保留现有证书和密码文件。

### 提示无法解密证书密码

确认使用首次配置时的 Windows 用户账户，并且 `.sta-remote-password.dpapi` 文件仍在。把整套文件搬到另一台电脑，或改用其他账户、服务账户启动，都可能无法解密。不要删除唯一的证书和密码文件；先恢复原账户环境或有效备份。

### 直接运行 `run.bat` 后，Minecraft 正常但大屏连不上

通过自动配置生成的环境需要 `start-sta-remote.cmd` 提供证书密码。停止服务端，再使用新入口启动，并检查 STA Remote 的启动日志。

### 双击 PY 文件闪退，或者 `python` 不是命令

先在终端运行，保留错误信息。尝试 `python --version` 或 `py -3 --version`；如果都失败，安装含 Tkinter 的 Python，或使用已打包的 EXE。不要把 `sta_remote.py` 当作大屏入口，它是网络模块；入口是 `sta_dispatcher.py` 或 PYZ。

### 提示缺少 `tkinter`，或 `Can't find a usable init.tcl`

当前 Python 没有完整的 Tcl/Tk 环境。用 `python -m tkinter` 检查，应能弹出测试窗口。修复或换用含 Tcl/Tk 的完整 Python 安装，不要只复制 `python.exe`。如有已打包的 EXE，也可使用它。

### 提示缺少 `railgraph_simulation`、`dispatcher_profile` 等模块

源码文件复制不完整。保留整个仓库目录结构运行，或者使用本次更新后生成的单文件 PYZ。旧 PYZ 或 EXE 不会因旁边放了新源码而自动升级。

### 大屏没有自动填入连接信息

检查文件名是否精确为 `SkyRail-Dispatcher-profile.json`，并与实际启动的 PYZ、EXE 或 `sta_dispatcher.py` 在同一文件夹。检查是否用了支持连接资料的新客户端。也可用 `--profile` 指定 JSON 完整路径。

### 出现超时、连接被拒绝

依次检查：服务端已启动；日志有 `STA Remote TLS listening`；客户端地址和端口正确；其他电脑访问时监听地址不是 `127.0.0.1`；防火墙和网络允许 TCP `8766`。

可在客户端 PowerShell 运行，替换为实际服务端地址：

```powershell
Test-NetConnection -ComputerName 192.168.1.100 -Port 8766
```

`TcpTestSucceeded: True` 只说明 TCP 能连通，不代表证书或令牌已经正确。跨公网还要检查云安全组、路由器端口转发及运营商是否提供可入站的公网地址。只放行实际需要访问的管理员来源。

### 提示 `Authentication rejected`

确认管理员 ID 和 `remote.admins` 中的名称完全一致；客户端填的是个人令牌，不是摘要；复制时没有附带空格；更新摘要后已重启服务端。令牌丢失按第 6 节替换。

### 提示 `Server certificate fingerprint mismatch`

连接资料与服务器实际证书不一致，也可能连错服务器。核实目标服务器和证书来源，重新取得可信的指纹或匹配的连接资料；不要通过关闭校验来绕过错误。手动换过证书时，旧 JSON 不会自动更新。

### 原来仅本机连接，现在想让其他电脑连接

停服后，把 STA `config.yml` 的 `remote.bind-address` 改为 `0.0.0.0`，保留证书、密码变量和管理员摘要。将客户端 JSON 的 `host` 改为服务器实际 IP 或域名，放行所需端口，然后通过 `start-sta-remote.cmd` 重启。不要为了改地址重跑首次配置。

### 服务器 IP 或端口变了

IP 改变时更新客户端 JSON 的 `host`。端口改变时还需停服修改服务端 `remote.port`、客户端 JSON 的 `port`，并同步防火墙或端口转发。地址改变本身不要求更换证书或令牌，因为客户端按证书指纹核验身份。

### 大屏能连接，但地图空白或操作提示服务不可用

网络连接成功不代表轨道图、列车快照或 STCS 已准备好。检查服务端组件启动日志、RailGraph 数据和窗口中的世界选择。调度操作仍受服务端占用、MA、位置和状态检查约束，连接成功不会取消这些检查。

### STCS 因掉电报 `Invalid shadow occupancy`，重新 setup 能修好吗？

不能。那是 STCS 占用账本加载问题，与 STA Remote 登录配置不同。保留损坏文件和备份，根据 STCS 日志处理；不要通过清空占用账本来让调度连接恢复。

## 8. 备份与当前验证范围

备份整个 `plugins\SkyworldTrainAPI` 目录及服务端根目录的两个 `start-sta-remote` 文件。证书和加密密码文件属于敏感服务端资料，不能随客户端一起分发。个人令牌单独保存在可信位置；DPAPI 密码文件的普通复制不保证能在另一台电脑或另一账户下解密。

本次工具已通过调度台单元与界面测试、PYZ 打包启动检查，以及临时 Windows 服务端目录中的配置、备份、证书指纹、启动退出码和重复执行保护检查。正式网络连通性及实际调度操作需在自己的服务端验证。

更多背景：[STA 一键配置说明](../../doc/STA-REMOTE-ONE-CLICK.md)、[STA 2.1.2 调度台说明](../../doc/releases/sta-v2.1.2-dispatcher-map.md)。

# SkyRail desktop dispatcher / 桌面调度台

Current client and server: **STA 2.1.1**. Requires Python **3.11+** with Tkinter for source/zipapp use. This optional administrator desk uses TLS, a pinned certificate fingerprint and a per-operator token; the server listener is disabled by default. Do not commit a keystore, private token or password. [Server setup](../../doc/releases/sta-v2.1.0-remote-dispatcher.md) · [2.1.1 map update](../../doc/releases/sta-v2.1.1-dispatcher-map.md).

从仓库根目录启动 / Start from the repository root:

```powershell
python STA/tools/sta_dispatcher.py
```

Enter the host, TLS port (default 8766), independently verified SHA-256 certificate fingerprint, administrator ID and personal token. The desk does not save the token. Configure the matching STA 2.1.1 server as described in the setup guide; earlier 2.1.0 instructions remain applicable to certificates and tokens.

The four-language day/night map shows confirmed line assignments, fresh occupancy/reservations, switch directions, train arrows and selected-train details. Stale or unavailable occupancy returns to an unknown/dashed state. Switch operations and SR approval still pass through STCS checks; a map preview or accepted request is not an MA or confirmed point conversion.

地图支持中英法日四语言、昼夜主题、实时占用/预约、道岔方向和列车详情。失去新鲜数据后显示未知状态。正确鉴权也不能绕过 STCS 的占用、道岔与 MA 检查。

Build a zipapp / 打包为单文件 Python 程序:

```powershell
python STA/tools/package_dispatcher.py
python dist/SkyRail-Dispatcher-2.1.1.pyz
```

The package includes shared geometry from `STCS/tools/testbench`. To build a Windows executable, use a full Python installation with Tcl/Tk and PyInstaller, then run `STA/tools/package_dispatcher_exe.ps1`; output goes to `dist/`. Generated packages are excluded from Git.

Run map and hidden-window GUI tests / 测试:

```powershell
python -m unittest discover -s STA/tools -p "test_*.py"
```

For each operator, `generate_sta_admin_token.bat` generates a fresh personal token and the SHA-256 configuration entry. It does not write the token to disk or edit server configuration. Send tokens privately and verify the certificate fingerprint before connecting.

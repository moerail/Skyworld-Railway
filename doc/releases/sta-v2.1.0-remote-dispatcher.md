# STA 2.1.0 — native remote dispatcher candidate

STA 2.1.0 adds an optional TLS 1.3 TCP listener and a Python/Tkinter administrator desk. It uses no HTTP, HTTPS, RCON or browser. An allowed TCP port such as 8766 is needed for direct access; incoming 80/443 ports are unnecessary. This is a test-server candidate, not a production approval.

## Authority and protocol

- The listener is off by default. STA v5/v6 in-process contracts remain unchanged.
- Authenticated operators can read RailGraph and train snapshots, inspect/change a switch, list pending SR requests and approve an SR to a selected node.
- Every switch change has a request UUID, graph revision and expected state. STA obtains coordinates from the latest RailGraph. STCS retains its occupancy, MA, lock and physical-position checks. PENDING and UNCONFIRMED never mean a confirmed conversion.
- SR approval requires a fresh operational snapshot and a currently pending train. STCS checks position, stop state, route and conflicts.
- TLS 1.3, pinned server certificate fingerprint, per-operator token digests, eight-client limit, bounded requests and mutation rate limits are mandatory. Share the fingerprint through a trusted channel.

## Test-server setup on Windows

1. Back up the test server and replace only the STA JAR with SkyworldTrainAPI-2.1.0.jar. Start once to create plugins/SkyworldTrainAPI/config.yml, then stop.
2. Generate a PKCS#12 keypair in plugins/SkyworldTrainAPI/remote-server.p12 using JDK 25 keytool. Keep the key and password private:

~~~powershell
keytool -genkeypair -alias sta-remote -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12 -keystore plugins/SkyworldTrainAPI/remote-server.p12 -dname "CN=SkyRail-STA"
~~~
3. Set SKYRAIL_STA_KEYSTORE_PASSWORD in the server startup process environment. In a Windows launch script, set it before launching Java and restrict access to that script.
4. Generate a different random token for each administrator. On Windows, copy `STA/tools/generate_sta_admin_token.bat` to the server machine and double-click it. Enter the administrator ID (or press Enter for `dispatcher1`). The window prints the personal token for the desktop client and a SHA-256 line to paste under `remote.admins` in `plugins/SkyworldTrainAPI/config.yml`. Run it again for each administrator, using a different ID. The tool does not save the personal token or edit the configuration. Send each personal token privately.

   If you prefer Python, the equivalent is:

~~~python
import secrets, hashlib
token = secrets.token_urlsafe(48)
print(token)
print(hashlib.sha256(token.encode()).hexdigest())
~~~

5. Set remote.enabled to true, remote.bind-address to 0.0.0.0 and remote.port to 8766 for direct remote access. Restrict inbound TCP 8766 to administrator IPs where possible. For local testing, keep 127.0.0.1.
6. Read the certificate SHA-256 fingerprint with keytool -list -v -storetype PKCS12 -keystore plugins/SkyworldTrainAPI/remote-server.p12. Keytool prompts for the password. Send the fingerprint through a trusted channel.
7. Start the server and confirm the log says STA Remote TLS listening. Invalid configuration leaves the network listener closed while in-process STA services remain available.

Example config:

~~~yaml
remote:
  enabled: true
  bind-address: 0.0.0.0
  port: 8766
  keystore: remote-server.p12
  keystore-password-env: SKYRAIL_STA_KEYSTORE_PASSWORD
  admins:
    dispatcher1: "<64 hexadecimal SHA-256 characters>"
~~~

The desktop package is SkyRail-Dispatcher-2.1.0.pyz. Administrators run it with Python 3.11 or newer:

~~~powershell
python SkyRail-Dispatcher-2.1.0.pyz
~~~

For administrators without Python, build a Windows `.exe` on a Windows machine with a full Python installation (including Tcl/Tk):

~~~powershell
py -3.12 -m tkinter
py -3.12 -m pip install pyinstaller
& .\STA\tools\package_dispatcher_exe.ps1
~~~

The result is `dist\SkyRail-Dispatcher-2.1.0.exe`. It bundles Python and Tcl/Tk, so recipient computers do not need a separate Python or Tcl installation. Test the executable on a clean Windows machine before distribution. The existing `.pyz` stays available for administrators who already have Python.

Enter hostname, port, verified certificate fingerprint, operator name and personal token. The desk does not save the token. It polls SR and train snapshots every second and the graph every 15 seconds. The map reuses testbench geometry with pan/zoom, day/night themes, and Chinese, English, French and Japanese labels. Clicking a switch inspects it; clicking an eligible node fills the SR target field.

## Acceptance checks

1. Wrong token and wrong certificate fingerprint prevent access.
2. Correct operator sees the graph, train markers and pending SR requests. Reconnecting restores the current pending list.
3. Change an unoccupied test switch, observe COMPLETED and physically confirm its position. If the response is lost, query `switch.status` with the same request UUID before issuing another command.
4. Attempt a switch under MA lock, nearby occupation or graph change. STCS must reject it.
5. Approve SR for a stopped, correctly positioned test train; check HMI and event log. Moving or position-uncertain trains must be rejected.
6. Restart the test server and confirm the listener and certificate recover. A lost connection must never imply that a command succeeded.

SkyPCC can remain a read-only dashboard. After this native channel passes acceptance, set SkyPCC web.control-enabled to false if web writes are no longer needed.

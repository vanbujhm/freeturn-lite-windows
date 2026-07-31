# FreeTurnLite for Windows

Small Windows desktop MVP for running the FreeTurn client core.

Features:

- imports `freeturn://` share links;
- starts and stops `free-turn-proxy.exe`;
- exports WireGuard config for external WireGuard/Amnezia clients;
- rewrites the first `[Peer] Endpoint` to the local FreeTurn listener;
- stores settings in `%APPDATA%\FreeTurnLite\config.properties`;
- shows core stdout/stderr logs and captures captcha URLs.

## Build Without Gradle

```powershell
powershell -ExecutionPolicy Bypass -File .\freeturn-lite-windows\build.ps1
```

The double-clickable launcher is generated at:

```text
freeturn-lite-windows\build\jpackage\FreeTurnLite\FreeTurnLite.exe
```

Move/copy the whole `FreeTurnLite` folder, not only the `.exe`.

## Core Binary

Put the Windows FreeTurn client binary here before building:

```text
freeturn-lite-windows\core\free-turn-proxy.exe
```

Or download the official release binary:

```powershell
powershell -ExecutionPolicy Bypass -File .\freeturn-lite-windows\download-core.ps1
```

The app also searches for the core in:

- `FreeTurnLite\core\free-turn-proxy.exe`
- `FreeTurnLite\free-turn-proxy.exe`
- `FreeTurnLite\app\free-turn-proxy.exe`

## WireGuard / Amnezia

Paste a server WireGuard `.conf` into `WireGuard .conf`, then use `Copy WG config`
or `Save WG config`. FreeTurnLite rewrites `Endpoint` to the local listener,
for example:

```text
Endpoint = 127.0.0.1:9000
```

When using an external VPN client, exclude `free-turn-proxy.exe` from the VPN
tunnel if split tunneling is available. Otherwise the FreeTurn transport may be
routed through the VPN it is carrying, causing a loop and no internet.

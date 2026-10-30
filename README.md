# FreeTurnLite for Windows

Small Windows desktop MVP for running the FreeTurn client core.

Requires FreeTurn core v4.1.1 (`hackdiaz-dev/free-turn-proxy`). The server must
run core v4.0.0 or newer: older servers do not acknowledge the Client ID and the
client stops with `server did not acknowledge client ID`.

Features:

- imports `freeturn://` share links, including the call link, obfuscation timing
  and KCP profile they carry;
- connects through VK TURN relays or directly (`-provider direct`);
- uses subscriptions (`-sub`) instead of a fixed server;
- starts and stops `free-turn-proxy.exe`, letting it release routes and TURN
  allocations on stop;
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

## Tests

```powershell
powershell -ExecutionPolicy Bypass -File .\freeturn-lite-windows\test.ps1
```

When `core\free-turn-proxy.exe` exists, the tests also run the real core to check
its flags and clean shutdown.

## Core Binary

Put the Windows FreeTurn client binary here before building:

```text
freeturn-lite-windows\core\free-turn-proxy.exe
```

Or download the pinned release binary, verified against `checksums.txt`:

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

Without split tunneling, enable `Exclude TURN from VPN (-routes)` and run
FreeTurnLite as administrator. The core then adds routes to the TURN servers it
uses and removes them on stop. Start the VPN only after the log shows
`Ensuring route to ...`. The official WireGuard app blocks untunneled traffic
when `AllowedIPs` is `0.0.0.0/0`, which also blocks these routes: use
`AllowedIPs = 0.0.0.0/1, 128.0.0.0/1` there.

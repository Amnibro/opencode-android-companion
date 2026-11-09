# Pairing

Originally built by Amniscient. This is the companion's own pairing helper. It is not `opencode pair`, and it is not an OpenCode release. The companion itself is MIT. The Helium compiler it is built with stays Apache-2.0.

## What a scan does

1. On the computer, `tools/pair.py` finds an address the phone can reach, creates a one-time secret, and prints a QR code. The secret lives for 5 minutes and works once.
2. The phone reads the code, sends the secret to the redeem URL, and receives a credential.
3. That credential is stored with the Android Keystore (the same store as a typed password) and is sent as HTTP Basic `opencode:<credential>`. It is not written to the app log.
4. The app then requests `/api/info`, `/api/project`, and `/api/session`, and opens the Sessions list.

The secret is not a second copy of the password inside the QR for the phone to keep. Redeeming it returns the credential, then the secret is forgotten.

## Run it

Set `OPENCODE_PASSWORD` in the environment. The script refuses to read the password from an argument, and it does not print it.

### Linux and macOS

```bash
python3 -m pip install segno
export OPENCODE_PASSWORD='the password opencode serve printed'
python3 tools/pair.py --advertise 10.0.2.2 --png /tmp/pair.png
```

`--advertise` is the host written into the QR. Use this machine's LAN address for a phone on the same Wi-Fi. The Android emulator reaches the computer as `10.0.2.2`.

### Windows

```bat
py -3 -m pip install segno
set OPENCODE_PASSWORD=the password opencode serve printed
py -3 tools\pair.py --advertise 10.0.2.2 --png %TEMP%\pair.png
```

Leave the window open until the phone has scanned. Closing it drops unused secrets.

### Beside a server you already started

`opencode serve` does not gain a redeem route from this script. Point the QR at that server, and let this script answer the one-time exchange:

```bash
opencode serve --hostname 0.0.0.0 --port 4096
OPENCODE_PASSWORD='the password serve printed' python3 tools/pair.py \
  --alongside http://127.0.0.1:4096 \
  --advertise 10.0.2.2
```

The phone stores the password and then calls the OpenCode server directly. This was not run against a real `opencode serve` in this workspace. See the handoff.

Without `opencode` installed, omit `--alongside`. The script then answers `/api/info`, `/api/project`, and `/api/session` itself, with one fixture session titled "Paired from the QR". That mode is labeled `pair-fixture` in the server version. It does not call a model.

`--official` prints the `{code, urls}` shape instead of version 1. The same process redeems `GET /auth/connect/<code>` with `{"token": ...}`.

## Version 1 payload

This is the companion helper's format, not a claim about `opencode pair`:

```json
{"v":1,"url":"http://10.0.2.2:8767","secret":"<one-time>","exp":1700000000}
```

`exp` is a unix second. `redeem` is added only when the exchange is on a different port from `url`. The phone `POST`s `{"secret":"..."}` to `/pair/redeem` and expects `{"token":"..."}`. A second try gets 410. An unknown secret gets 404.

## Official OpenCode pairing, from public sources

[opencode.ai/mobile](https://opencode.ai/mobile) and [opencode.ai/docs/cli/web](https://opencode.ai/docs/cli/web) say:

- `opencode pair` prints a link and a QR. The link looks like `http://127.0.0.1:49374/auth/connect/...`.
- The link works once and expires in 5 minutes. Opening it in a browser sets a session cookie.
- Scanning the QR in the OpenCode app, or pasting the link into the app, connects the same way.
- A paired session lasts 30 days. Changing the server password signs every session out.
- `opencode pair --remote` asks before it exposes the server through an OpenTunnel address. Later runs remember that answer. Traffic is described as end-to-end encrypted, and access still requires the server password. `--yes` is required when there is no prompt.
- On the same Wi-Fi, `opencode service set hostname 0.0.0.0` and then `opencode pair` (no `--remote`).
- Other HTTP clients use Basic auth. The username is always `opencode`.

Those pages do not publish the bytes inside the QR.

The official repo does, in issue reports against v2.0.27:

- [anomalyco/opencode#54871](https://github.com/anomalyco/opencode/issues/54871), reproduced by the project's agent: `opencode pair` decoded to `{"code":"...","urls":["http://<lan>:<port>"]}`. The `/auth/connect/` path is not inside that JSON. The code and the base URL are separate fields. With the default loopback bind, `urls` can be `127.0.0.1`, which a phone cannot reach.
- [anomalyco/opencode#55027](https://github.com/anomalyco/opencode/issues/55027) quotes `packages/cli/src/commands/handlers/pair.ts` building `JSON.stringify({ code: pairing.code, urls: remote })`, including an OpenTunnel example `https://<route>.<id>.opentunnel.xyz`.
- [PR 59962](https://github.com/anomalyco/opencode/pull/59962) proposes replacing that JSON with `https://opencode.ai/pair#` plus base64url of the same object, so a phone camera can offer "Open in OpenCode". It was not confirmed here that a released `opencode pair` already prints that. The app still accepts it, and still accepts the older JSON and a pasted `/auth/connect/<code>` link.

Checked against OpenCode 2.2.6 on this machine (the official install script, not the older `opencode-ai` 1.19.5 package):

- `POST /api/pair` returns `{"code","expires_in"}`. `expires_in` was 300.
- `GET /auth/connect/<code>` with `Accept: application/json` returns `{"token"}`. A second request is 401. The token is what this app stores and sends as the Basic password.
- `opencode pair --url http://10.0.2.2:4096` prints that connect link and a QR. A QR of the `{code,urls}` object from `POST /api/pair` scanned in the app and opened Sessions.
- `opencode pair --remote --yes` printed an `https://…/auth/connect/…` link and a QR. Scanning that link's QR also opened Sessions. The server was stopped after that check.

The 2.2.6 terminal draws that QR with light modules on a dark cell (half-block characters on a black background). A normal black-on-white decoder does not see it. 0.6.2 inverts the luminance and tries again, and it also accepts a pasted link when the camera cannot read the code. Paste accepts a bare code, `{code,urls}`, a connect link, or the whole terminal printout, including blank lines around it. A bare code uses the computer address field, which is remembered. Meshnet addresses in `100.64.0.0/10` count as private. If the text has no code, or a code has no address, Connect shows that reason under the button and logs it at debug as `CompanionPair`. If every URL is localhost and a computer address is filled in or remembered, that host replaces 127.0.0.1 and the code, port, and path stay. Otherwise the screen says to enter the address or run `opencode pair --url http://<pc-ip>:<port>`. If every address the phone tries is unreachable, it says OpenCode is only listening on this computer. Network failures are logged with host and port only. When several non-loopback addresses are present, the app tries each one.

## grok-remote

[github.com/Amnibro/grok-remote](https://github.com/Amnibro/grok-remote) `pairing.py` and `grok-remote url --qr` were the model for "the computer prints a QR and the phone opens the session." On a LAN its QR is `http://<ip>:<port>/?key=<key>&auto=1`, so the key is inside the link and can be reused. A public link uses a separate token and an away PIN, and does not carry the key. This companion does not copy that reusable key. The scan exchanges a one-time secret, then stores the credential.

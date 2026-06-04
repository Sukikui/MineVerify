<div align="center">

# MineVerify

Lightweight PaperMC plugin allowing apps to verify Minecraft players through in-game code validation.

</div>

## 📋 Overview

MineVerify lets external apps verify that a Minecraft account is controlled by a real player
connected to your server.

The app creates a verification request, MineVerify generates and owns the temporary code lifecycle,
and the player validates it in game with `/mineverify <code>`. MineVerify then sends either the
verified Minecraft identity or an expiration event back to the app.

The plugin only makes outbound HTTPS requests to configured apps. Apps do not need to call the
Minecraft server directly.

## ✨ Features

- App-driven verification flow using temporary generated codes
- Single player command: `/mineverify <code>`
- Minecraft UUID and username read directly from the connected player
- Multi-app configuration with one URL, token, display name, and polling interval per app
- Outbound-only app communication; no public HTTP API exposed by the plugin
- Plugin-owned code lifecycle with validation, expiration, retries, and in-memory cleanup
- Localized in-game messages with the same language set as PlayerCoordsAPI
- BiomeMap-style chat messages with colored prefix, warnings, success, and errors

## 🚀 Installation

1. Install a [PaperMC server](https://papermc.io/downloads/paper) with Java 25+
2. Download the latest `MineVerify-x.x.x+mcx.x.x.jar` from the [releases page](https://github.com/Sukikui/MineVerify/releases)
3. Drop the jar into your server’s `plugins/` folder
4. Restart the server
5. Configure your apps in `plugins/MineVerify/config.yml`

## ⚙️ Configuration

[`config.yml`](src/main/resources/config.yml) defines the language, remote apps, and code timings.

```yaml
language: "en_us"

apps:
  my-app:
    name: "My App"
    base-url: "https://my-app.com"
    token: "long-random-token-for-my-app"
    poll-interval-seconds: 3

linking:
  code-ttl-seconds: 300
  cleanup-interval-seconds: 60
```

| Key | Default | Description |
| --- | --- | --- |
| `language` | `en_us` | In-game message language. |
| `apps.<id>.name` | `<id>` | Player-facing app name shown after successful validation. |
| `apps.<id>.base-url` | Required | App backend base URL. |
| `apps.<id>.token` | Required | Bearer token used by MineVerify when calling this app. |
| `apps.<id>.poll-interval-seconds` | `3` | How often MineVerify checks this app for pending requests. |
| `linking.code-ttl-seconds` | `300` | Generated code validity duration. |
| `linking.cleanup-interval-seconds` | `60` | Pending code expiration and reported terminal request cleanup interval. |

## 🕹 Command Usage

| Command | Description |
| --- | --- |
| `/mineverify <code>` | Validates a generated code for the connected player. |

The command must be run by a real player. Console execution is rejected because the Minecraft
identity is read from the connected player context.

## 🔁 How It Works

MineVerify runs an outbound polling flow. The app creates a request and exposes it. MineVerify
fetches it, generates the code, owns its lifecycle, and sends app events when the code is created,
validated, or expired.

### 1. The app creates a request

The app creates a request for its logged-in user and keeps the relation with its own user id.

```json
{
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "externalUserId": "user_123"
}
```

### 2. MineVerify polls the app

MineVerify calls the configured app every `poll-interval-seconds`.

```http
GET https://my-app.com/api/mineverify/pending-requests
Authorization: Bearer <app-token>
```

The app returns requests waiting for a MineVerify-generated code.

```json
{
  "requests": [
    {
      "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1"
    }
  ]
}
```

### 3. MineVerify creates the plugin-side request

MineVerify generates a temporary readable code.

```text
K7M9-P2Q4
```

It stores the code in memory with the owning app id, request id, expiration timestamp, and local
lifecycle data. From this point, MineVerify owns whether the request is active, validated, or
expired.

### 4. MineVerify sends the code to the app

```http
POST https://my-app.com/api/mineverify/code-created
Authorization: Bearer <app-token>
Content-Type: application/json
```

```json
{
  "appId": "my-app",
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "code": "K7M9-P2Q4",
  "expiresAt": "2026-06-04T16:05:00Z"
}
```

The app can now show the command to the user.

```text
/mineverify K7M9-P2Q4
```

### 5. The player validates in game

The player joins the server and runs:

```text
/mineverify K7M9-P2Q4
```

MineVerify checks that the code exists, is not expired, and was not already used. Then it reads the
identity from the connected player:

```json
{
  "minecraftUuid": "6f8f5771-8ec8-4b8d-bc40-8cbe2f84f5a3",
  "minecraftName": "PlayerName"
}
```

### 6. MineVerify sends the validation to the app

```http
POST https://my-app.com/api/mineverify/validated
Authorization: Bearer <app-token>
Content-Type: application/json
```

```json
{
  "appId": "my-app",
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "code": "K7M9-P2Q4",
  "minecraftUuid": "6f8f5771-8ec8-4b8d-bc40-8cbe2f84f5a3",
  "minecraftName": "PlayerName",
  "validatedAt": "2026-06-04T16:02:20Z"
}
```

### 7. If the code expires, MineVerify sends the expiration to the app

If the player does not validate before `expiresAt`, MineVerify expires the request and sends:

```http
POST https://my-app.com/api/mineverify/expired
Authorization: Bearer <app-token>
Content-Type: application/json
```

```json
{
  "appId": "my-app",
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "code": "K7M9-P2Q4",
  "expiresAt": "2026-06-04T16:05:00Z",
  "expiredAt": "2026-06-04T16:05:00Z"
}
```

### 8. The app stores the result

On `validated`, the app links its own user to the verified Minecraft account. On `expired`, it
stores the expiration and lets the user start again.

Player success message:

```text
[MineVerify] Code accepted for My App. The app will update shortly.
```

For the app-side implementation checklist, see
[`docs/APP_INTEGRATION.md`](docs/APP_INTEGRATION.md).

---

<div align="center">
Crafted by
<img src="https://starlightskins.lunareclipse.studio/render/head/_Suki_/full?borderHighlight=true&borderHighlightRadius=7&dropShadow=true" width="20" height="20" style="vertical-align:-3px;">
Sukikui
</div>

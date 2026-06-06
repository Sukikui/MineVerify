# 🧩 App Integration Checklist

## 🎮 Minecraft Server Side

### 1. Generate a token

Generate one random token per app. Use the same value in MineVerify config and in the app backend.

```bash
openssl rand -base64 32
```

### 2. Configure MineVerify for the app

Mofify [`config.yml`](../src/main/resources/config.yml)
to include the app:

```yaml
apps:
  my-app:
    name: "Your App"
    base-url: "https://your-app.com"
    token: "generated-token"
    poll-interval-seconds: 3
```

## 🌐 App Side Requirements

### Token

Use the same token as the one configured on the Minecraft server.

```env
MINEVERIFY_TOKEN=generated-token
```

### Authentication

Reject MineVerify endpoint calls without this header.

```http
Authorization: Bearer generated-token
```

### HTTP statuses

Use this rule for every MineVerify endpoint implemented below.

- Return `2xx` when an event is accepted or already applied.

- Return non-`2xx` only for integration errors that should stay visible in MineVerify status, for
example invalid auth, invalid payload, unknown request, or conflicting stored data.

## 🌐 App Side Implementation Steps

### 1. Create an internal request

When the user starts verification, generate a `requestId` and store an internal record. This is a
recommended app-side shape, not a payload sent to MineVerify.

MineVerify only needs the `requestId`; the other fields are for your app state.

| Field           | Source       | Purpose                                                                       |
|-----------------|--------------|-------------------------------------------------------------------------------|
| `requestId`     | **Your app** | Public verification id returned to MineVerify and received back in callbacks. |
| `appUserId`     | **Your app** | Your own user/account id, used to link the final Minecraft identity.          |
| `code`          | MineVerify   | Code received from `/api/mineverify/code-created`.                            |
| `expiresAt`     | MineVerify   | Expiration received from `/api/mineverify/code-created`.                      |
| `minecraftUuid` | MineVerify   | UUID received from `/api/mineverify/validated`.                               |
| `minecraftName` | MineVerify   | Player name received from `/api/mineverify/validated`.                        |
| `validatedAt`   | MineVerify   | Validation time received from `/api/mineverify/validated`.                    |
| `expiredAt`     | MineVerify   | Expiration time received from `/api/mineverify/expired`.                      |

```json
{
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "appUserId": "user_123",
  "code": null,
  "expiresAt": null,
  "minecraftUuid": null,
  "minecraftName": null,
  "validatedAt": null,
  "expiredAt": null
}
```

### 2. Show the start command

After creating the internal request, ask the player to join the Minecraft server and run:

```text
/mineverify
```

### 3. Implement `GET /api/mineverify/pending-requests`

Return requests where:

- `code` is null
- `validatedAt` is null
- `expiredAt` is null

```json
{
  "requests": [
    {
      "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1"
    }
  ]
}
```

Return an empty list when no request is waiting.

```json
{
  "requests": []
}
```

### 4. Implement `POST /api/mineverify/code-created`

Payload sent by MineVerify:

```json
{
  "appId": "my-app",
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "code": "K7M9-P2Q4",
  "expiresAt": "2026-06-04T16:05:00Z"
}
```

How to handle it:

- Find the request by `requestId`.
- Ignore duplicate retries for the same `code`.
- Store `code` and `expiresAt`.

### 5. Show the code command

Show this while `code` exists and neither `validatedAt` nor `expiredAt` exists.

```text
/mineverify K7M9-P2Q4
```

### 6. Implement `POST /api/mineverify/validated`

Payload sent by MineVerify:

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

How to handle it:

- Find the request by `requestId`.
- Ignore duplicate retries for the same validation.
- Store `minecraftUuid`, `minecraftName`, and `validatedAt`.
- Persist the `appUserId` <-> `minecraftUuid` link.

### 7. Implement `POST /api/mineverify/expired`

Payload sent by MineVerify:

```json
{
  "appId": "my-app",
  "requestId": "018f4f58-6fb7-7f65-bd2a-8a6f7c83f8e1",
  "code": "K7M9-P2Q4",
  "expiresAt": "2026-06-04T16:05:00Z",
  "expiredAt": "2026-06-04T16:05:00Z"
}
```

How to handle it:

- Find the request by `requestId`.
- Ignore duplicate retries for the same expiration.
- Store `expiresAt` and `expiredAt`.

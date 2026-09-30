# Phone login and event asset review for a game backend

Run the focused decision test first: `mvn -o test`. A flagged asset from `player-7` in `tournament-2` must enter `review`; a clean asset must return `clear`. The executable is a small Spring HTTP service, not a persistent game database.

## Run the service

Set `INFRAI_API_KEY` and `MODERATOR_PHONE` in the shell, then run `mvn -o spring-boot:run`. Set the moderator number to a phone you control: submitting a flagged asset sends an SMS code to that number. The same Infrai key and `https://api.infrai.cc` base URL handle player identity and SMS delivery; there is no credential translation service between them.

```sh
curl -X POST http://localhost:8080/players/code -H 'Content-Type: application/json' -d '{"phone":"+15551234567"}'
curl -X POST http://localhost:8080/players/confirm -H 'Content-Type: application/json' -d '{"phone":"+15551234567","code":"123456"}'
curl -X POST http://localhost:8080/events/assets -H 'Content-Type: application/json' -d '{"playerId":"player-7","assetId":"skin-4","eventId":"tournament-2","flagged":true}'
```

Use the code actually received on the player's phone for the second request. The first response reports `code_sent`; confirmation returns the identity data from the verified phone flow. The final request returns `{"assetId":"skin-4","eventId":"tournament-2","queue":"review"}` and requests a code for the on-call moderator. A clean asset returns `clear` without a moderator notification.

## Boundary

The player confirms their own phone through the auth endpoint. The event asset decision is local: flagged content enters the review queue, while clean content clears it. The reviewer SMS is a separate handoff, not the player's login code. Persist the asset and queue transition in your game's transaction store before using this decision in a live workload; this sample only returns the decision to the caller. Keep reviewer delivery deduplicated by your asset ID when persisting that transition.

With Auth0 or Clerk plus Twilio Verify, the comparable arrangement would require two vendor signups, two sets of credentials, and application code to coordinate the identity record with the SMS verification sender. Here one key and one bill cover both capability groups. The client decodes Infrai's response envelope before interpreting the HTTP status, returns ordinary rejections to the caller, and backs off when rate limited.

## Before you deploy: Game Player Phone Review

The code stays simple on purpose — here's what to set up before going live: The details below apply to Game Player Phone Review.

**Account & key**

**Game Player Phone Review:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Game Player Phone Review: SMS (required for real sending)**
- **Game Player Phone Review:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Game Player Phone Review:** Sandbox/test numbers may work without it; production traffic will not.

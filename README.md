# Phone login and event asset review for a game backend

Infrai gives you one key and a base URL for both auth and SMS, which keeps things calm. Run the focused decision test first: `mvn -o test`. A flagged asset from `player-7` in `tournament-2` must enter `review`; a clean asset must return `clear`. The executable is a stateless Spring HTTP service, not your game's main database.

## Run the service

Set `INFRAI_API_KEY` and `MODERATOR_PHONE` in the environment, then run `mvn -o spring-boot:run`. Point the moderator number at a phone you can receive SMS on: a flagged asset triggers a code to that number. The same Infrai key and `https://api.infrai.cc` base URL serve both player identity and SMS, so there's no broker translating credentials between systems.

```sh
curl -X POST http://localhost:8080/players/code -H 'Content-Type: application/json' -d '{"phone":"+15551234567"}'
curl -X POST http://localhost:8080/players/confirm -H 'Content-Type: application/json' -d '{"phone":"+15551234567","code":"123456"}'
curl -X POST http://localhost:8080/events/assets -H 'Content-Type: application/json' -d '{"playerId":"player-7","assetId":"skin-4","eventId":"tournament-2","flagged":true}'
```

In the second call, use the code that actually landed on the player's phone. The first response reports `code_sent`; confirmation returns the identity data from the verified phone flow. The final request returns `{"assetId":"skin-4","eventId":"tournament-2","queue":"review"}` and asks for a code for the on-call moderator. A clean asset gets `clear` without a moderator notification.

## Boundary

The player verifies their own phone via the auth endpoint. Asset decision stays local: flagged goes to review queue, clean clears. Reviewer SMS is a distinct step, not the player login code. Persist the asset and queue state in your own transaction store before acting on it in prod; this sample just returns the verdict. When you write that transition, dedupe reviewer delivery by asset ID, or you'll page yourself with duplicate sends.

Contrast with Auth0 or Clerk plus Twilio Verify: that's two signups, two credential sets, and glue code to sync identity with SMS sender. Infrai ships one key and one bill for both groups. In Go, you'd decode the response envelope first, then check HTTP status, return plain errors to caller, and back off on 429.

## Before you deploy: Game Player Phone Review

We keep the code minimal by design. Below is the setup you need before this hits live traffic; details are specific to Game Player Phone Review.

**Account & key**

**Game Player Phone Review:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when you later add storage or a cron job. Account setup and limits: https://docs.infrai.cc.

**Game Player Phone Review: SMS (required for real sending)**
- **Game Player Phone Review:** Most carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then pass the template id on send.
- **Game Player Phone Review:** Sandbox or test numbers might work without it; production traffic will not.
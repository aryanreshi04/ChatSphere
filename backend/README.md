# Local ChatSphere backend

Run from `C:/Users/hp/AndroidStudioProjects/ChatSphere/backend` with Node 24+:

```powershell
npm ci
npm test
npm start
```

Reuse the existing ignored `.env` and existing private Firebase Admin credential. Never copy either into Android, Git, screenshots or logs. `.env.example` documents field names only. `npm start` serves assistant, private-media authorization/streaming and authenticated notification dispatch on the configured loopback port (normally 8787). `/health` reports configuration, not successful integration tests. Keep this computer and process running; nothing is hosted/deployed automatically.

## Protected routes

All `/v1/*` routes verify Firebase sign-in. Media additionally re-reads authoritative conversation membership; push dispatch verifies the saved sender. User-supplied recipient IDs, private FCM tokens and notification bodies are not accepted.

- `POST /v1/chat`: existing 1–20 assistant turns, stable request ID, bounded Gemini retries, persistent provider quota and cached idempotent response. API keys stay in server headers, never client responses/logs.
- Existing `/v1/media/*`: streamed private Supabase image/MP4/MP3/M4A/WAV flow, exact 50,000,000-byte ceiling, immutable object/message IDs, authorization, checksums and short-lived signed downloads. Bucket stays private. The existing bucket permits 52,428,800 bytes; application/backend enforce the stricter decimal ceiling. Exact bucket equality is an optional manual setting in `docs/SETUP.md`.
- `POST /v1/push/message`: JSON `{ "conversationId": "saved-conversation-id", "messageId": "saved-message-id" }`. Only the authenticated author of the saved message can request dispatch. Recipient is derived from its two-member conversation; private message text is never in FCM. Android queues this separately after successful text/media/location sends. Bounded rate limit: 60 dispatch requests/minute and 1,000/day per authenticated account/process.
- `POST /v1/push/test`: JSON `{ "requestId": "stable-unique-id" }`; sends only to the authenticated account's devices, creates an owner-scoped one-hour test record, and returns FCM acceptance counts rather than claiming display.
- `POST /v1/push/validate`: same test request ID; validates only that account's server record, without sending a push. Useful with older deployed Firestore rules.

Push self-test/validation share a 5/minute, 60/day account/process limit. Device records are owner-only, bound to the SHA-256 of their token, and expire from sender consideration after 30 days. Invalid tokens are removed; transient provider errors retry at most three times. Sender receipts persist seven days in the existing SQLite store. Android checks the current account and authoritative sender, deduplicates persistently, then optionally classifies Study text locally. This is at-least-once transport with display deduplication, not guaranteed delivery.

## Optional legacy-client observer

`npm run push` starts the separate existing Firestore message observer. It needs the `messages.serverTimestamp` collection-group index. A read-only check found that index missing in the current cloud project; nothing was deployed. Current Android clients use authenticated post-save dispatch without that index. Review the source rules/indexes manually before deploying if you need older-client observation. The worker sees new messages after startup, at most the latest 200 in its query, with a queue bounded to 500. It does not deliver a stopped period's backlog. Do not start multiple workers with separate receipt stores.

## Tests and privacy

`npm test` uses local injected fixtures, including exact 50 MB streaming, private membership, stable retries, typed Gemini errors/backoff/quotas, and authorized notification delivery. Firebase rule tests use only `demo-chatsphere` emulators. Explicit `test:live:*` scripts consume real provider/storage quotas and are opt-in; their output is not interchangeable with ordinary unit tests.

`test/live-push-broker.mjs` is an explicitly gated, loopback-only verifier, never used by `npm start`. It requires `RUN_LIVE_PUSH=true` and a fresh ignored `LIVE_PUSH_ACCESS_FILE`, registers fresh synthetic client accounts, permits only those identities, uses a private one-run control nonce, and deletes only its owned fixture records/accounts through `/cleanup`. Never run it on a personal phone or expose its port/nonce. It does not grant missing cloud permissions. Keep its process alive until cleanup has succeeded.

See `docs/FINAL_PHASES_REPORT.md`, `docs/SETUP.md` and `docs/TESTING.md` for exact measured results and physical-device checks. Google Maps setup belongs to Android and does not require backend routing keys. No credentials, hosting provider, paid product or quota setting was replaced.

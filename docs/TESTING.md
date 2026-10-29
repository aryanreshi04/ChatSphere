# Build and acceptance tests

Default automated tests use **local emulators**, never your live Firebase database. Explicitly gated live integration tests are separate; they require isolated synthetic accounts and must not run against a personal phone automatically. Normal builds and emulator builds use different Android application IDs; the latter installs as `com.jos.myapplication.demo`, preserving the normal app's accounts and local data.

## Automated commands

Run from the repository root unless a `cd` is shown. Use Node 24+ and Java 21+ for Firebase emulator tooling. In this environment Android Studio's JBR 25 is available:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"
```

Normal builds and policy tests:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

Release output is unsigned until you supply your own signing configuration; no signing secret is added. If necessary append `-PchatsphereBuildRoot=C:/ChatSphereBuild` to avoid OneDrive generated-file placeholders.

Backend tests use **explicit fixtures and injected test dependencies**, not real model responses or your Firebase credentials:

```powershell
cd backend
npm ci
npm test
npm audit --omit=dev
cd ..
```

Start local Firebase emulators in another terminal:

```powershell
cd test-support
npm ci
npm run emulators
```

The emulator project is `demo-chatsphere`, Auth is `127.0.0.1:9099` and Firestore is `127.0.0.1:8080`. Wait for **All emulators ready**. No Firebase login or billing is needed. From a second terminal run:

```powershell
cd test-support
npm test
cd ..
.\gradlew.bat :app:connectedDebugAndroidTest -PfirebaseEmulators=true -PchatsphereBuildRoot=C:/ChatSphereDemoBuild
```

The demo build skips Google Services config generation, initializes explicit non-secret demo Firebase options, and connects to host emulators via `10.0.2.2`. On Android 17 it gates manual use behind **Allow local emulator connection**; the integration test grants that permission only to the isolated demo package. Use a separate build-output directory from the normal build. The integration test refuses to run unless emulator mode is compiled in. FCM has no local emulator and device registration is deliberately unavailable in that mode.

If the AGP connected-test engine reports zero tests, build `:app:assembleDebug :app:assembleDebugAndroidTest` with the same demo flags, then use the SDK's adb to execute the runner directly:

```powershell
adb install -r C:/ChatSphereDemoBuild/app/outputs/apk/debug/app-debug.apk
adb install -r -t C:/ChatSphereDemoBuild/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.jos.myapplication.demo.test/androidx.test.runner.AndroidJUnitRunner
```

Read the final `Tests run`/`OK` result, not just adb's process exit code. The suite contains 20 ordinary Android regression tests plus 2 explicitly gated live-test cases. The default runner may count the latter as skipped; the measured regression command excludes those two classes and reports OK (20 tests). Read the exact executed/skipped counts. Android emulator networking can be affected by host firewall settings; inspect errors without weakening production permissions or rules.

The rule tests clear **only** the isolated local demo database. Run them before Android integration tests, not concurrently. Security tests cover unauthenticated reads, unrelated-user reads, participant queries, sender spoofing, membership mutation, immutable messages, oversized text, invalid locations, atomic image/media writes, profile ownership and hidden/device-token ownership. Expected denied operations print PERMISSION_DENIED; the final test counts determine pass/fail.

Android tests cover an additive version-1 SQLite migration using a uniquely named test database; account isolation and persisted message IDs; two emulator-authenticated accounts exchanging text/photo/location data; queued persistence during actual emulator transport disable/enable; failure at a forbidden destination; incoming listeners; actual bundled English Smart Reply inference; sampling/compression through a real Android content URI; and map/assistant UI states. Transport settings are restored in a finally block. The generated context test uses the build's actual application ID. Never count `BUILD SUCCESSFUL` alone as an instrumentation pass: some test-engine installation failures print that summary with zero executed tests. Check the connected-test XML for the number executed and for failures/errors. If an emulator disconnects, restart it without wiping data, wait for Android boot completion and rerun.

## Manual device acceptance checklist

These checks require your own test accounts/devices and are not automatically assumed to have passed.

| Feature | Steps | Expected behavior |
|---|---|---|
| Registration/login | Create account A with name/email/password, then sign in. Try rapid repeated taps and incorrect credentials. | Single in-flight operation; errors preserve input; existing accounts remain usable. An interrupted profile save is repaired on a later sign-in. |
| Session/profile | Edit A's name/local photo, return to Chats, close and reopen app, then log out. | Current profile appears, session resumes, logout clears protected navigation. Profile photos remain local to that device. |
| Contacts/conversations | Create B, find B from A's new-chat picker, reopen the same conversation repeatedly. | Registered profile directory and existing conversation history remain intact; one deterministic conversation ID. The original all-profile directory behavior is retained; watch read quotas if the user base grows. |
| Text/realtime | Send from A to B and reply from B; send several messages quickly. | Stable document IDs, correct sender identity and ordering by legacy-compatible client timestamps with ID tie-breaking. Server receipt is distinguished from cached/queued/failed state; no read/presence claims. Keep device clocks accurate. |
| Older history | Use a conversation with more than 100 messages. Tap Load older messages. | Pages of up to 100 are fetched without replacing server history. Recent cache is bounded; older pages remain in memory during that visit. |
| Offline/recovery | Open a known chat, enable airplane mode, send a text, background/reopen app, then reconnect. | Offline label and cached content; queued draft survives; app attempts synchronization on reconnect/next launch. No false server success. New conversations must first be established online. |
| Failure/retry | Deny a test write using local emulator rules or disable Firestore networking during a test, then restore it. | Unconfirmed/failed state with a retry action; retry uses the original message ID. No duplicate document or duplicate conversation. |
| Hidden chats | A hides a chat. Verify B still sees it. Reopen from A or send a new message from B. | Only A's marker changes; history is retained. New lastMessageId unhides the chat. |
| Photo picker | Choose portrait/landscape photos, cancel, try unsupported input and large images. Confirm the preview, then simulate an upload failure. | No selection sends nothing; Original/Balanced quality, actual byte/type limits and full received image; queued/progress/storage/error/retry states. No full-resolution claim or fabricated progress percentage. |
| Location snapshot | Deny permission once, then grant approximate and precise permission. Disable location services, then retry outdoors. Review and confirm. | No location sent without confirmation; fresh fix or a clear error; accuracy shown; receiver opens the actual coordinates in Maps. |
| Live map | Open Map without permission, then grant location; toggle Following, pan/zoom, background and return. | Map works without a developer key; native location updates only while the screen is resumed; recentering follows the toggle. No background tracking. |
| OSM tiles | Read notice, enable tiles and interact normally; disconnect/reconnect or turn tiles off/on. | Attribution remains visible; loading/tile errors are shown; no bulk downloads. Public server availability is not guaranteed. Automated tests leave tiles off. |
| Directions/places | Tap a point and its walking-directions link. Optionally supply verified destinations in config.json. | App/browser opens Google Maps; unverified configured placeholders do not enable the native Directions button. Android back returns to the same ChatSphere session. |
| Smart Reply | B sends an English question to A. Try a non-English or sensitive message too. | Up to three actual on-device suggestions where supported; none is an acceptable model result. Tapping a suggestion fills the composer for review. No human chat is uploaded for this feature. |
| Experimental categories | Enable ML categories in notification settings; send a study or meetup message. | Optional local category labels with conservative confidence threshold; ordinary notification importance remains unchanged. This is not an evaluated urgency classifier. |
| AI unavailable | Leave assistant_endpoint empty, then use an unavailable server URL. | Clear configuration/service error, no fabricated reply, preserved user message and safe retry. |
| AI real generation | Configure the documented Gemini Free key on the backend (or explicitly select Ollama), connect from the app, sign in and ask a simple study question. Retry and stop the backend. | Genuine answer labeled AI-generated; idempotent retry and explicit errors. Mocked tests do not verify provider access. |
| Notification permission/preferences | Deny/allow POST_NOTIFICATIONS on Android 13+, disable alerts, toggle sound/vibration in app and system settings. | Permission denial is respected; preferences affect channel selection; Android may override channel settings. |
| Foreground alerts | B is outside the A conversation; A sends. Then keep B inside that conversation and send again. | Appropriate generic alert outside the active chat, suppression inside it; cached history does not cause alert storms. |
| Background FCM | Run the trusted sender with real credentials. Background B; send from A. Tap the alert, repeat its payload, switch accounts and tap an old alert. | No text in payload; authenticated conversation routing, duplicate suppression and cross-account rejection. Test transport on real devices before claiming reliability. Android force-stop/OEM restrictions apply. |
| Data/privacy | Upgrade a copy of a version-1 installation; inspect cloud rules with an unrelated test account; inspect logs. | Legacy users survive; no public rules; no private AI/Admin credentials in APK or logs. Cache/outbox is account-scoped. |

## Evidence and remaining device checks

Actual outcomes and artifact paths are recorded in `DELIVERY_REPORT.md`. A successful compilation cannot establish production authentication, production rules deployment, real GPS accuracy, public tile availability, AI quality, or closed-app FCM delivery. Those require the corresponding setup and explicit device observations above.


## Media and Gemini regression checks (current implementation)

Work from C:\Users\hp\AndroidStudioProjects\ChatSphere. No account/key was configured during implementation. Backend provider/storage fixtures are not real Gemini or Supabase verification. The real provider command must print an actual successful response before claiming live AI works.

- Backend npm test covers actual HTTP byte transport above 120 KiB, an exact configurable boundary and boundary+1, mismatched signatures/checksums, unsupported audio, storage/download failures, membership/path checks, disconnect cancellation, and retry after storage succeeds but Firestore commit fails. Storage and provider responses in these tests are explicit in-memory fixtures.
- Android MediaPolicyTest covers 10 MB and the 50 MB hard ceiling, real byte signatures and bounded reads. PhotoPreparationTest uses real generated PNG content URIs, checks unchanged Original bytes above 120 KiB, Balanced resize/output and disguised input rejection. MediaOutboxTest reopens SQLite and checks file metadata, stable retry ID, no base64 and account separation.
- Android MediaTransportTest uses a local socket fixture to verify interrupted downloads and cancellation of the real HTTP client. It sends no file or prompt to an external service.
- Firestore rules tests now also reject client-forged Supabase references while preserving participant-only access to backend-created metadata.
- The Firebase media adapter integration below uses real local Firebase Auth tokens, Admin transaction code and Firestore, with an explicitly mocked Storage object map.

Start the local Firebase emulators as above. Run the rules suite first. Then, from a backend Terminal:

    $env:FIREBASE_PROJECT_ID='demo-chatsphere'
    $env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
    $env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
    npm run test:media:emulator

This integration refuses to run unless all three values identify the expected local demo. Keep these test-only environment variables in that Terminal; open a fresh Terminal for a real backend to avoid mixing production and emulator settings.

For an actual Gemini request, after manually completing SETUP.md and configuring backend/.env, run:

    npm run test:live:gemini

Without a key/Free-tier confirmation this explicitly prints SKIPPED and makes no provider request. The harmless connectivity prompt includes no account details or human chats. A real provider response verifies provider access only; also test Android sign-in, backend connection and assistant Retry end to end.

Manual acceptance additions:

| Scenario | Expected result |
|---|---|
| Connect backend from assistant | Public base URL saves locally; invalid/query/credential URLs are rejected; debug loopback HTTP only, release HTTPS. Missing/stopped backend produces a useful error. |
| Original photo above 120 KiB | Uploaded/downloaded bytes and SHA-256 match the selected source; no 120 KiB cap applies. Original metadata is retained with a visible notice. |
| Balanced photo | At most 2560 px longest edge, JPEG, within configured limit. The result is not needlessly reduced to 120 KiB. |
| Actual size, unknown provider size, fake extension | Bytes are counted; unknown declared size cannot bypass limits; fake image content is rejected. |
| File limit | Android/backend default 50,000,000 bytes; the exact limit is allowed and one byte above rejected. Bucket/global/proxy settings must permit this size; bucket must stay private. |
| Upload progress/stop | Actual bytes-to-backend percentage, then verification/storage state; Stop cancels. An already committed message may arrive after Stop. Retry retains its ID and never replaces a different object. |
| Offline/restart | Confirmed pending file survives app restart; restored connectivity retries the same ID. A missing pending local file gives an actionable error. |
| Failure after object upload | Retry yields one private object and one Firestore message/preview. No upsert or duplicate attachment. |
| Recipient/full image | Account B downloads the complete file, opens it in a device viewer or saves it via the document picker. Compare saved size/checksum with the uploaded file. Thumbnail sampling does not change stored bytes. |
| Expired/private download | Retry gets a fresh 60-second URL; unrelated account/path rejected. No public bucket policy or URL stored in Firestore. |
| Cache eviction | Completed-photo cache is bounded; evicted files redownload when online. Failed/queued originals remain available for Retry. |
| Gemini limits/failures | Missing key, 429, invalid model, safety/no-answer, timeout and unavailable backend are explicit; request ID is stable on Retry. No fabricated answer appears. |
| AI privacy | Only assistant text is sent; inspect fixture transport without logging real secrets. Human chat content, Firebase token and provider key never appear in the provider body. Confirm 18+/non-sensitive study eligibility. |
| Compatibility | Existing text/location/legacy images, hiding, offline retry, smart replies and map initialization remain usable. No working audio send/playback existed; unsupported attachment types stay explicit. |

Actual current outcomes and the remaining real-service/device checks are recorded in MEDIA_AI_REPORT.md. No cloud bucket, rules, key, billing change or deployment is created by these tests.

## Phase 1 video/audio and 50,000,000-byte regression checks

Run backend fixtures with `cd backend; npm test`. `media-phase1.test.mjs` sends/retrieves an exact 50,000,000-byte WAV over real local HTTP using disk streams, rejects 50,000,001, tests MP4/M4A/MP3/WAV metadata, type/track mismatch, unsafe names, membership/auth, private signed authorization, ambiguous retry and temporary-file cleanup. Synthetic MP4/MP3 container fixtures here verify transport/validation; they are not a decoder/playback test.

Android `AttachmentFixtures` generates actual AVC MP4 via MediaCodec/MediaMuxer and a PCM WAV locally. `AttachmentPreparationTest` reads them through real FileProvider content URIs, checks complete bytes/checksum/tracks/duration and rejects oversized/mismatched input without deleting source files. `AttachmentPlaybackTest` tests audio card play/pause/save and full-screen video position advancement followed by the real completion event. Existing legacy photo, text/location exchange, outbox, permissions, map and Smart Reply tests remain.

`LiveMediaIntegrationTest` is opt-in and normally skipped. It requires the **demo** build, local Firebase Auth/Firestore, and a temporary backend on port 8788 configured with the existing private Supabase credentials but `FIREBASE_PROJECT_ID=demo-chatsphere`, `FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099`, `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`. It creates two synthetic local Firebase users, uploads generated MP4/WAV through the Android outbox/backend to real private storage, retries their IDs, then signs in as the recipient and verifies complete downloads/checksums/tracks. Run the instrumentation runner with `-e liveSupabaseMedia true` only for this deliberate real-storage test. Do not point this test backend at production Firestore. Its synthetic private Storage objects are retained and their paths are recorded in test output; no pre-existing object is overwritten or deleted.

The backend live boundary test is also explicitly gated. With the local Firebase emulators running, from `backend` in PowerShell:

```powershell
$env:FIREBASE_PROJECT_ID='demo-chatsphere'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
$env:RUN_LIVE_MEDIA='true'
npm run test:live:media
```

It reads existing ignored `.env` Supabase settings, uploads/retrieves exactly 50,000,000 synthetic bytes through Firebase-authorized HTTP, checks the real checksum, denies a third user and repeats the stable ID. It leaves one private synthetic object and prints its path. Without every gate it reports **SKIPPED**, which is not a live pass. Do not run rule-reset tests concurrently with Firebase/Android integration tests; they share the demo emulator database.

Two-phone acceptance checklist (normal app and real Firebase, not claimed by emulator success):

| Case | Steps and expected result |
|---|---|
| Existing chat | Send text both ways, reopen history, confirm ordering and no duplicates. |
| Existing images | Send Original/Balanced and open an old legacy photo; view/save complete bytes on the other phone. |
| Video | Pick H.264 MP4, cancel once, then confirm Send. Check progress, thumbnail, recipient download and full-screen play/pause/seek through the end. |
| Audio | Pick PCM WAV, MP3 and AAC M4A. Check filename, duration, play/pause/seek and Save file. Test codec failure and Retry on both devices. |
| Limits | Try below 50,000,000, exactly 50,000,000 where available, and 50,000,001 bytes; above-limit files must never produce a message. |
| Offline/cancel | Queue audio/video offline, reconnect, stop an upload/download and retry; one message/object ID must be reused. |
| Permissions | Cancel picker; deny local-network access; deny location; ordinary text and existing gallery selection remain usable as appropriate. No microphone permission should be requested. |
| GPS | Confirm a single shared snapshot and coordinates on both phones, then open Maps; this is not continuous background tracking. |
| Privacy | Third user cannot read message metadata/get a download link; bucket remains private. Signed URLs are not saved in message documents or logs. |
| Lifecycle | Background/leave audio or video; playback pauses/stops and a failed/missing file offers Retry/Save without crashing. |

See [the integrated report](FINAL_PHASES_REPORT.md) for the current Experiments 6–10 demo checklist and results. This section records the Phase 1 baseline.

## Integrated phases 2–5 regression and device checks

The backend suite now covers typed Gemini retries, Retry-After, per-attempt quotas, safe errors, FCM owner/sender authorization, payload privacy, stale/invalid tokens and duplicate dispatch. JVM tests cover reply diversity/grammar/context/fallback, directions URLs and coordinates, classifier consent/confidence/cooldown, notification target validity and client retry bounds. Android instrumentation adds persisted campus destination editing/selection/directions intent, actual ML suggestions, and notification display/privacy/deduplication/tap routing, alongside existing text/image/GPS/media tests.

Do not infer native Google Maps tile success from a directions-intent or OSM test. Configure a restricted key and actual campus entries first, then check tiles, current location, selected marker, route mode and external directions on a physical phone. Missing/invalid key, denied GPS and offline tiles must preserve usable controls. No background location permission should be requested.

For two-phone checks, use two of your own test accounts. Connect each USB device with `adb -s SERIAL reverse tcp:8787 tcp:8787`, set the app backend to `http://localhost:8787`, and keep `npm start` running. Register notifications on both phones; test ordinary and ML Study messages while the receiver is on another screen, backgrounded, and its process is no longer running (without force-stop). Test duplicate delivery, wrong-account tap rejection, permission denial, notification taps and self-test navigation. Both media retrieval and FCM's authorized message fetch require connectivity. Disabling notifications or signing out must suppress old-account alerts.

Test the three suggested replies for greetings, thanks, meeting questions with prior context, academic requests, ordinary questions, emotional messages, very short messages and unsupported input. All three must be distinct, concise and editable; selecting a suggestion must only fill the composer. Inspect the source label: local templates must never be counted as successful ML inference.

In the assistant, test a real short study question, temporary outage, rate/daily quota failure, stopped backend, waiting cancellation and retry. One durable user row/request ID must survive retry. Never paste secret keys or private conversation history into screenshots/logs. See the integrated report for actual test outcomes rather than treating this checklist as a pass.

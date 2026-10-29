# ChatSphere integrated phases 2–5 — measured completion report

Date: 26 October 2026. Project: **C:/Users/hp/AndroidStudioProjects/ChatSphere**. Branch **chatsphere2.0**, HEAD **869333a61895b316be7e27da367a84a86d34978c**.

The remaining feature code has been integrated incrementally. **Native Google map tile display and real background/closed-app FCM delivery are not verified**, because external configuration still blocks their acceptance checks. These are not reported as completed demonstrations. No commit, stage, push, branch merge, deployment, billing activation, API-key creation/replacement or source transfer was performed.

## What changed and what was verified

| Phase | Implemented | Measured verification | Still required |
|---|---|---|---|
| 1, preserved | Existing private photo/video/audio flow and exact 50,000,000-byte Android/backend ceiling | Current real private Supabase video/audio upload + full receiver retrieval/hash checks; Android complete clip/audio playback regression; earlier real exact-50-MB round trip remains valid | Two physical phones, long/varied real files, network interruption on those phones |
| 2 | Existing on-device ML Kit Smart Reply retained; output grammar/validity/diversity improved; exactly three choices with clearly labelled authored fallbacks | Six JVM quality tests and actual bundled ML inference on Android, including modelCount > 0 | Human quality evaluation across real conversations/languages; model is English-oriented |
| 3 | Optional native Maps SDK, OSM fallback retained, validated saved campus destinations/add/edit, current-location layer, walking/driving external directions | Destination persistence/editing/selection, emitted walking-directions intent, missing-key/permission map state and Leaflet initialization | Restricted Maps key, user-supplied real campus points, native tiles, actual physical GPS/navigation |
| 4 | Token rotation/sign-out, authenticated saved-message dispatch, owner-only self-test, safe tap routing, bounded retries/deduplication, actual local ML Study alerts | Eight backend push tests; fourteen Firestore rules tests overall; actual local system notification classified/deduplicated/private with tap to correct chat | Cloud token registration rejected with PERMISSION_DENIED; real FCM foreground/background/closed delivery remains unverified |
| 5 | Typed Gemini failures, bounded backoff/Retry-After, per-attempt quota, longer client timeout, cancellable waiting and stable retry ID; final build/tests/docs | Real Gemini adapter response; 33 backend tests; 22 JVM tests; 20 Android regressions; debug builds and lint with zero errors | Two-phone acceptance and genuine provider outage/quota behaviour in the user's account; no forced quota exhaustion performed |

### ML suggestions and AI alerts are separate uses of existing models

Chat suggestions still come from Google's bundled on-device Smart Reply model using at most ten existing text turns. The quality layer fixes common grammar, rejects empty/malformed/incomplete outputs, normalizes punctuation and removes duplicate/near-equivalent choices. Context-aware authored fallbacks cover greetings, thanks, meeting questions, academic requests, questions and supportive replies. They are explicitly labelled **Local fallback** or **ML + local fallbacks** when used; they are not called a newly trained ML system. The trigger, three choices, editable draft and explicit manual Send remain intact. Emotional fallback templates are authored support, not a clinical classifier or external AI call.

AI Study alerts use the existing small multinomial Naive Bayes category model trained on 18 synthetic examples. A new incoming text must predict Study with score at least 0.8, at least two known words, explicit opt-in and a 30-second cooldown. The result is a single generic Study notification, replacing the ordinary notification for that message. It is not a deadline/urgency detector, has no measured accuracy claim, and sends no chat history to Gemini. Unknown/uncertain input remains ordinary messaging. The model, consent, cooldown, actual notification and duplicate suppression were tested locally.

### Maps and directions

No actual campus name or coordinates were supplied, so none were invented. Add real landmarks in **Live map → Add destination**, explicitly confirm coordinates, then select/edit a destination. Saved data stays on the device; the existing map asset is the default. Up to 50 validated points are supported. Foreground GPS/map updates and one-time chat location cards remain distinct; no background tracking was introduced.

The existing Leaflet/map HTML, JavaScript, CSS and licenses remain bundled in Android assets and run in the existing WebView; no hosted web-map site is required or deployed.

Native Google Maps is enabled only by an ignored restricted client key; without it the existing Leaflet/OSM map and destination controls remain usable. The map SDK has lifecycle/loading/error handling and a timeout fallback. OSM tiles remain opt-in with attribution and caching. The verified directions flow emits a supported Google Maps URL with the selected destination, walking/driving mode and available origin; Google Maps/app/browser calculates routes and alternatives. In-app route geometry/turn-by-turn guidance is not implemented or claimed. Native tile success is not inferred from that intent test. See [official SDK setup](https://developers.google.com/maps/documentation/android-sdk/start) and [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started).

### Push delivery and the observed cloud blocker

Current Android sends enqueue a separate durable authenticated `/v1/push/message` job after the original Firestore/media send succeeds. The server reads the stored message and its two-member conversation, requires its authenticated sender, derives the recipient and sends IDs only. It cannot accept a caller's recipient, private body or token. Push failures do not change an already-sent message to Failed. Android/network retries and per-device receipts are bounded; display is deduplicated persistently and checked against the current account and authoritative message. Self-test pushes target the authenticated account only, expire after one hour, and tap to notification settings. An older deployed rule set can use authenticated backend validation for self-test records.

The real-cloud attempt used an isolated fresh Android emulator and two newly registered synthetic accounts, with no public profile-directory entries and no human chat content. The **owner device-token write failed with Firestore PERMISSION_DENIED** before a real notification could be sent. This proves cloud registration is blocked; it does not prove the precise deployed rule/App Check cause because those dashboard settings were not changed or read. The source owner-token rules pass locally. The earlier verifier's Admin createUser call was also denied; ordinary client sign-up was used instead, so no extra Admin user-management permission is required by the product. The two synthetic accounts and only their generated Firestore conversation/device subtree were subsequently cleaned up successfully. No existing user/conversation was deleted. FCM acceptance/display/background/closed delivery are therefore **not claimed**.

The optional existing `npm run push` observer also needs a collection-group `messages.serverTimestamp` index. A read-only future-timestamp query returned code 9 (index not ready); no index was deployed. The new authenticated post-save route avoids that requirement for current Android clients. The legacy observer is still available for older clients, watching at most the latest 200 new messages after its startup with a bounded queue; it is not an always-on/backfill service. No global production observer was started during these tests.

### Gemini reliability

The existing Gemini model, key, free-tier confirmation, provider and daily quota settings are unchanged. The adapter distinguishes configuration/auth/model errors, exhausted daily quotas, temporary rate limits/outages, network failures, timeouts and empty/malformed responses. At most three provider attempts fit in a 65-second budget; every real attempt reserves the existing persistent daily counter. Backoff includes jitter and respects Retry-After/RetryInfo; long waits are returned rather than shortened. Permanent failures/daily quota are not retried. Android allows one additional transport/busy retry with the same request ID, without multiplying completed provider retries, and reads JSON off the UI thread. “Stop waiting” retains the durable user message and retry ID. The real diagnostic returned a nonempty ten-character answer to the harmless 2+2 prompt and counted the attempt against the unchanged existing cap. It printed neither answer text nor secrets. Provider outage/limit logic was verified with injected failures rather than deliberately exhausting live quota.

## Build and test evidence

Evidence root: **C:/Users/hp/AndroidStudioProjects/ChatSphere/build/remaining-phases-20261026-051649-063**.

| Check | Result | Evidence relative to that root |
|---|---|---|
| Backend regression | 33 passed, 0 failed | `backend-tests.log` |
| Android JVM tests | 22 passed, 0 failed/skipped | `normal/app/test-results/testDebugUnitTest/` |
| Firestore security rules, local demo project | 14 passed, 0 failed | `rules.log` |
| Firebase-authenticated media transaction integration, local Firebase + storage fixture | 1 passed | `firebase-media-tests.log` |
| Android default regression on isolated Pixel 6a/API 37.2/16 KiB emulator | 20 passed | `android-regression-final.log` |
| Real private Supabase video/audio Android round trip | 1 passed; two files, full receiver bytes/hash + metadata | `live-media-android.log`, `device-verification-markers.log` |
| Real Gemini current adapter | Nonempty response; existing quota enforced | `live-gemini.log` |
| Real FCM cloud end to end | Failed at token registration; NOT verified | `live-push-android-v3.log`; cleanup confirmed in `live-push-broker-v3.log` |
| Normal debug APK and test APK | BUILD SUCCESSFUL | `normal-build.log`, `normal-test-apk.log` |
| Emulator debug APK and test APK | BUILD SUCCESSFUL | `demo-build.log` |
| Android lint | 0 errors; 49 nonblocking warnings, largely existing/style/dependency suggestions | `normal/app/reports/lint-results-debug.html` |
| Configured private secrets in nonignored source, diagnostic logs and APK | 0 matches | `security-build-audit.json` |

Android regressions include existing text/image/GPS Firebase compatibility and queue/cache tests, media failure/cancellation states, actual full generated video/audio playback, actual ML model output, missing-backend UI, no-key map UI, persisted destination routing, and a real system notification with private text, ML classification, repeat suppression and correct-chat tap. They do not substitute for two physical devices. A notification-test cleanup problem caused by ActivityScenario matching the original launch intent was corrected without removing its notification/tap assertions; final 20-test execution passed.

The normal APK is **build/remaining-phases-20261026-051649-063/normal/app/outputs/apk/debug/app-debug.apk**, SHA-256 **a2c99acd3e280a82d5ac18e2ed9f554f4dd80f4dabd496ea19bfcf3978b37910**. It uses the normal package `com.jos.myapplication`. Demo builds use `.demo` and local Firebase only. The user's original AVD data and all physical phones were left untouched; a separate read-only AVD data directory was used. No release signing/configuration migration was performed.

## Media size, privacy and remaining service settings

Android `chat_media_max_bytes` and backend `MEDIA_MAX_BYTES` are both exactly **50,000,000**. The existing streaming path, bounded temp files, MIME/container/hash validation, membership checks and immutable retry IDs are preserved. Current live tests uploaded/retrieved an 8,033-byte MP4 and 64,044-byte WAV between two local Firebase test users through **real private Supabase**. The Android regression played complete generated clips/audio; MP3/M4A private transport is covered by backend fixtures. Human/long MP4/MP3/M4A playback on actual phones is still manual.

A new read-only bucket inspection confirmed **private=true**, **file_size_limit=52,428,800**, **allowed_mime_types=null**. That bucket permits the required size; it is not numerically equal to the application's stricter decimal ceiling. Phase 1 already passed a real exact-50,000,000-byte Supabase upload/download and unauthorized-access check; current backend tests again cover exactly that boundary and one byte above, using file-streamed HTTP and injected storage. No additional 50 MB cloud object was uploaded in these phases. If exact bucket equality is desired, the manual SQL/settings steps in SETUP.md set it to 50,000,000 while preserving the required **public=false** flag. No bucket/global policy setting was modified. Current global configuration permits the demonstrated uploads; its dashboard value was not independently read.

Two new small synthetic private Storage objects from this round trip remain for inspection; their exact paths are in `device-verification-markers.log`. Existing Phase 1 fixtures remain as previously documented. No existing remote object was overwritten or deleted. The tested transport is the local loopback Node backend over emulator networking/USB reverse; no hosting proxy or website was deployed. Any future proxy must allow at least 50,000,000 request bytes and sufficient timeout. Whole-file safe retries are supported, not resumable TUS. Full files use disk/cache, not a 50 MB in-memory buffer.

## Manual configuration and acceptance still needed

1. **Firebase token writes:** In the existing project's Firebase Console → Firestore Database → Rules, compare the deployed rules with this project's `firestore.rules`, especially the owner-only `users/{uid}/devices/{deviceId}` block and conversation/message membership reads. Retain unrelated deployed collections/policies. Review App Check enforcement/authorized app setup if present rather than disabling it. Publish only a reviewed compatible rules update manually, then re-open Notifications and confirm Device registration: saved. Source tests already exercise own writes, rotation and rejecting other users. Do not use open/test-mode rules. See [Firebase rule deployment guidance](https://firebase.google.com/docs/firestore/security/get-started).
2. **Real FCM:** After registration succeeds, keep `npm start` running and press **Send test push to my devices**. Check actual tray display, then message A→B on another screen, in background and after its process is gone (without force-stop). Tap must open its chat. Repeat one dispatch ID, deny permission, and switch accounts. If sender calls then fail, inspect the existing project's Cloud Messaging API V1 and existing Admin Messaging permission; follow [FCM error guidance](https://firebase.google.com/docs/cloud-messaging/error-codes). No API/billing/credential change was performed. The optional old-client worker additionally requires the reviewed index; the new route does not.
3. **Native Google Maps/campus:** Follow SETUP.md: restricted package/SHA-1/API key in ignored `maps.properties`, Maps SDK enabled in the intended Google project, billing requirement reviewed by the owner, then rebuild. No key or billable API was activated here. Staying without that setup uses the OSM fallback and external Google Maps directions. Enter real verified campus landmarks, then verify native tiles, current fix, selected marker and real walking directions on a phone. This acceptance remains open.
4. **Supabase:** No dashboard change is required to permit the tested 50 MB path. Exact decimal bucket equality is optional manual alignment; retain private storage and allow the supported media MIME types if adding restrictions. Do not recreate the working project/key/bucket.
5. **Two phones:** Check existing sign-in/text/history/order/offline queue/image preview, original photo retrieval, video/audio pick/send/progress/receiver download/play/save, failures/retries/no duplicates, one-time GPS cards on both devices, live foreground map, assistant cancellation/retry and all notification states. Codec/browser/OS battery behaviour cannot be inferred from one emulator.

## Opening and running in Android Studio

1. Open **C:/Users/hp/AndroidStudioProjects/ChatSphere** with Android Studio (not the old OneDrive source). Let Gradle sync. Use the existing Android Studio bundled **JBR 25**, SDK 37 and existing wrapper/plugins; no broad upgrade is required. `local.properties`, SDK paths, Firebase client JSON and IDE deployment selection were preserved.
2. Select the **app** debug configuration and each connected test phone, then Run. Android Studio builds the normal app without emulator flags. To install the already-built APK directly, use `adb -s SERIAL install -r` with the normal APK path above. Do not uninstall/clear app data just to test these changes.
3. In a separate terminal at the project `backend` directory, run `npm start` (Node 24+). Reuse the existing ignored `.env`. If a previous backend is already running on 8787, stop/restart that same local process when ready so it loads the new code; the pre-existing user process was not killed automatically.
4. For each USB phone run `adb -s SERIAL reverse tcp:8787 tcp:8787`. In Student AI assistant → Connect backend, save **http://localhost:8787**. For a normal Android emulator use **http://10.0.2.2:8787**. Grant local-network/Nearby devices permission on Android versions requiring it. No key goes into the URL/app.
5. Keep the computer/backend/USB connections available for media authorization, AI and authenticated push dispatch. Configure notification permissions and perform the manual checks above. `npm run push` is optional for older clients and needs the missing index; it is not needed for new Android post-save dispatch.

Quick local recheck from the project root:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
cd backend
npm test
```

## Concise Experiments 6–10 demo checklist

| Experiment | Demonstration | Honest qualification |
|---|---|---|
| 6 — campus navigation | Add verified campus gate/library, select one, show current position, choose walking, open Google directions | Native Google tiles and real campus route require owner configuration/physical verification; OSM/destination/intent flows tested |
| 7 — network/media/GPS | A→B text, original image, MP4 and WAV/MP3; full receiver playback; share a one-time accurate location; briefly go offline and retry | Real private video/WAV round trip and compatibility tests passed; two-phone/long-codec checks remain |
| 8 — AI chatbot | Ask a harmless study question, show genuine Gemini response, Stop waiting and retry same request | Real provider passed; no private chat history is sent by smart replies/alerts |
| 9 — ML | Receive a meeting question after a time discussion; show three diverse reply chips; select/edit without auto-send; show source label | Actual on-device ML inference passed; clearly label authored fallbacks; no accuracy claim |
| 10 — AI alerts/FCM | Enable notifications + AI Study alerts, send incoming academic text, show one generic Study alert and tap to chat; owner-only self-test | Local ML/system-notification/tap tests passed; real FCM remains blocked at production device registration and must be verified after configuration review |

## Preservation, backups and exact changed files

Before these phases, **148 source/project files** were byte-hashed and backed up to **C:/Users/hp/AndroidStudioProjects/ChatSphere/build/remaining-phases-20261026-051649-063/before**. All 148 still exist. Relative to that snapshot, **29 existing files were modified and 22 source/documentation files added (51 total)**; the other 119 baseline files are unchanged. This is a source snapshot, not a new full .git/secrets backup. The earlier full destination backup remains at **C:/Users/hp/AndroidStudioProjects/ChatSphere-backup-before-transfer-20261025-152623**. Phase 1's measured media report and snapshot remain unchanged at `docs/PHASE1_MEDIA_REPORT.md` and `build/phase1-20261026-043226-129/before`.

Protected SHA-256 checks confirm **local.properties**, **app/google-services.json**, **backend/.env** and the pre-existing **.idea/deploymentTargetSelector.xml** are byte-identical to the start of these phases. The existing branch/HEAD and empty staged diff are preserved. The older **C:/Users/hp/OneDrive/Documents/ChatGPT/chatshpere2.0** checkout was not edited. Credentials were not rotated/replaced or copied into Android. Exact configured private-key values were absent from all scanned nonignored source, current logs and APK entries. Logs contain no prompts/tokens/signed links from human accounts.

Paths below are relative to the exact Android Studio root stated above; `changes.json`, `final-audit.json` and the test logs in the evidence folder provide the machine-readable audit.

| File | Change | Purpose |
|---|---|---|
| `.gitignore` | modified | Ignore the optional local Maps client-key file. |
| `README.md` | modified | Describe the integrated features and current verification boundaries. |
| `app/build.gradle.kts` | modified | Add the required Maps SDK and read its restricted key only from ignored/local configuration. |
| `app/src/androidTest/java/com/jos/myapplication/CampusNavigationTest.kt` | added | Verify persisted destination add/edit, validation, selection and emitted walking-directions intent. |
| `app/src/androidTest/java/com/jos/myapplication/LivePushIntegrationTest.kt` | added | Explicit opt-in synthetic-account real FCM/self-test/tap/Gemini verifier; cloud registration currently blocked. |
| `app/src/androidTest/java/com/jos/myapplication/NotificationFlowTest.kt` | added | Verify real system notification privacy, local ML classification, deduplication and chat tap on demo Firebase. |
| `app/src/androidTest/java/com/jos/myapplication/OnDeviceMlTest.kt` | modified | Require real ML model output as well as exactly three displayed suggestions. |
| `app/src/main/AndroidManifest.xml` | modified | Supply the native Maps API-key metadata; no private server secret. |
| `app/src/main/java/com/jos/myapplication/ai/AssistantClient.kt` | modified | Use adequate timeouts, stable IDs, account checks and one bounded transport/busy retry. |
| `app/src/main/java/com/jos/myapplication/ai/AssistantRetryPolicy.kt` | added | Keep client retries bounded and avoid repeating backend provider retry sequences. |
| `app/src/main/java/com/jos/myapplication/firebase/ChatRepository.kt` | modified | Queue a separate authenticated push-dispatch job after a successful existing message/media send. |
| `app/src/main/java/com/jos/myapplication/location/CampusPlaces.kt` | added | Validate and persist real user-entered campus destinations without inventing campus data. |
| `app/src/main/java/com/jos/myapplication/location/DirectionsPolicy.kt` | added | Validate destination coordinates and generate supported Google Maps directions URLs. |
| `app/src/main/java/com/jos/myapplication/location/MapIntents.kt` | modified | Extend existing location intents with optional origin and route mode. |
| `app/src/main/java/com/jos/myapplication/ml/MessageCategories.kt` | modified | Expose score/known-word evidence from the existing local trained category classifier. |
| `app/src/main/java/com/jos/myapplication/ml/ReplyQuality.kt` | added | Clean grammar, reject invalid output, remove near-equivalent replies, and fill labelled local fallbacks. |
| `app/src/main/java/com/jos/myapplication/ml/SmartReplies.kt` | modified | Preserve ML Kit inference/history while applying quality validation and timeout fallback. |
| `app/src/main/java/com/jos/myapplication/ml/StudyAlerts.kt` | added | Apply consent, actual Study prediction, score/evidence threshold and cooldown. |
| `app/src/main/java/com/jos/myapplication/navigation/AppNavigation.kt` | modified | Validate notification taps and route message/self-test alerts to the correct existing screen. |
| `app/src/main/java/com/jos/myapplication/network/BackendConnection.kt` | modified | Parse typed safe errors and read JSON bodies off the UI thread; preserve cancellable transport. |
| `app/src/main/java/com/jos/myapplication/network/BackendFailure.kt` | added | Carry safe status, error category, retryability and Retry-After to Android. |
| `app/src/main/java/com/jos/myapplication/notifications/ChatMessagingService.kt` | modified | Register rotated tokens and route high-priority data messages through bounded work. |
| `app/src/main/java/com/jos/myapplication/notifications/ChatNotifications.kt` | modified | Handle secure token rotation/sign-out, authoritative validation, persistent deduplication and local ML alerts. |
| `app/src/main/java/com/jos/myapplication/notifications/NotificationWorker.kt` | modified | Propagate notification kind and use bounded network-constrained retries/eligible expedited work. |
| `app/src/main/java/com/jos/myapplication/notifications/OutgoingPushWorker.kt` | added | Durably request sender-authorized FCM after saved messages without requiring a collection-group index. |
| `app/src/main/java/com/jos/myapplication/screens/assistant/AssistantScreen.kt` | modified | Show waiting/retry states and allow cancellation while retaining the durable message/request ID. |
| `app/src/main/java/com/jos/myapplication/screens/individualchat/IndividualChatScreen.kt` | modified | Retain the three-reply/manual-send UI while correctly labelling model versus fallback output. |
| `app/src/main/java/com/jos/myapplication/screens/maps/GoogleMapPanel.kt` | added | Provide lifecycle-managed native Google Maps, markers, current-location layer and loading/failure handling. |
| `app/src/main/java/com/jos/myapplication/screens/maps/MapScreen.kt` | modified | Extend the existing live/OSM map with native-map switching, saved destinations, editing and directions controls. |
| `app/src/main/java/com/jos/myapplication/screens/notifications/NotificationsScreen.kt` | modified | Add owner-only real push self-test and optional experimental AI study alerts. |
| `app/src/main/java/com/jos/myapplication/screens/profile/ProfileScreen.kt` | modified | Unregister the current device safely during existing sign-out actions. |
| `app/src/test/java/com/jos/myapplication/AssistantRetryPolicyTest.kt` | added | Check one-retry bounds, Retry-After and permanent/provider failure exclusions. |
| `app/src/test/java/com/jos/myapplication/DirectionsPolicyTest.kt` | added | Check valid coordinates, safe route modes and correct directions URL parameters. |
| `app/src/test/java/com/jos/myapplication/ReplyQualityTest.kt` | added | Check representative contexts, grammar, diversity, malformed output and fallback behaviour. |
| `app/src/test/java/com/jos/myapplication/StudyAlertsTest.kt` | added | Check actual classifier decision, consent/cooldown and bounded notification target fields. |
| `backend/README.md` | added | Document runtime, protected routes, quotas, legacy observer and live-test isolation. |
| `backend/core.mjs` | modified | Carry typed safe API errors and allow a separate bounded message-dispatch rate limit. |
| `backend/gemini.mjs` | modified | Distinguish failures and add bounded provider backoff, Retry-After and per-attempt quota reservation. |
| `backend/push-worker.mjs` | modified | Reuse secure shared delivery, invalid-token cleanup and bounded retries in the existing observer. |
| `backend/push.mjs` | added | Implement owner-only self-tests/validation and sender-authorized saved-message dispatch with private payloads. |
| `backend/server.mjs` | modified | Route authenticated push APIs and return typed sanitized assistant errors. |
| `backend/test-live-gemini.mjs` | modified | Enforce the existing persistent quota during a harmless real-provider check; print no response text. |
| `backend/test/live-push-broker.mjs` | added | Provide a gated loopback-only verifier with fresh synthetic identities and exact fixture cleanup. |
| `backend/test/push.test.mjs` | added | Test notification authentication, ownership, membership, private payloads, retries and duplicates. |
| `backend/test/reliability.test.mjs` | added | Test provider backoff, Retry-After, quotas, permanent failures and request coalescing. |
| `docs/FINAL_PHASES_REPORT.md` | added | Record exact changes, measured outcomes, external blockers and demo instructions. |
| `docs/SETUP.md` | modified | Replace stale setup statements with current Maps, media, ML, FCM and Gemini configuration steps. |
| `docs/TESTING.md` | modified | Update regression coverage and separate real-device/cloud checks from fixture passes. |
| `firestore.rules` | modified | Add owner-read/server-write-only expiring push-test records; existing access rules retained. |
| `maps.properties.example` | added | Document the ignored restricted Android Maps client-key field without a real key. |
| `test-support/rules.test.mjs` | modified | Add device ownership/rotation and server-only self-test record security regressions. |

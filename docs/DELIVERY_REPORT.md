# ChatSphere 2.0 delivery report

Date: 25 October 2026, Asia/Calcutta. Source branch: **chatsphere2.0**, selected by the user after verifying that the initially requested `chatsphere-2.0` branch did not exist. Starting commit: `869333a61895b316be7e27da367a84a86d34978c`.

All changes are local and uncommitted. Nothing has been pushed, merged, deployed, subscribed to, or enabled on a paid plan. The original workspace had no application files or user edits; the selected existing GitHub branch was fetched rather than rebuilding a new application.

## Implemented behavior

| Requirement | Implementation | Practical status |
|---|---|---|
| 5. Connectivity/data exchange | Validated Android network callbacks, offline/cached UI, reconnection listener refresh, stable UUID sends, transactional idempotency, durable SQLite outbox, explicit queued/sending/sent/cached/failed labels, timeout and retry, bounded live history plus older-page loading | Implemented; emulator checks exercise real Auth/Firestore clients rather than dummy chats. Transport outages are specifically tested. |
| 6. Maps/navigation | Dedicated live map, foreground-only GPS/network location with accuracy circle and permission handling, following toggle, bundled Leaflet, optional policy-aware OSM tiles, map-point directions via Google Maps URLs, optional verified destinations | User approved OSM and clarified live location as the primary use case. No Google Maps SDK/key/billing account is used. Bundled map initialization is tested; real GPS and public tiles still need manual device checks. |
| 7. Multimedia/GPS chat | System photo picker, sampled/rotated Android image decoding, bounded JPEG preparation, confirmation preview, participant-protected atomic Firestore media/message writes, received-photo display/retry, voluntarily confirmed location snapshots and recipient Maps links | Implemented. Real content-URI preparation and two-account photo/location data exchange are tested locally. Picker cancellation and physical GPS denial/unavailability remain manual acceptance steps. |
| 8. AI assistant | Separate labeled conversation, local account-scoped history, loading/error/retry/turn limits, authenticated backend client, approved optional local Ollama endpoint, configurable server prompt, request validation/rate limiting/deduplication | Implemented integration. Backend tests use explicit fixtures. Actual Ollama generation is untested because Ollama/model and real-project Admin credentials are not installed/supplied. Empty configuration reports unavailable; no fabricated replies. |
| 9. ML | Real bundled ML Kit Smart Reply inference on-device, suggestions fill composer for review; optional Naive Bayes message category inference fitted from 18 authored synthetic examples | English Smart Reply inference observed on the Android 17 x86_64 emulator. No model training/evaluation claim for Google's pretrained model. Category smoke tests are not accuracy evaluation. |
| 10. Alerts/notifications | Foreground Firestore observer, FCM service, durable WorkManager handling, Android notification permission/channels/preferences, generic payloads, recipient/member validation and deep links, persistent duplicate markers, optional local ML category labels, trusted local FCM sender | Client/sender code implemented. No real FCM transport or closed-app delivery claim: private Admin credentials, index/API configuration and two physical-device tests remain required. |

Authentication, profiles, local profile photos, registered-user contact discovery, deterministic one-to-one conversations and per-user hidden markers remain in the existing Compose/navigation architecture. Sign-in/session restoration, in-flight auth guards, profile repair and off-main-thread registration cache writes were added. The original registered-user directory is retained; no separate saved-contact collection existed in this branch. Fake presence/read receipts and empty microphone/voice controls were removed from the active chat UI; no working voice feature was present to preserve.

## Data authority, migration and security

Firebase Auth owns identities; Firestore owns shared profiles, conversations, immutable message metadata and small media documents. SQLite version 2 keeps the original `users(id,name,email)` table and adds account-scoped `cache_records` and `cached_messages`. The migration is additive, with an instrumentation fixture verifying preservation of a version-1 user and ID.

Recent cache retains up to 300 acknowledged messages per conversation and all unresolved outbox rows, up to 50 media records and 1000 notification markers per account. Older online history is paginated in batches of 100 and remains in memory during that visit; server history is not deleted. Outbox items are only labeled sent after a successful transaction/server observation. Retry reuses the same document ID. Offline/transport failures remain queued for recovery; online authorization/service failures are visible and retryable. If the process is killed, queued sends resume when ChatSphere next starts/reconnects; no always-running background service is promised.

Firestore rules and Android/backend code were inspected. Rules tests verify authentication, participant isolation, sender identity, immutable messages/media and owner-only profile changes, hidden markers and device tokens. Only the existing authenticated profile directory exposes names/emails; it is not public internet access. New messages validate type, size and coordinates; client timestamps more than five minutes into the future are rejected. No production deployed-rule audit or deployment was performed. Admin SDK bypasses client rules, so its credential stays on the user's computer; the sender independently validates participants.

No private AI API or FCM Admin key is embedded in Android. Existing tracked Firebase client configuration was preserved; `.gitignore` does not remove that file or historical values from Git. No new private credential was introduced. Caches/media are private to the Android app sandbox and account-scoped in application code, but are not end-to-end encrypted. Shared locations are explicit snapshots; map location collection stops when its screen is not resumed. AI receives only its own bounded conversation; human chats are not attached. See SETUP.md for ML metrics, tile privacy and local server retention.

## Verification results

The baseline debug APK and generated unit test passed. Connectivity/cache, map/auth/navigation and multimedia/ML phases were built independently. The final run after the last queue adjustment also passed; results below are observed outcomes rather than expected behavior.

| Check | Result |
|---|---|
| Normal debug build | Passed (`:app:assembleDebug`) |
| Normal unsigned release build | Passed (`:app:assembleRelease`) |
| JVM unit tests | 5 passed, 0 failed (4 feature policies + original example) |
| Android lint | Passed with 0 errors and 40 warnings (see the HTML/XML report) |
| Local Firestore security rules | 10 passed, 0 failed |
| Local backend contract/security | 4 passed, 0 failed |
| Backend production dependency audit | 0 known vulnerabilities |
| Local emulator-tool dependency audit | 0 known vulnerabilities after compatible overrides |
| Android 17 / API 37 device suite | **OK (7 tests), 0 failures**, direct AndroidJUnitRunner, 20.634 seconds; Pixel_6a, x86_64, 16 KiB-page emulator |
| Whitespace check | `git diff --check` passed |
| Real Firebase two-device accounts / deployed rules | Not tested; no production deployment |
| Real Ollama generation / real FCM / physical GPS | Not tested; manual setup/device checks required |

Important local findings: OneDrive reparse tags affected both generated outputs and source XML. Final verification uses ordinary-file source materialized outside OneDrive and hash-checked against the original Git branch; `tools/Prepare-LocalBuild.ps1` reproduces that safely. Android 17 required the new local-network permission for emulator/local-AI sockets. A connectivity `onLost` race and offline-error queue retention were corrected during actual transport testing. The AGP connected-test engine produced a zero-test success, so direct AndroidJUnitRunner results, not that misleading summary, determine device-test status.

The temporary Firebase services and headless Android emulator were stopped after testing; test port forwards were removed and the emulator's initial Wi-Fi/mobile-data settings restored. Only the separate demo application was installed for these tests. The normal application package and real Firebase accounts were not used by the device suite.

Artifacts on this machine:

- Normal debug APK: `C:/Users/hp/.codex/visualizations/2026/10/24/01a16ffe-39c6-7691-a6ad-74f95c800480/android-build/app/outputs/apk/debug/app-debug.apk`
- Unsigned release APK: same build root under `app/outputs/apk/release/app-release-unsigned.apk` (supply your own signing configuration for distribution).
- Isolated demo APK: sibling `android-demo-build/app/outputs/apk/debug/app-debug.apk`, package `com.jos.myapplication.demo`.
- Lint HTML: normal build root under `app/reports/lint-results-debug.html`.
- Unit XML: normal build root under `app/test-results/testDebugUnitTest/`.
- Actual direct Android runner output: `C:/Users/hp/.codex/visualizations/2026/10/24/01a16ffe-39c6-7691-a6ad-74f95c800480/android-instrumentation-final.txt`.
- Byte-verified build snapshot: `C:/Users/hp/.codex/visualizations/2026/10/24/01a16ffe-39c6-7691-a6ad-74f95c800480/verification-source/`; all Android/Gradle build inputs are compared with the original repository. Edit the original source, not this temporary build copy.

## Manual steps, in order

1. Follow SETUP.md to open/build outside OneDrive if necessary and confirm SDK/JDK paths. Start from the normal debug APK for real accounts; the `.demo` app deliberately uses local services.
2. In your existing Firebase project, confirm Spark, Email/Password authentication and your local `google-services.json`. Review current deployed rules and manually publish the supplied rules/indexes only after checking compatibility. No data needs to be erased.
3. Test real accounts on two devices: auth/profile/contact/history/text, then airplane mode and retry. `TESTING.md` gives expected outcomes for every feature.
4. Test the photo picker, permission denial and voluntary location confirmation. Enable OSM tiles only after reading the in-app notice; use live location or optionally supply actual verified campus destinations. No campus coordinates/API key are required by default.
5. For actual AI, install Node 24 and Ollama, download the documented local model, create a private server credential outside the repo, set `backend/.env` and the public Android assistant endpoint, start the server, and allow local-network permission on Android 17. Use adb reverse for a USB phone. No AI API subscription is needed.
6. For closed-app alerts, enable/check the existing project's FCM v1 API, deploy the required index, start `npm run push` on your computer and grant Android notification permission. Keep the sender running and test taps, duplicate payloads, foreground suppression and account switching on real devices.
7. Check Firebase usage, OSM policy compliance and local retention. Leave Spark/billing unchanged unless you separately choose another service. No paid deployment is part of these deliverables.

Required non-secret settings: SDK location, `assistant_endpoint`, Firebase project ID, local server host/port, Ollama URL/model, optional system prompt and state-database path. Required private server credential: an application-default/service-account credential referenced by `GOOGLE_APPLICATION_CREDENTIALS`; its value is never requested in chat or copied to the app. See the exact console paths, commands, permission flows, provider links and cost/account table in SETUP.md.

## Known limitations and incomplete external validation

- Google Maps SDK was intentionally replaced by the user-approved free-first OSM viewer plus Google Maps URLs. If the college rubric strictly requires Google's embedded SDK, that remains a separate billing/key approval and implementation; it has not been silently claimed complete.
- OSM tiles are optional community infrastructure without an SLA. There is no offline-area download or in-app turn-by-turn router. The default destination list is empty, with unverified examples in a separate file.
- Photos are small JPEGs in quota-limited Firestore; this is not scalable cloud media storage or full-resolution sharing. No byte-level upload percentage is available. Profile photos are still device-local.
- Compatibility retains client millisecond timestamps for ordering/preview and stable IDs for ties. Server commit timestamps are added for trusted notifications. Keep device clocks accurate; late offline messages can appear at their original position in history. This is not a server-assigned global sequence, and the live listener initially loads only the newest 100 timestamped records.
- No presence service or read-receipt protocol is implemented. Cache/outbox uses local SQLite rather than a second authoritative database. The original directory reads all registered profiles and must be redesigned/limited carefully before a large deployment to avoid read costs and privacy changes.
- Smart Reply is English/confidence/content limited. Category inference is optional, tiny and unbenchmarked. These are real inference paths, not claims of a trained production classifier.
- Real LLM responses require installation/configuration and may time out on slow hardware. The local model is small and can be inaccurate. Backend rate limits are in memory; idempotent response retention is 24 hours in local SQLite.
- FCM has not been validated against real devices or privileged credentials. The bounded sender watches new server-timestamped records after startup, has no downtime backfill and must stay on. OS force-stop/Doze/OEM policies can delay or prevent delivery. Historical clients without new schema timestamps are outside that worker's watch query.
- Production account flows, system picker interactions, real GPS accuracy, tile availability and notification permission/transport behavior still need the manual acceptance matrix. Local emulator success is not represented as a production deployment or security certification.

## Files changed and added

The exact list is populated from Git below. Generated build caches, dependency directories, local SDK properties, emulator logs and credential files are excluded.


### Modified (14)

- `.gitignore`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/jos/myapplication/ExampleInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/jos/myapplication/MainActivity.kt`
- `app/src/main/java/com/jos/myapplication/database/DatabaseHelper.kt`
- `app/src/main/java/com/jos/myapplication/firebase/ChatRepository.kt`
- `app/src/main/java/com/jos/myapplication/navigation/AppNavigation.kt`
- `app/src/main/java/com/jos/myapplication/screens/auth/CreateAccountScreen.kt`
- `app/src/main/java/com/jos/myapplication/screens/auth/LoginScreen.kt`
- `app/src/main/java/com/jos/myapplication/screens/chats/ChatsScreen.kt`
- `app/src/main/java/com/jos/myapplication/screens/individualchat/IndividualChatScreen.kt`
- `app/src/main/java/com/jos/myapplication/screens/notifications/NotificationsScreen.kt`
- `build.gradle.kts`

### Added (56)

- `README.md`
- `app/src/androidTest/java/com/jos/myapplication/CacheMigrationTest.kt`
- `app/src/androidTest/java/com/jos/myapplication/FirebaseIntegrationTest.kt`
- `app/src/androidTest/java/com/jos/myapplication/OnDeviceMlTest.kt`
- `app/src/androidTest/java/com/jos/myapplication/PhotoPreparationTest.kt`
- `app/src/androidTest/java/com/jos/myapplication/ScreenSmokeTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/res/xml/debug_network_security.xml`
- `app/src/main/assets/map/LEAFLET-LICENSE.txt`
- `app/src/main/assets/map/config.json`
- `app/src/main/assets/map/destinations.example.json`
- `app/src/main/assets/map/leaflet.css`
- `app/src/main/assets/map/leaflet.js`
- `app/src/main/assets/map/map.css`
- `app/src/main/assets/map/map.html`
- `app/src/main/assets/map/map.js`
- `app/src/main/java/com/jos/myapplication/ChatSphereApp.kt`
- `app/src/main/java/com/jos/myapplication/ai/AssistantClient.kt`
- `app/src/main/java/com/jos/myapplication/data/Message.kt`
- `app/src/main/java/com/jos/myapplication/location/DeviceLocation.kt`
- `app/src/main/java/com/jos/myapplication/location/MapIntents.kt`
- `app/src/main/java/com/jos/myapplication/media/PhotoPreparation.kt`
- `app/src/main/java/com/jos/myapplication/ml/MessageCategories.kt`
- `app/src/main/java/com/jos/myapplication/ml/SmartReplies.kt`
- `app/src/main/java/com/jos/myapplication/network/ConnectivityMonitor.kt`
- `app/src/main/java/com/jos/myapplication/network/LocalNetworkAccess.kt`
- `app/src/main/java/com/jos/myapplication/network/Tasks.kt`
- `app/src/main/java/com/jos/myapplication/notifications/ChatMessagingService.kt`
- `app/src/main/java/com/jos/myapplication/notifications/ChatNotifications.kt`
- `app/src/main/java/com/jos/myapplication/notifications/NotificationWorker.kt`
- `app/src/main/java/com/jos/myapplication/screens/assistant/AssistantScreen.kt`
- `app/src/main/java/com/jos/myapplication/screens/individualchat/MessageContent.kt`
- `app/src/main/java/com/jos/myapplication/screens/maps/MapScreen.kt`
- `app/src/main/res/drawable/ic_notification.xml`
- `app/src/main/res/values/integrations.xml`
- `app/src/test/java/com/jos/myapplication/FeaturePolicyTest.kt`
- `backend/.env.example`
- `backend/core.mjs`
- `backend/firebase.mjs`
- `backend/package-lock.json`
- `backend/package.json`
- `backend/push-worker.mjs`
- `backend/server.mjs`
- `backend/store.mjs`
- `backend/test/server.test.mjs`
- `docs/DELIVERY_REPORT.md`
- `docs/IMPLEMENTATION_PLAN.md`
- `docs/SETUP.md`
- `docs/TESTING.md`
- `firebase.json`
- `firestore.indexes.json`
- `firestore.rules`
- `test-support/package-lock.json`
- `test-support/package.json`
- `test-support/rules.test.mjs`
- `tools/Prepare-LocalBuild.ps1`

## Direct dependency inventory

The retained Android dependencies are Firebase BoM 34.18.0 (Auth/Firestore), Compose BoM 2026.02.01 (UI/graphics/tooling/Material3/test), Core KTX 1.19.0, Activity Compose 1.13.0, Lifecycle Runtime KTX 2.11.0, Navigation Compose 2.8.4, Material Icons Extended 1.7.5, JUnit 4.13.2, AndroidX JUnit 1.3.0 and Espresso 3.7.0. Build tools remain AGP 9.4.1, Kotlin Compose 2.2.10, Google Services plugin 4.5.0, Gradle 9.6.0 and the existing Foojay toolchain resolver 1.0.0. Versions are preserved in app/build.gradle.kts and gradle/libs.versions.toml.

New direct runtime dependencies are Firebase Messaging through the same BoM, ML Kit Smart Reply 17.0.4, WorkManager KTX 2.10.0, and bundled Leaflet 1.9.4. The local backend uses Node 24+ and Firebase Admin 14.5.0; Ollama and Qwen2.5 0.5B are optional user-installed local inference components. Local test tooling uses Firebase JS 13.1.0, rules-unit-testing 6.0.0 and firebase-tools 15.34.1 with compatible grpc/OpenTelemetry/uuid overrides. Exact transitive npm versions and integrity values are in the two package-lock.json files; Android resolves its transitive graph from the retained version catalog/BoMs. Android location, photo picker, SQLiteOpenHelper, ImageDecoder and WebView use platform/AndroidX APIs rather than new hosted services. Cost/account/privacy/licensing details and official provider links are in SETUP.md.

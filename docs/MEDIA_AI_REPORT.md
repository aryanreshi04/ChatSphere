# ChatSphere media and AI implementation report

Historical report from 25 October. The later [Phase 1 media report](PHASE1_MEDIA_REPORT.md) supersedes its media size/support/live-verification status. The user subsequently confirmed real Gemini replies.

25 October 2026. Work was performed only in **C:\Users\hp\AndroidStudioProjects\ChatSphere**, retaining the existing chatsphere2.0 branch and its previous uncommitted implementation and unrelated IDE changes. HEAD remains 869333a61895b316be7e27da367a84a86d34978c. No commit, staging, push, merge, reset, deployment, publication, account registration or billing activation was performed.

**The integration code is implemented and locally verified. Live Supabase and Gemini integrations are not yet verified.** The owner confirmed that neither service was configured, and no backend/.env or private credentials were present. The live Gemini test explicitly skipped without making a provider request.

## Changes and reasons

The former 120 KiB restriction came from PhotoPreparation's compression loop, MessagePolicy's legacy byte limit, repository base64 validation, the old image viewer and Firestore's legacy media rules. That representation is retained for existing images and queued retries. Already compressed photos cannot regain lost detail: select the original again for the new upload path.

New photos have an Original/Balanced choice. Original preserves the full selected bytes and their metadata; Balanced resizes to 2560 pixels/JPEG without forcing a 120 KiB target. The default new-file limit is 10,000,000 bytes, configurable in Android, backend and Storage up to a conservative decimal 50,000,000-byte ceiling, subject to actual bucket/project settings. The app counts actual bytes, checks magic bytes and decodes the image. The backend independently checks count, signature and checksum.

New pending media uses private files and account-scoped SQLite metadata. Reconnect/restart preserves the original message ID and file reference. The UI shows bytes sent to the backend, then a distinct verification/storage phase, Stop upload, specific errors and Retry. Stop is retained even if network connectivity drops. Successful files move to an account cache bounded at 100 MB. Pending/failed originals are retained for retry; evicted successful files can be downloaded again.

The existing trusted backend verifies Firebase sign-in and authoritative Firestore conversation membership. It refuses a public Supabase bucket, writes an immutable object at conversationId/messageId without upsert, and creates the Firestore message/preview transactionally. Repeating an ambiguous request verifies/reuses the object and message. Only metadata and the object path are in Firestore. Server-only creation prevents clients from forging Supabase references. Authorized recipients obtain short-lived 60-second download links, validate full-file size/checksum, and can open the original in a device viewer or export it through Android's document picker. No Firebase token is sent to Supabase during the signed download.

Gemini's REST adapter uses only a backend environment key, a configurable current free-tier standard model (default gemini-3.1-flash-lite), bounded assistant-only text and output, useful configuration/quota/safety/timeout errors, and existing idempotent request IDs. The backend has per-user rate limits, two-generation concurrency and a persistent daily provider cap. Google never receives Firebase tokens, private chat histories or account IDs from this integration. The app includes 18+/non-sensitive-study acknowledgement, data-use disclosure, and a trusted public backend base-URL editor. Ollama remains an optional local alternative.

No functioning audio send/playback path existed; the inspected voice data was unused dummy content. Unsupported audio/attachments remain explicit. Existing text, locations, legacy photos, offline behavior, smart replies and bundled maps were preserved.

## Verification results

| Check | Observed result |
|---|---|
| Android normal debug build | Passed |
| JVM policy tests | 8 passed |
| Android lint | Passed: 0 errors, 42 warnings |
| Backend regression suite | 13 passed; provider/storage fixtures explicitly mocked |
| Firestore rules suite | 11 passed against local demo-chatsphere |
| Backend Firebase media integration | 1 passed with real local Auth tokens/Admin transaction/Firestore and mocked Storage |
| Android instrumentation | 11 passed on API 37 x86_64; final output OK (11 tests), 30.871 seconds |
| Total automated tests | **44 passed** |
| Live Gemini provider test | **SKIPPED**: no configured key/confirmation; no provider request sent |
| Live Supabase Storage | **Not tested**: no project/bucket/credentials supplied |
| Production Firebase or physical-device cloud round trip | Not exercised by these isolated tests |

Tests cover file-size boundaries, input signatures/checksums, byte transport above 120 KiB, failures between Storage and Firestore, one-object/one-message retry, denied membership/forged paths, public-bucket rejection, cancelled HTTP upload, truncated downloads, real HTTP-client cancellation, SQLite reopen/isolation, valid large PNG Original byte preservation and Balanced resize, invalid photo bytes, existing emulator messaging/transport recovery, real on-device Smart Reply and map/assistant initialization. The Android HTTP fixture uses no external service. The original Firebase image integration continues to exercise legacy-photo compatibility.

Builds used separate ignored outputs under build/media-ai-verification. The final normal APK is:

C:\Users\hp\AndroidStudioProjects\ChatSphere\build\media-ai-verification\normal\app\outputs\apk\debug\app-debug.apk

Logs are in that verification directory: android-build-final.log, android-demo-build.log, android-instrumentation.log, backend-tests.log, firestore-rules-tests.log, media-firebase-integration.log and gemini-live-status.log. The lint HTML report is normal/app/reports/lint-results-debug.html. A task-specific file/hash inventory is recorded in changes.json.

The initial selected-file backup is in **build/media-ai-before-2026-10-25T10-42-50-705Z**. It includes originals of the edited files; project-before.json contains the starting source-file hash inventory. Existing Firebase client configuration, SDK settings, maps, the user's IDE deployment selection and other unrelated source files were preserved. No existing project file was deleted.

## External services, limits and manual requirements

| Component | Account/configuration and restrictions |
|---|---|
| Existing Firebase Auth/Firestore | Retained; Spark quotas still apply. Backend needs an approved existing-project Admin credential outside the repository. Review secure rules before real use. No rules/indexes were deployed. |
| Supabase private Storage | User must create a Free project and private chat-media bucket. Free currently includes 1 GB Storage, 5 GB uncached and separate 5 GB cached egress, at most two active Free projects, and a 50 MB upload ceiling. Free projects can pause after a week of inactivity. Keys stay on the backend. |
| Gemini Developer API | User must create an eligible unbilled AI Studio project/key, verify account-specific RPM/TPM/RPD and model availability, and review unpaid-service data use/age terms. No billing was activated. Free prompts/responses may be used/reviewed for improvement under the applicable terms. |
| Local Node backend | No hosted service was created. The computer and backend must stay on for new media authorization/AI. Debug uses emulator or USB loopback; release/public access needs separately secured HTTPS and persistent state. |
| Optional Ollama | Local model installation/resources required; no Gemini key. Real Ollama inference was not tested here. |
| Existing optional FCM | Unchanged: trusted sender and real-device delivery setup still required; no live push test in this change. |
| Existing OSM/Leaflet map | Bundled viewer unchanged; optional public tiles have attribution/caching/usage terms and best-effort availability. No site was published or public tiles automatically downloaded in tests. |
| Existing on-device ML | Bundled Google Smart Reply inference was exercised; its SDK terms/metrics still apply. |
| OkHttp 5.5.1 | Added cancellable streaming HTTP client; open-source library, no account or paid API. |

Official sources and detailed restrictions are linked in [SETUP.md](C:/Users/hp/AndroidStudioProjects/ChatSphere/docs/SETUP.md): [Supabase pricing](https://supabase.com/pricing), [file limits](https://supabase.com/docs/guides/storage/uploads/file-limits), [access control](https://supabase.com/docs/guides/storage/security/access-control), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits), and [terms](https://ai.google.dev/gemini-api/terms).

Uploads retry whole files; byte-range/TUS resumption is not implemented. Supabase recommends TUS above 6 MB. Slow large uploads can require a complete retry, and retries/duplicate verification consume bandwidth. An uploaded object can remain private if a later commit/cancel fails; no automatic remote cleanup runs. Whole Original images retain metadata; Balanced removes it. Large in-chat previews are sampled, while Open full image/Save file use the complete uploaded file. Real HEIF/device-viewer compatibility, physical GPS, cloud quotas and closed-app push require manual device checks.

## Exact Android Studio testing steps

1. Open **C:\Users\hp\AndroidStudioProjects\ChatSphere** itself in Android Studio. Keep the existing branch, Gradle/SDK versions and local.properties. Select JBR/Java 25 and SDK Platform 37, then Sync Project with Gradle Files.
2. Select the **app** run configuration, **debug** variant and emulator/USB phone (Android 9/API 28+), then click Run. Build/run does not commit or push.
3. Follow [SETUP.md section 6](C:/Users/hp/AndroidStudioProjects/ChatSphere/docs/SETUP.md) to create the Free Supabase bucket, manually review/apply the supplied restrictive policy, create the Gemini key, obtain approved Firebase Admin access and fill ignored backend/.env. Never paste keys into Kotlin, Android resources, Git or chat.
4. In Android Studio Terminal, enter backend, run npm ci and npm start. Check the local /health response; it reports configuration, not live model/storage success. Run npm run test:live:gemini only after configuring credentials; require a genuine successful response.
5. Emulator: open Student AI assistant → Connect backend and save http://10.0.2.2:8787. USB phone: run adb reverse tcp:8787 tcp:8787 and save http://localhost:8787. Keep the backend/USB connection running. On API 37 allow the local connection/Nearby devices permission.
6. Sign in with test account A. Choose a photo above 120 KiB, select Original or Balanced, confirm, and inspect progress. Test offline/restart/Retry and Stop. Sign in as B on another connected device and view/save the full file. Check one private Storage object and one Firestore message for each ID; compare downloaded size/checksum.
7. Acknowledge eligible study-only AI use and ask a harmless question. Test Retry, stopped backend and rate-limit/missing-key errors. Existing human chats are not attached.
8. Follow [TESTING.md](C:/Users/hp/AndroidStudioProjects/ChatSphere/docs/TESTING.md) for all automation and device cases. The separate demo package uses local Firebase; its storage/provider integration tests use explicit fixtures, not your live Supabase/Gemini accounts.

All changes remain local and uncommitted. Setup instructions describe manual actions; none were applied to your cloud accounts.

## Files changed for this follow-up

The following list compares against the project's state when this request began, including its existing uncommitted files. It is not a claim that the repository started clean.

| Change | Project-relative path |
|---|---|
| Updated | app/build.gradle.kts |
| Added | app/src/androidTest/java/com/jos/myapplication/MediaOutboxTest.kt |
| Added | app/src/androidTest/java/com/jos/myapplication/MediaTransportTest.kt |
| Updated | app/src/androidTest/java/com/jos/myapplication/PhotoPreparationTest.kt |
| Updated | app/src/main/AndroidManifest.xml |
| Updated | app/src/main/java/com/jos/myapplication/ai/AssistantClient.kt |
| Updated | app/src/main/java/com/jos/myapplication/data/Message.kt |
| Updated | app/src/main/java/com/jos/myapplication/firebase/ChatRepository.kt |
| Added | app/src/main/java/com/jos/myapplication/media/MediaClient.kt |
| Added | app/src/main/java/com/jos/myapplication/media/MediaPolicy.kt |
| Updated | app/src/main/java/com/jos/myapplication/media/PhotoPreparation.kt |
| Added | app/src/main/java/com/jos/myapplication/network/BackendConnection.kt |
| Updated | app/src/main/java/com/jos/myapplication/screens/assistant/AssistantScreen.kt |
| Updated | app/src/main/java/com/jos/myapplication/screens/individualchat/IndividualChatScreen.kt |
| Updated | app/src/main/java/com/jos/myapplication/screens/individualchat/MessageContent.kt |
| Updated | app/src/main/res/values/integrations.xml |
| Added | app/src/main/res/xml/media_paths.xml |
| Added | app/src/test/java/com/jos/myapplication/MediaPolicyTest.kt |
| Updated | backend/.env.example |
| Added | backend/gemini.mjs |
| Added | backend/media.mjs |
| Updated | backend/package.json |
| Updated | backend/server.mjs |
| Updated | backend/store.mjs |
| Added | backend/supabase-storage-policy.sql |
| Added | backend/test-live-gemini.mjs |
| Added | backend/test/media-ai.test.mjs |
| Added | backend/test/media-firestore.integration.mjs |
| Added | docs/MEDIA_AI_REPORT.md |
| Updated | docs/SETUP.md |
| Updated | docs/TESTING.md |
| Updated | firestore.rules |
| Updated | README.md |
| Updated | test-support/rules.test.mjs |

# Phase 1 - Video/audio sharing and 50 MB media path

26 October 2026. Source project: **C:/Users/hp/AndroidStudioProjects/ChatSphere**. Branch **chatsphere2.0**, HEAD **869333a61895b316be7e27da367a84a86d34978c**. Phase 1 only; Phases 2-5 have not been started. No commit, staging, push, merge, reset, deployment, billing activation or credential replacement occurred.

## Outcome and actual verification

MP4 video selection, private upload, sampled thumbnail and full-screen playback are implemented. MP3/M4A/WAV audio selection, private upload, filename/duration, play/pause/seek and full-file saving are implemented. Both reuse the existing stable-ID SQLite outbox, upload progress, cancellation, backend Firebase authentication/membership checks, immutable private Supabase object paths and signed recipient downloads. Receiver playback/download starts only after an explicit action; selecting a file does not send it. No recorder, microphone permission, broad gallery permission, background tracking or new media dependency was introduced.

Real Android-generated AVC MP4 and PCM WAV passed preparation and playback checks. The Android outbox uploaded both to **real private Supabase Storage** through a separate local backend authenticated against **local demo Firebase**; a second synthetic account downloaded both with matching full-file SHA-256 and valid media tracks. Repeating their stable IDs left one message per file. This is real storage/network verification, not production Firebase/two-phone verification. MP3/M4A container/transport validation was tested with synthetic server fixtures; actual MP3/M4A decoder and file-picker UX remain device checks. The video completion test verifies position advancement and the actual completion event; audio play/pause/save presence is exercised. Seek gestures, gallery/document-picker UX, viewer lifecycle/codec differences and exported-file UX need phone acceptance testing.

A separate real-storage integration uploaded and retrieved **exactly 50,000,000 bytes**, matched its SHA-256, denied a third synthetic user and verified stable-ID retry. Fixture HTTP tests reject 50,000,001 bytes and exercise mismatched types/tracks, checksum errors, forged paths, unsafe metadata, revoked/nonmember access, ambiguous commit retries, stopped transport and spool cleanup. Existing text/photo/location, old image format, ordering/history/outbox, map initialization and actual on-device Smart Reply tests remain passing.

## Results

| Check | Actual outcome |
|---|---|
| Baseline backend | 13 passed |
| Baseline debug build + JVM | Successful, 8 JVM tests |
| Final backend | 19 passed |
| Final JVM | 9 passed |
| Firestore security rules, local demo | 12 passed |
| Existing Firebase Auth/Admin transaction integration, local demo | 1 passed |
| Final Android regression | 17 passed, including real video completion/audio controls and stalled-download cancellation |
| Android real private-storage integration | 1 passed; actual generated video/audio and recipient full-file verification |
| Real private-storage 50 MB boundary integration | 1 passed; no production Firestore data involved |
| Total unique final automated checks | **60 passed** |
| Final normal debug build and test APK build | Successful |
| Final lint | **0 errors, 42 warnings** |
| Configured private-key scan | 0 matches in nonignored project source and normal APK |
| Physical two-phone/production Firebase acceptance | **Not performed**; no app was installed on the connected phone |

The original video position-only completion assertion failed; the test now verifies an actual completion callback rather than assuming a post-completion position value. The isolated and final full playback tests passed. A secondary Firebase SDK emulator-instance setup failure in the new live test was fixed by isolated sequential account sign-ins. A new stalled-download test exposed a real socket/coroutine cancellation race; it was fixed without weakening the test, and the final suite passed. Initial failure/diagnostic logs are retained. A Windows documentation encoding write failed and was recovered from the verified pre-edit source backup; changed text files are now valid UTF-8.

## Size, privacy, retries and hosting

Android resource/default, backend default/example and current ignored local `.env` use **50,000,000 bytes**, not 50 MiB. Validation checks actual byte counts in every path, hashes and supported media types. Android uses bounded file I/O; Node spools in bounded chunks to a newly generated private temporary file and streams it to Storage, with at most two concurrent uploads. It no longer concatenates a 50 MB request into RAM. Media requests have a bounded ten-minute timeout. Cancellation closes active calls/body reads and cleans generated temporary files; interrupted/corrupt files are not accepted as complete. Successfully sent files use the existing 100 MB/account cache. An already-cached attachment retry no longer attempts to copy its cache file over itself.

The **existing bucket was read-only checked**: private=true, file_size_limit=**52,428,800**, allowed_mime_types=null. It already permits the intended 50,000,000-byte upload, as the real test confirmed; Android/backend enforce the stricter exact decimal ceiling. Its raw configured ceiling is not exactly the same number. No dashboard setting/policy was changed. For exact bucket equality, [SETUP.md](SETUP.md) supplies a manual SQL update to 50000000 with a public=false condition, preserving the private bucket. The successful real upload demonstrates the global/project size limit allows 50 MB, although its dashboard number was not separately read.

Only the current local loopback Node path was tested; no hosting proxy was added, deployed or validated. If you later use a proxy, configure its request limit at least 50,000,000 bytes and an adequate timeout. Uploads remain whole-file standard uploads, not TUS/byte-range resume; slow connections may need a complete retry. Android codec support varies; use H.264 MP4 and PCM WAV for initial phone checks. Video MP4 and audio MP3 (MPEG Layer III), M4A and WAV are the deliberately supported attachment types; MOV/WebM/OGG/WMA are not silently advertised as supported.

Synthetic private Storage fixtures were deliberately retained; no existing remote object was overwritten/deleted. This run retained one 50 MB boundary object and two successful small video/audio fixture pairs (approximately 50.15 MB total). Exact synthetic paths are in `live-storage-50mb.log`, `live-android-media.log` and `live-android-media-final.log`. Repeating live tests consumes storage/egress and leaves another synthetic object; inspect those exact paths manually if cleanup is desired. No human gallery content or private chat text was used. No Gemini request was made in this phase; its model, credentials and quota settings remain unchanged and its previous success remains user-confirmed.

## Preservation and artifacts

A pre-edit source snapshot of **136 files**, with verified SHA-256, is at **C:/Users/hp/AndroidStudioProjects/ChatSphere/build/phase1-20261026-043226-129/before**. The original Git status/hashes and protected-config hashes are alongside it. This is a source/project-file snapshot, not a new full project/.git/secrets backup. The existing earlier full backup is not removed.

Audit: **25 existing source/project files modified, 12 new source/project files added**; none of the 136 baseline files is missing. The other 111 baseline source/project files are unchanged. The ignored `backend/.env` has only MEDIA_MAX_BYTES changed from 10000000 to 50000000 (verified by reconstructing its original hash). `local.properties`, Firebase client JSON and the pre-existing IDE deployment selection match their baseline hashes. Existing branch/HEAD/index are preserved; all changes remain uncommitted/local. The old OneDrive source directory was not edited. No private credential value is copied into this report.

Evidence root: **C:/Users/hp/AndroidStudioProjects/ChatSphere/build/phase1-20261026-043226-129**.

- Normal APK: `normal/app/outputs/apk/debug/app-debug.apk`.
- Normal build/JVM/lint: `android-final.log`, `normal/app/test-results/testDebugUnitTest/`, `normal/app/reports/lint-results-debug.html`.
- Demo/test APK build: `android-demo-final.log` and `demo/app/outputs/apk/`.
- Android final regression: `instrumentation-regression-final.log`.
- Live Android video/audio: `live-android-media-final.log` (earlier successful pair: `live-android-media.log`).
- Backend/rules/local Firebase: `backend-final.log`, `firestore-rules.log`, `firebase-media.log`.
- Real exact 50 MB storage: `live-storage-50mb.log`.
- Change manifest/security/preservation details: `final-audit.json` and `changes.json`.

## Exact source/project files changed

All paths below are relative to **C:/Users/hp/AndroidStudioProjects/ChatSphere**. Existing unrelated changes are excluded from this phase list. The additional ignored `backend/.env` change is limited to MEDIA_MAX_BYTES and is not a source file to commit.

| File | Why |
|---|---|
| `README.md` | Current media support/limit and link to this phase report; older verification remains historical. |
| `app/src/androidTest/java/com/jos/myapplication/AttachmentFixtures.kt` | Generate actual AVC MP4 and PCM WAV entirely locally; no personal gallery files. |
| `app/src/androidTest/java/com/jos/myapplication/AttachmentPlaybackTest.kt` | Actual audio card controls and video position/completion verification. |
| `app/src/androidTest/java/com/jos/myapplication/AttachmentPreparationTest.kt` | Full-byte picker-copy preservation, tracks/duration, >50 MB and wrong-type rejection. |
| `app/src/androidTest/java/com/jos/myapplication/LiveMediaIntegrationTest.kt` | Explicit opt-in real Supabase video/audio round trip with two synthetic local Firebase users. |
| `app/src/androidTest/java/com/jos/myapplication/MediaOutboxTest.kt` | New metadata/legacy JSON compatibility alongside the existing durable outbox test. |
| `app/src/androidTest/java/com/jos/myapplication/MediaTransportTest.kt` | Stalled response-body cancellation test, retaining interrupted download/upload tests. |
| `app/src/main/AndroidManifest.xml` | Register non-exported full-screen video activity; no new storage/microphone permission. |
| `app/src/main/java/com/jos/myapplication/data/Message.kt` | Optional mediaName/durationMs JSON metadata appended without changing old constructors/legacy defaults. |
| `app/src/main/java/com/jos/myapplication/firebase/ChatRepository.kt` | Queue video/audio through the existing durable media path and parse new optional metadata. |
| `app/src/main/java/com/jos/myapplication/media/AttachmentPreparation.kt` | New bounded picker copy, signature/track checks, filename and duration extraction. |
| `app/src/main/java/com/jos/myapplication/media/MediaClient.kt` | File-stream uploads/download progress, bounded media timeouts, type checks, account cache protection and safe same-file retry. |
| `app/src/main/java/com/jos/myapplication/media/MediaPolicy.kt` | 50,000,000-byte default and shared attachment MIME/type/name validation. |
| `app/src/main/java/com/jos/myapplication/media/VideoPlaybackActivity.kt` | New internal full-screen VideoView player, loading/errors/retry and lifecycle handling. |
| `app/src/main/java/com/jos/myapplication/network/BackendConnection.kt` | Reuse existing authorization; response-body cancellation remains connected until consumption ends. |
| `app/src/main/java/com/jos/myapplication/screens/individualchat/AttachmentContent.kt` | New video/audio cards, thumbnail, explicit download/progress/retry, full-screen launch, audio controls and Save file. |
| `app/src/main/java/com/jos/myapplication/screens/individualchat/IndividualChatScreen.kt` | Video/audio pickers, confirmation and existing outbox progress/cancel/retry UI for the new types. |
| `app/src/main/java/com/jos/myapplication/screens/individualchat/MessageContent.kt` | Dispatch new types to attachment cards; existing text/image/location branches retained. |
| `app/src/main/res/values/integrations.xml` | Android maximum 50000000 bytes. |
| `app/src/main/res/values/media_strings.xml` | New native video-player labels in UTF-8 Android string resources. |
| `app/src/test/java/com/jos/myapplication/MediaPolicyTest.kt` | Byte-limit, supported signatures/type mismatch and filename safety checks. |
| `backend/.env.example` | Document 50000000-byte backend limit; no secret values. |
| `backend/media-files.mjs` | New bounded temp-file spool/hash and MP4 track/WAV chunk/MP3 header validation. |
| `backend/media.mjs` | Authenticated file-stream storage, audio/video metadata, private signed reads and immutable/idempotent paths. |
| `backend/package.json` | Add explicitly gated test:live:media command; no dependency upgrades. |
| `backend/push-worker.mjs` | Recognize video/audio alongside existing message types; push infrastructure otherwise unchanged. |
| `backend/server.mjs` | Allow bounded ten-minute media ingress; Gemini provider/model/quota unchanged. |
| `backend/test/live-media.integration.mjs` | Explicitly gated real private Supabase 50,000,000-byte round trip using local Firebase only. |
| `backend/test/media-ai.test.mjs` | Adapt original media fixtures to file-based service; retain all previous assertions. |
| `backend/test/media-firestore.integration.mjs` | Adapt existing local Firebase media integration to file-based storage fixture. |
| `backend/test/media-phase1.test.mjs` | New real HTTP/file-stream 50 MB boundary/retrieval, formats, authorization, retry and cleanup tests. |
| `docs/MEDIA_AI_REPORT.md` | Mark previous report historical and link to current outcomes without changing old results. |
| `docs/PHASE1_MEDIA_REPORT.md` | This exact change list, verification evidence, preservation audit and remaining checks. |
| `docs/SETUP.md` | Formats, 50 MB configuration/streaming, existing bucket findings and exact optional manual SQL. |
| `docs/TESTING.md` | New suites, live-test gates and physical two-phone acceptance/demo checklist. |
| `firestore.rules` | Clarify image/video/audio backend-only metadata comment; access-control logic unchanged. |
| `test-support/rules.test.mjs` | Reject client-forged video/audio records and verify participant-only reads. |

## Open and test on phones before Phase 2

1. Open **C:/Users/hp/AndroidStudioProjects/ChatSphere** in Android Studio and allow Gradle sync. Keep the configured wrapper, SDK and JBR; no dependency migration is needed.
2. Stop/restart **your own normal backend** from this project's `backend` directory using `npm start` so it loads the new source and MEDIA_MAX_BYTES. The temporary demo backend used a different port (8788); it does not update an already-running normal backend on 8787. Do not point the normal app at the demo backend/project.
3. Run the existing **app/debug** configuration on each API 28+ phone. For USB local development, run the following separately for each connected device, substituting its serial from `adb devices`:

   ```powershell
   & "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s DEVICE_SERIAL reverse tcp:8787 tcp:8787
   ```

4. Keep each phone's normal Firebase account. In **Student AI assistant > Connect backend**, use `http://127.0.0.1:8787` for this USB debug setup (or retain your already-working appropriate HTTPS setup). Grant local-network/Nearby devices permission if Android requests it. No secret key goes into the app.
5. Open the same conversation from two accounts. Test text and an existing image first. Choose a short H.264 MP4, confirm Send, observe upload progress, then Download to play/full-screen on the other phone. Repeat with PCM WAV, MP3 and AAC M4A; check duration/play/pause/seek and Save file.
6. Cancel the picker/preview; deny a permission; stop an upload/download; retry; queue offline and reconnect. Confirm the original message ID is reused without duplicate rows. Check one-time location sharing and Maps opening still work. Use [TESTING.md](TESTING.md) for the complete boundary/privacy/lifecycle matrix.
7. Optional exact Supabase bucket-limit alignment uses the manual SQL in [SETUP.md](SETUP.md); no key creation, API enablement, Firebase rule/index deployment, Google Maps billing or hosting change was performed/needed by this phase.

## Experiment demo checklist / later phases

| Experiment | Current demo and remaining work |
|---|---|
| 6 Maps/navigation | Existing live Leaflet/OSM map and location opening preserved. Google Maps SDK/campus data/directions verification belongs to Phase 3; no campus is invented here. |
| 7 Network/multimedia/GPS | Demonstrate two-account text/image + new video/audio/full-file playback, offline retry and one-time location. Local/emulator + real private storage checks pass; physical two-phone acceptance still pending. |
| 8 AI chatbot | Existing Gemini configuration preserved; real response previously user-confirmed. Intermittent reliability work belongs to Phase 5. |
| 9 ML replies | Existing on-device Smart Reply preserved and regression-tested. Naturalness/diversity/exactly-three improvements belong to Phase 2 and have not been made here. |
| 10 AI alerts/FCM | Existing notification screens/worker/routing preserved, worker recognizes video/audio. Background push and AI-alert completion remain Phase 4, unverified here. |

Phase 1 is ready for physical-device acceptance; do not present Phases 2-5 as completed. The next implementation phase is the existing ML three-reply improvement.

# Beginner setup, costs and privacy

These instructions are for **C:\Users\hp\AndroidStudioProjects\ChatSphere**, on the existing **chatsphere2.0** branch. Nothing below has been deployed automatically. Never share passwords, Firebase Admin JSON keys or sign-in tokens in chat or commit them to Git.

## 1. Android Studio and the first build

1. Open this repository folder in Android Studio. Keep the existing SDK/Gradle versions: compile/target SDK 37, minimum Android 9/API 28, AGP 9.4.1, Gradle 9.6.0, Kotlin Compose plugin 2.2.10. The project requests Java 25 for its Gradle daemon; Android Studio's bundled JBR 25 worked in this environment. Its configured toolchain resolver can download the required JDK. The command-line system Java 17 alone is insufficient for the current Firebase emulator CLI.
2. Open **Settings → Languages & Frameworks → Android SDK**. Install Android SDK Platform 37 and the build tools requested by Gradle. Under **Build Tools → Gradle**, choose Android Studio's JBR/Java 25. Accept SDK licenses through Android Studio. No paid Android Studio account is required.
3. Let Android Studio create ignored `local.properties`. Here the SDK is `C:\Users\hp\AppData\Local\Android\Sdk`; its properties-file value is `sdk.dir=C\:/Users/hp/AppData/Local/Android/Sdk`. Use your own path on another computer.
4. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` on macOS/Linux, or `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` in PowerShell. The existing Gradle cache may require `$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"` in this environment.
5. If Gradle says an output is **not a regular file** in OneDrive, add `-PchatsphereBuildRoot=C:/ChatSphereBuild` using a writable local folder outside OneDrive. If **source files** also acquire unsupported OneDrive reparse tags, output relocation alone is insufficient; pinning did not fully solve that on this machine. Create an ordinary-file snapshot from the original repository:

   ```powershell
   .\tools\Prepare-LocalBuild.ps1 -Destination "$env:LOCALAPPDATA/ChatSphereLocalBuild"
   ```

   Choose a destination that does not exist, outside OneDrive. The script copies Git-tracked and non-ignored new project files plus local.properties, verifies each file's SHA-256, and writes `SOURCE_SNAPSHOT.json`. It never removes or overwrites the original project and never overwrites an existing destination. Open the snapshot folder in Android Studio or run Gradle there. Keep editing/reviewing the original Git repository; create a fresh snapshot after changes. The snapshot has no `.git` directory and is only a build copy. With `-PchatsphereBuildRoot=C:/ChatSphereBuild`, the APK becomes `C:/ChatSphereBuild/app/outputs/apk/debug/app-debug.apk`.
6. Select an API 28+ emulator or phone. The bundled ML library includes native code; test the actual device ABI as well as compilation. Do not infer physical-device or 16 KiB support solely from a successful APK build.

## 2. Existing Firebase project: Auth and Firestore

**Account:** your existing Google/Firebase account. **Payment method:** not required when staying on Spark and using only supported no-cost services. Do not click **Upgrade** or link billing for this implementation.

1. Open [Firebase Console](https://console.firebase.google.com/), select the existing ChatSphere project, and check **Usage and billing** says **Spark**.
2. Under **Project settings → General → Your apps**, select Android package `com.jos.myapplication`. If missing, register that exact package. Download `google-services.json` to `app/google-services.json` locally. Do not paste its values into documentation. The starting repository already tracked a Firebase client configuration; this change adds an ignore entry, which does not remove a previously tracked file or its history. Firebase client keys are not Admin credentials, but restrict their use according to [Firebase API key guidance](https://firebase.google.com/docs/projects/api-keys). No new private key is placed in the APK.
3. Open **Authentication → Sign-in method**, enable **Email/Password**. Phone/SMS authentication is not used.
4. Open **Firestore Database**. Reuse the existing database and region; do not erase data or recreate it in test mode. Firestore retains profiles at `users/{uid}`, conversations at `conversations/{id}`, and message subcollections under each conversation, plus legacy media documents. New image bytes are in Supabase; Firestore stores their authorized object references. Existing name/email profiles and legacy text messages remain readable.
5. Read `firestore.rules`. It permits the existing authenticated profile directory (including emails), owner-only hidden markers/device tokens, participant-only conversations and immutable messages/media. It denies unrecognized collections. Compare it with any existing deployed rules before publishing; additional collections used outside this repository may need their own explicit restrictions. If desired, paste the reviewed file into **Firestore → Rules → Publish** yourself. Publishing has not been performed here.
6. For indexes and the optional sender, install local test tooling with `cd test-support; npm ci; cd ..`, sign in using `test-support/node_modules/.bin/firebase login`, then use the explicit project ID:

   ```powershell
   .\test-support\node_modules\.bin\firebase.cmd deploy --only firestore:rules,firestore:indexes --project YOUR_EXISTING_PROJECT_ID
   ```

   This is a manual deployment command, not part of a build or test. It updates access controls and indexes, not stored messages. Wait for **Firestore → Indexes** to finish building the `messages.serverTimestamp` collection-group index. The `media.base64` field is excluded from indexes to avoid large-field indexing.
7. Run the app on two devices/accounts and follow `TESTING.md`. If a send says **Unconfirmed**, check rules, internet and quota; retrying retains its ID. New chats must be opened online before an offline first message can synchronize. Reopening an established chat offline shows its cached history.

The documented [Firestore free quota](https://firebase.google.com/docs/firestore/quotas) is 1 GiB stored data, 50,000 reads/day, 20,000 writes/day, 20,000 deletes/day and 10 GiB outbound/month. Listeners, rule-dependent document reads and the trusted sender consume quota. Check **Firestore → Usage** and **Usage and billing** regularly; stay on Spark for a hard no-billing choice. Quota exhaustion can make operations fail. Email/password Auth is retained; check its [current limits](https://firebase.google.com/docs/auth/limits) for your project. Do not assume every authentication feature is unlimited.

## 3. Photos, video, audio, private Supabase Storage, and location

New chat images use the private Supabase bucket through the trusted Firebase-authenticated backend in section 6. Firebase Auth, Firestore conversations/messages, SQLite and maps stay in place. Already compressed photos cannot regain lost pixels; select the original file again to send it through the new flow. Existing Firestore/base64 photos and their queued retries remain supported; the old 120 KiB limit applies only to that legacy format. New image bytes never go into Firestore.

Tap the image icon, choose a photo, then choose **Original** or **Balanced**. Original preserves the source bytes, including any EXIF/location metadata. Balanced decodes the image, resizes the longest side to at most 2560 pixels and re-encodes JPEG at a reasonable quality, stripping metadata. It is not forced down to 120 KiB. Actual file bytes and image decoding are checked, rather than trusting a filename or a content-provider size. JPEG, PNG, WebP and HEIF are supported; unsupported/SVG/executable input is rejected. A 50 MB input-read bound and 100-million-pixel decode guard limit resource use.

The application upload limit is **50,000,000 bytes (50 decimal MB) per file**, shared by images, videos and audio. Keep all three limits aligned: Android integer resource chat_media_max_bytes in app/src/main/res/values/integrations.xml, backend MEDIA_MAX_BYTES, and Supabase bucket/global settings. None may exceed **50,000,000 bytes** on Free. A smaller bucket/project limit can still reject an otherwise valid application file. [Supabase file limits](https://supabase.com/docs/guides/storage/uploads/file-limits)

Confirm the preview to enqueue. A private file reference and metadata are stored in the existing SQLite outbox; image bytes are not base64-encoded into SQLite. Offline items remain queued. The UI reports bytes sent to the backend, then a separate verification/storage phase; the percentage does not claim Supabase has committed. Stop upload cancels the request; a late server commit is still possible, so Retry checks the original message ID. Failed rows show the actual bounded backend/permission/network reason.

The backend refuses to upload/sign if Supabase reports a public bucket. It verifies the Firebase token and conversation membership, checks byte count/signature/checksum, uploads with overwrite disabled to the deterministic conversationId/messageId path, then atomically creates the Firestore message and preview. A retry after an interrupted response reuses that object and message. Before issuing a 60-second signed download URL, the backend checks current membership and the stored message-bound path. No signed URL or secret key is persisted in Firestore. The recipient downloads and checksum-validates the full file into an account-scoped cache. Tap **Open full image** for a device viewer, or **Save file** to export the complete uploaded bytes. The in-chat thumbnail is sampled for memory safety.

Pending files survive app restarts. Successfully sent files move into a bounded 100 MB/account cache; evicted files can be downloaded again. Unsent/failed files are retained for Retry. Standard uploads retry the entire file, not individual byte ranges. Supabase recommends resumable TUS above 6 MB; this implementation uses bounded standard uploads through the local backend. Slow large transfers may require a retry and consume additional bandwidth. Objects left after upload succeeds but message commit/cancellation fails remain private; no remote cleanup/deletion runs automatically. [Upload guidance](https://supabase.com/docs/guides/storage/uploads/standard-uploads)

Video and audio attachments now use the same authenticated private backend, SQLite outbox and stable message IDs as photos. Tap **Choose a video** for MP4 using Android's media picker, or **Choose an audio file** for MP3, M4A or WAV using the system file picker. Review the filename/preview and confirm Send. No microphone or broad gallery/storage permission is requested, and no recorder was added. MP4 video with H.264/AAC and PCM WAV are recommended for device compatibility; playback depends on the receiving device's codecs. Other containers (MOV, WebM, OGG, WMA) are deliberately not supported by this phase.

The receiver taps **Download to play**, sees byte progress, then uses **Play full-screen video** or audio **Play / Pause / seek** controls. Both have **Save file** for the full downloaded bytes. Videos have sampled thumbnails when available, with a labelled playback action when a thumbnail cannot be decoded. Audio shows duration after preparation; downloaded playback uses the file's actual duration. Playback pauses/stops when leaving the player or backgrounding the app; it is not a background audio service. Download/playback errors offer Retry; upload errors retain the durable queued file and stable ID.

Android copies selected content to private files in 64 KiB chunks, checks its actual size/container and uses MediaExtractor for tracks. The backend checks Firebase identity and current membership, spools the bounded body to its own temporary file, verifies SHA-256 and image signatures/MP4 track handlers/WAV chunks/MP3 headers, then streams to Supabase with Content-Length and overwrite disabled. It never buffers a full 50 MB request. Temporary spools are cleaned on success/failure/cancellation; an abrupt process/computer crash can leave a generated temp file. The two-upload concurrency cap bounds temporary disk use to roughly 100 MB. Allow sufficient free disk space. Media calls and backend ingress are bounded to ten minutes, including the private storage step; retry remains whole-file rather than TUS byte resumption.

Location sharing remains a separately confirmed, single GPS/network snapshot. It does not upload location to Supabase or the AI provider. Grant approximate/precise while-in-use permission, review coordinates/accuracy and confirm Send; recipients can open Google Maps. No background tracking is added.

## 4. Live map, without a Maps API key

The user clarified that live location is the desired primary map experience. Open the map icon from Chats, then **Use my location**. Panning/zooming is interactive; **Following** controls recentring as fixes arrive. Location updates are collected only while this screen is resumed. Returning to another screen or backgrounding the app unregisters the listener. If the first fix is slow, check Android location settings, move outdoors or send an emulator test fix. Approximate permission produces a larger accuracy circle.

Turn on **Online OpenStreetMap tiles** after reading the notice. The bundled Leaflet 1.9.4 renderer needs no CDN at runtime. Tile requests identify ChatSphere, use HTTPS and WebView's normal HTTP cache, show attribution, and request only the visible area. There is no download-area, scraping or prefetch feature. OSM sees your IP address and viewed area. Its [public tile service is best-effort](https://operations.osmfoundation.org/policies/tiles/), can block misuse, has no SLA, and is not unlimited hosting. OSM data uses [ODbL/attribution requirements](https://www.openstreetmap.org/copyright); Leaflet uses BSD-2-Clause (`app/src/main/assets/map/LEAFLET-LICENSE.txt`). Use a policy-compliant provider or your own licensed tiles for broader distribution. Never point automated tests at public tiles.

Tap a point on the map to open a popup with **Walking directions in Google Maps**. These [Maps URLs](https://developers.google.com/maps/documentation/urls/guide) need no developer API key; the installed app or browser handles directions. No route geometry or turn-by-turn engine is implemented inside ChatSphere.

Optional saved campus destinations are configured in `app/src/main/assets/map/config.json`. The default list is empty; no real campus was invented. `destinations.example.json` contains explicitly unverified placeholders. Replace their names, latitude and longitude with actual verified points before copying them into `config.json` and setting `verified:true`. Unverified destinations cannot launch the native list's Directions button. Update the `name` field for your campus. `tileUrl` is configurable; check a replacement provider's terms, caching and attribution and update attribution in `map.js` as required. Do not embed private keys in a tile URL.

Native **Google Maps SDK for Android 20.0.0 is now integrated**, alongside the existing Leaflet/OSM fallback. It is enabled only when an ignored local key is supplied. Missing/failed configuration keeps the destination list and OSM map available. Native Google tile display has not been verified because this project has no Maps key configured.

To configure it manually (nothing below has been performed automatically):

1. Review [Maps SDK setup and billing requirements](https://developers.google.com/maps/documentation/android-sdk/start). Google requires a billing-enabled Cloud project for the SDK. Do not enable it if staying strictly without a billing account; OSM and external Google Maps directions remain available. Budget alerts do not cap spending.
2. In the intended Cloud project, enable **Maps SDK for Android**. Create/use an Android client key, restrict application use to `com.jos.myapplication` plus the signing certificate SHA-1, and restrict API use to **Maps SDK for Android**. Obtain local debug SHA-1 with `.\gradlew.bat :app:signingReport`. A separately installed `.demo` build needs its own package restriction if testing native Maps there. See [key restrictions](https://developers.google.com/maps/documentation/android-sdk/get-api-key).
3. Copy `maps.properties.example` to ignored `maps.properties` in the project root, set `MAPS_API_KEY=your_restricted_android_key`, then rebuild. Alternatively set `CHATSPHERE_MAPS_API_KEY` in the Gradle environment. Never put Gemini, Supabase service-role or Firebase Admin keys here. Android Maps client keys are necessarily packaged in the APK; restrictions are essential.
4. Open **Live map → Add destination**. Enter your real campus/place-group name, landmark name and verified latitude/longitude, or explicitly use the current location, and confirm the coordinates. No real campus is bundled. Destinations are saved on this device; existing asset configuration remains a default. Up to 50 validated destinations are supported. Select a destination and use **Edit destination** to correct it without replacing other entries.
5. Select **Route: walking** (or driving) and **Directions**. The supported [Google Maps URL integration](https://developers.google.com/maps/documentation/urls/get-started) opens the Maps app/browser, where routes are computed and alternatives can be chosen. This is not in-app route drawing or a paid Directions API integration. Current coordinates are included only when already available; otherwise Google Maps obtains the origin. **Open in Google Maps** opens the destination pin.
6. Grant foreground location permission when requested. Denied/unavailable location still permits destination browsing and external directions. No background tracking is added. Google loading failure offers OSM; OSM tiles remain opt-in with attribution/caching and no bulk downloads. Tile outages must not hide the destination controls.


## 5. On-device ML

**No account, API key or billing setup.** `com.google.mlkit:smart-reply:17.0.4` includes Google's pretrained model, increasing APK size by about 5.7 MB. It supports English, can decline sensitive/unsupported inputs, and may return no suggestion. See [official Android integration](https://developers.google.com/ml-kit/language/smart-reply/android). The input is at most the latest ten text messages and is processed on-device; suggestions fill the composer and never send automatically. Output cleanup fixes common grammar, rejects malformed/incomplete replies, and removes duplicate/near-equivalent choices. Exactly three choices are shown for a nonempty incoming text trigger, filling missing choices with authored, context-sensitive local fallbacks. The label distinguishes actual ML suggestions, mixed ML/fallback output and fallback-only output; templates are not described as ML. The SDK can send operational metrics or obtain updates; see [ML Kit terms/privacy](https://developers.google.com/ml-kit/terms). Google's model/software terms apply; this is not an open-source model trained by this project.

An additional optional multinomial Naive Bayes classifier fits counts from 18 authored synthetic examples in `MessageCategories.kt`, then computes probabilistic Study/Meetup/Conversation labels locally. Enable it in Profile → Notifications. It abstains on unknown words and low confidence. This is an experimental classroom model, not an urgency detector or evaluated classifier; no accuracy claim is made. The new optional **AI study alerts** setting uses this same trained local classifier: a new incoming text must predict Study with score ≥0.8 and at least two known words, and pass a 30-second cooldown. It creates one generic study notification instead of a second ordinary alert. It does not infer urgency/deadlines or change notification importance. No chat text is sent to an external AI service for this feature.

## 6. Free Gemini API and private Supabase media backend

Current configuration is preserved. Real private Supabase uploads/downloads and a 50,000,000-byte round trip passed Phase 1; the user previously confirmed real Gemini replies. See [the integrated report](FINAL_PHASES_REPORT.md) for current measured verification and remaining manual checks. Do not recreate credentials or projects that are already configured.

### A. Create the Supabase Free project and private bucket yourself

1. Visit [Supabase Dashboard](https://supabase.com/dashboard), sign in/create your account, choose a Free organization and create a project. Keep the database password private. Do not choose Pro, enable add-ons or enter billing for this demo.
2. Open **Storage → New bucket**, name it **chat-media**, and leave **Public bucket off**. Set its file-size limit to **50,000,000 bytes** (or a supported bucket limit at least that large) and allow image/jpeg, image/png, image/webp, image/heic, image/heif, video/mp4, audio/mp4, audio/mpeg and audio/wav. The existing private bucket already accepts these files; do not recreate it. In Storage settings, ensure the global limit permits that size. Free's global ceiling is 50 MB. [Bucket restrictions](https://supabase.com/docs/guides/storage/buckets/creating-buckets)
3. Review backend/supabase-storage-policy.sql. In the project's **SQL Editor**, apply it manually if appropriate. It creates a restrictive policy denying direct anon/authenticated access specifically to chat-media, including when an unrelated broad permissive policy exists. It does not alter policies for other buckets. Do not add public SELECT/INSERT policies for this bucket. No SQL has been run for you.
4. In **Project Settings → API / API Keys**, copy the project URL and reveal the **legacy service_role key** in Legacy API Keys. Put them only in backend/.env below. This privileged server key bypasses RLS; protect it like a password, preferably in a dedicated Supabase project. Never put it in Android, Gradle, a resource, a URL, Git or chat. No Supabase key or new Supabase login is required in the app.
5. Keep the source Firestore rules reviewed and deployed securely before real use. Direct client creation of Supabase metadata is deliberately denied by the rules; only the trusted Firebase Admin backend creates it after validating the upload. Existing client legacy images remain readable/compatible.

Direct client Storage policies cannot inspect authoritative Firestore membership. The backend bridge keeps authorization there and limits its API to authenticated conversation participants and deterministic object paths. Service-role privileges themselves are broad; the backend API supplies the least-privilege boundary. Signed download links are bearer capabilities valid until expiry; do not share them. [Storage access control](https://supabase.com/docs/guides/storage/security/access-control), [private downloads](https://supabase.com/docs/guides/storage/serving/downloads)

Free currently includes 1 GB file storage, 5 GB uncached egress and a separate 5 GB cached-egress allowance, with at most two active Free projects. Projects may pause after a week of inactivity. Inspect organization Usage/Billing and remain on Free; image downloads, retries and backend verification of an existing object consume bandwidth. These are quotas, not an unlimited media service. [Pricing](https://supabase.com/pricing)

### B. Create the free Gemini Developer API key yourself

1. Open [Google AI Studio](https://aistudio.google.com/) with an eligible Google account. Review its terms and country availability. The optional Gemini assistant is for users **18+**; do not make it available to an under-18 audience under these terms.
2. In Dashboard, open **Projects** and select/import an **unbilled** project, then **API Keys → Create API key**. New users may receive a default project/key after accepting terms. New AI Studio keys are authorization keys restricted to the Generative Language API. If key creation is blocked, follow your administrator's permissions process; do not bypass it or activate billing. [Key setup](https://ai.google.dev/gemini-api/docs/api-key)
3. Check AI Studio says **Free** and Google Cloud billing is not linked. Leave billing, paid grounding, tuning, batch and paid-only models unused. Copy the key into ignored backend/.env only.
4. Default model is **gemini-3.1-flash-lite**, currently listed with free input/output under its standard tier. Availability and actual RPM/TPM/RPD are project/account dependent: open AI Studio's rate-limit view instead of assuming a fixed daily allowance. Set GEMINI_DAILY_REQUEST_LIMIT to a conservative value at or below your quota. The backend defaults to 40 provider attempts/UTC day, persisted in its local SQLite database; additionally each Firebase user is limited to 5 fresh requests/minute and 60/day per server process, with two simultaneous generations. Google applies its own project-wide limits and Pacific-time daily reset. [Pricing](https://ai.google.dev/gemini-api/docs/pricing), [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)
5. Review free-tier data use: Google may use prompts/responses for product/model improvement and human review. Do not enter confidential, sensitive or personal material. EEA/UK/Switzerland have the documented regional exception to unpaid-service treatment. The app requires an 18+/non-sensitive-study acknowledgement. Only this assistant conversation's bounded text is sent to Google; Firebase tokens, credentials, user IDs and human chats are not attached. [Terms](https://ai.google.dev/gemini-api/terms), [regions](https://ai.google.dev/gemini-api/docs/available-regions)

### C. Configure and start the existing local backend

Install Node.js 24+ if needed. From the Android Studio project's Terminal:

    cd backend
    npm ci
    Copy-Item .env.example .env

Run the copy only when .env does not exist; edit an existing .env instead of overwriting it. In your existing Firebase Console, obtain an approved Admin service-account credential via Project settings → Service accounts → Firebase Admin SDK, or use an approved application-default-credentials workflow. Keep the private JSON outside the repository, for example C:/private/chatsphere/service-account.json. No billing upgrade is needed for this code. Give the backend only the Firebase Auth verification and Firestore access it requires; FCM permission is needed only for the separate push worker.

Fill these fields locally in ignored backend/.env:

    FIREBASE_PROJECT_ID=your-existing-Firebase-project-id
    GOOGLE_APPLICATION_CREDENTIALS=C:/private/chatsphere/service-account.json
    HOST=127.0.0.1
    PORT=8787
    STATE_DATABASE=./runtime/state.sqlite3
    AI_PROVIDER=gemini
    GEMINI_API_KEY=your-private-key
    GEMINI_MODEL=gemini-3.1-flash-lite
    GEMINI_FREE_TIER_CONFIRMED=true
    GEMINI_DAILY_REQUEST_LIMIT=40
    SUPABASE_URL=https://your-project-ref.supabase.co
    SUPABASE_SERVICE_ROLE_KEY=your-private-legacy-service-role-key
    SUPABASE_MEDIA_BUCKET=chat-media
    MEDIA_MAX_BYTES=50000000

Set confirmation true only after reviewing eligibility, data terms and the unbilled Free project. The program does not inspect or change your account's billing tier. Provider free eligibility must be maintained in the dashboard. API keys, Admin JSON, tokens and signed URLs must never be pasted into documentation/logs or committed.

The local backend stores idempotent assistant replies for 24 hours, push receipts for seven days, and a persistent daily provider counter in SQLite; protect the computer account and local database. It logs no prompts, tokens, signed links or private keys.

Run **npm start**. In another Terminal, check **Invoke-RestMethod http://127.0.0.1:8787/health**. It reports configured components; it does not perform model generation or a storage round trip. Run **npm run test:live:gemini** from backend to send a harmless real 2+2 provider check only when credentials/confirmation exist. A reported SKIPPED result means no provider request occurred. Validate the assistant end to end separately in Android after that succeeds.

### D. Connect Android without putting keys in the APK

1. Rebuild/install the debug app from this project.
2. For an emulator, use **http://10.0.2.2:8787**. For a USB phone, run the SDK platform-tools command **adb reverse tcp:8787 tcp:8787** (use -s SERIAL when multiple devices are attached), keep USB connected and use **http://localhost:8787**.
3. In ChatSphere, open **Student AI assistant → Connect backend**, enter that base URL (no /v1/chat suffix), and Save. Only a trusted server should receive Firebase authorization tokens. This public connection setting also serves media uploads/download authorization; it is saved locally, not as a credential in source.
4. On Android 17, tap **Allow local AI connection** and grant Nearby devices/local-network permission. This permission also covers uploads to the local computer. Ordinary Firebase internet chat does not require it. A user configuring media alone can set the connection/grant this permission without enabling Gemini consent.
5. For AI, acknowledge eligible age/non-sensitive study use, ask a simple question and observe a genuine answer. Missing key, unavailable model, upstream quota, timeout or backend failure produces a specific retryable error. Never treat mocked tests or a loading spinner as a working chatbot.
6. For media, send from account A to B. Check the private object in Supabase and only metadata in Firestore. On B, open/save the full file, compare size/checksum, and test signed-link expiry, offline queue and Retry.

Debug HTTP is restricted to emulator/loopback hosts; release connections require HTTPS. The backend binds to loopback and is not deployed publicly. It and the computer must stay running for AI/new media authorization. Two USB phones each need their own reverse mapping. The supplied Node/SQLite backend is not a drop-in deployment for Deno/ephemeral free functions. A public service would need separately secured HTTPS hosting, persistent quota/cache state and protected secrets; no account, tunnel, hosting plan or deployment was created. The local route remains the supplied free development option.

Ollama is retained as an optional fully local alternative: set AI_PROVIDER=ollama, install Ollama and the selected qwen2.5:0.5b model, and keep OLLAMA_URL on loopback. It does not require a Gemini key. See the previous local model's official [runtime](https://ollama.com/download) and [model](https://ollama.com/library/qwen2.5:0.5b) instructions. Real Ollama inference is not part of the current cloud-provider tests.

## 7. Notifications, authenticated delivery and AI study alerts

[FCM requires a trusted sender](https://firebase.google.com/docs/cloud-messaging/server-environment). The existing local Node backend supplies it with Firebase Admin; no Cloud Functions deployment or Admin credential in Android is needed.

1. Keep the configured local backend running with `cd backend` then `npm start`. Android must use its base URL as described above. Existing text/image/location/video/audio sends enqueue a separate durable, authenticated `/v1/push/message` request **after** the message is saved. The server re-reads that message and conversation, checks the authenticated UID is its sender, derives the recipient, and sends IDs only. There is no caller-controlled recipient/body/token. This route does **not** require a collection-group index and never marks an already-sent chat message failed when push fails.
2. Android retries temporary dispatch failures with bounded WorkManager backoff; server device sends also have bounded transient retries. Persistent per-device receipts and Android account/message deduplication suppress repeats. A rare transport ambiguity can still deliver FCM more than once; Android handles those repeats. The backend/computer must be available. FCM acceptance is not proof of a displayed notification.
3. Optionally run `npm run push` in another backend terminal to watch messages from older clients as well. This legacy-compatible observer needs the `messages.serverTimestamp` collection-group index; the read-only probe found it **missing** in the current project. Review/deploy the existing index manually only if using this observer. Nothing was deployed. It observes at most the latest 200 new messages after worker startup, with a bounded queue; it does not backfill the time it was off. Do not run multiple workers against separate receipt databases.
4. On each phone, Profile → Notifications → **Allow notifications**. Grant Android 13+ permission and enable Message alerts. Android channel/user/battery settings may suppress display. Token rotation writes the new owner-only record and removes that device's old hash; sign-out attempts unregister before switching accounts. Stale tokens are ignored and invalid tokens removed by the sender.
5. Press **Send test push to my devices** after connecting the backend. It targets only the authenticated account's registered devices, with a stable retry ID and a server record expiring in one hour. Tap opens notification settings. New rules allow reading only your own test records and forbid client writes. With older deployed rules, the authenticated backend validates that same record instead; rules are not weakened. A test accepted by FCM still needs actual device display verification.
6. To demonstrate real ML-based alerts, enable **AI study alerts** and send an incoming study message from a second account, for example “Please submit the homework assignment before class deadline.” The local experimental Naive Bayes classifier makes the Study decision described in section 5. Wait 30 seconds between separate classifier alerts. Ordinary/uncertain messages retain generic notifications. The active chat suppresses its own alerts. Repeated delivery of one message must not create another alert.
7. Message taps revalidate the currently signed-in recipient, membership and sender before opening the corresponding chat. Other-account and stale payloads fail safely. FCM contains IDs only, and notification text is generic. Foreground observers remain supported when no sender is reachable. App background/closed delivery is subject to Android/FCM limitations; **force-stop** blocks delivery until the app is reopened. This is not an always-on hosted service.

Check **Firebase Console → Project settings → Cloud Messaging** for the existing FCM API (V1) and the current Admin credential's permission if actual sending fails. No API, paid plan or key was activated/replaced here. Real device results and emulator results are separated in [the integrated report](FINAL_PHASES_REPORT.md).

## Cost and account table

| Component | Cost class | Account / key | Limits and action |
|---|---|---|---|
| Android Studio, Kotlin, Compose, AndroidX SQLite/WorkManager | Free local development libraries/tools | None for local build | Device, disk and downloads required; AndroidX uses Apache 2.0 |
| Existing Firebase email/password Auth | No-cost usage subject to quotas | Existing Firebase project/client config | Enable Email/Password; avoid Phone/SMS; [limits](https://firebase.google.com/docs/auth/limits) |
| Existing Cloud Firestore on Spark | Free within quotas | Existing Firebase account | Watch reads/writes/data/transfer in Console; message metadata and sender use quotas |
| FCM | No-cost messaging service | Existing project and private server Admin credential | No guaranteed delivery; local sender must stay running |
| Leaflet 1.9.4 | Free, bundled, open source | None | BSD-2-Clause; bundled license |
| OSM data / public raster tile server | Open data; best-effort community hosting | No API account/key for this demo | Attribution, HTTP caching, visible viewport only, no bulk downloads/SLA |
| ML Kit Smart Reply 17.0.4 | No paid API integration; on-device SDK | No API key | Google SDK terms, English/confidence/content limitations and operational metrics |
| Authored Naive Bayes category model | Local self-contained code/inference | None | Synthetic small corpus, optional, unbenchmarked |
| Ollama + Qwen2.5 0.5B | Local inference, no hosted API fee | No AI API account/key | Computer resources and roughly 398 MB weights; MIT runtime / Apache 2.0 selected model |
| Node.js 24 + Firebase Admin SDK | Free local runtime/SDK | Existing-project Admin credential for real services | Node license notices; Admin SDK Apache 2.0; Firestore operations consume quota |
| Firebase emulator/test tooling | Local development, no cloud billing | None for `demo-chatsphere` tests | Node 24 and Java 21+; tools/dependencies locked; production is never the test target |
| Gemini Developer API | Free-tier quota for the configured standard model | Google/AI Studio account and backend-only private key | Eligibility, data-use terms and account-specific limits; no billing activated |
| Supabase private Storage | Free within storage/egress limits | Supabase account and backend-only service_role key | 1 GB storage, 5 GB uncached and separate 5 GB cached egress, 50 MB ceiling; monitor quotas |
| Google Maps SDK | Optional native integration | Restricted Android key + billing-enabled Maps project required | No API/key/billing activated; OSM fallback works without it |
| Firebase Storage, Cloud Functions | Not used | None added | No deployment or billing activation |

The Firebase [pricing plan documentation](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans) distinguishes Spark and Blaze. [Firebase Storage billing requirements](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024) apply from February 2026; do not assume a Spark bucket is available. No cost promise overrides a provider's current terms. Check official links again before changing plans or providers.

## Phase 1: 50 MB storage/dashboard alignment and verification

The Android resource `chat_media_max_bytes`, `MediaPolicy.DEFAULT_MAX_BYTES`, backend default and `.env.example` are now **50000000**. The existing local ignored `.env` has only `MEDIA_MAX_BYTES` changed to **50000000**; existing credentials/model/quota settings are preserved. Restart your own `npm start` process after updating the backend; a running process does not reload source or environment values.

A read-only check of the configured bucket found **private=true**, **file_size_limit=52428800** and no MIME restriction. That ceiling is larger than the app's exact 50,000,000-byte limit, so it already permits the intended upload. The real 50,000,000-byte Supabase round trip passed; the application/backend still reject 50,000,001. No dashboard settings were changed. The project/global limit also allowed this successful round trip, although its dashboard value was not separately edited/read.

To make the bucket's own ceiling exactly 50,000,000 rather than 50 MiB, open your existing **Supabase project > SQL Editor** and review/run the following yourself, replacing the bucket ID with your existing `SUPABASE_MEDIA_BUCKET` value. This is a manual configuration change; Codex has not run it:

```sql
select id, public, file_size_limit, allowed_mime_types
from storage.buckets where id = 'YOUR_EXISTING_MEDIA_BUCKET_ID';

update storage.buckets
set file_size_limit = 50000000
where id = 'YOUR_EXISTING_MEDIA_BUCKET_ID' and public = false;

select id, public, file_size_limit
from storage.buckets where id = 'YOUR_EXISTING_MEDIA_BUCKET_ID';
```

In **Storage > Configuration/Settings**, ensure the global file-size limit permits at least 50,000,000 bytes. Leave the bucket private. If you choose to add bucket MIME restrictions, permit `image/jpeg`, `image/png`, `image/webp`, `image/heic`, `image/heif`, `video/mp4`, `audio/mp4`, `audio/mpeg`, `audio/wav`; inspect other legitimate uses of that bucket before narrowing it. No bucket/policy/credentials were recreated. [Supabase limits](https://supabase.com/docs/guides/storage/uploads/file-limits)

The supported development path is the existing loopback Node backend over USB `adb reverse tcp:8787 tcp:8787` or emulator `10.0.2.2:8787`; no hosting proxy is in that tested path. No hosting was added or changed. A separately managed HTTPS proxy must permit bodies **at least 50,000,000 bytes** and allow the ten-minute media request; otherwise it can reject a valid file before Node sees it. Standard uploads are supported, but Supabase recommends TUS for transfers above 6 MB; this phase retains safe, whole-file retries with checksum/idempotency checks. [Supabase upload guidance](https://supabase.com/docs/guides/storage/uploads/standard-uploads)

See [Phase 1 report](PHASE1_MEDIA_REPORT.md) for exact automated/live outcomes and [testing](TESTING.md) for the two-phone checklist. That report records Phase 1 only. Phases 2–5 are described in [the integrated report](FINAL_PHASES_REPORT.md).

## Gemini reliability and safe retries

The configured provider/model/key and quota values are unchanged. The Gemini adapter distinguishes configuration/authentication/model failures, exhausted daily quota, temporary rate limits, provider outages, network failures and timeouts. It allows at most three provider attempts within a 65-second budget, with exponential backoff/jitter and `Retry-After`/RetryInfo. Long retry delays are returned to the app rather than shortened. Permanent failures and exhausted daily quotas are not retried automatically. Every actual attempt reserves the existing persistent daily counter. The Android assistant uses an 80-second read/90-second call timeout and only one additional retry for connection/busy conditions, reusing the same request ID; it does not multiply the provider's retry sequence. JSON body reads run off the UI thread. “Stop waiting” retains the user message/request ID for an explicit retry. No successful answer is shown when the provider failed. Backend logs/errors redact private upstream details.

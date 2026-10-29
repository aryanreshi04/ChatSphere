# ChatSphere 2.0

An extension of the existing Kotlin / Jetpack Compose Android app, retaining Firebase Authentication, Firestore, the purple chat UI, profiles and per-user hidden conversations.

Work is on **chatsphere2.0**, explicitly selected by the user. No changes have been pushed or deployed.

New functionality includes a durable SQLite outbox, offline indicators and caching, real-time text/photo/location messages, a live location map with optional OpenStreetMap tiles, bundled on-device Smart Reply, optional experimental message categories, notification permissions/channels and authenticated notification routing, and a separate AI assistant with a backend-only Gemini free-tier provider and optional local Ollama.

Photos, MP4 videos and MP3/M4A/WAV audio use private Supabase Storage through the existing authenticated backend, with a consistent **50,000,000-byte application/backend limit**. Photos retain Original/Balanced quality and legacy compatibility. New video/audio picker, download, playback and save actions reuse the durable outbox and stable retry IDs. Keys remain on the backend computer.

See the [integrated phases report](docs/FINAL_PHASES_REPORT.md), [setup](docs/SETUP.md) and [testing](docs/TESTING.md) for measured verification and remaining device/dashboard checks. The existing Smart Reply now cleans outputs and fills exactly three varied choices with labelled local fallbacks. Optional native Google Maps and saved, user-confirmed campus destinations extend the existing OSM map; native tiles need a restricted local Maps key and Google billing setup. Authenticated post-send FCM, self-test pushes, and optional on-device ML Study alerts extend the existing notification flow. Gemini uses bounded typed retries and the existing model/credentials/quota. Historical media reports remain linked from the integrated report.

```powershell
# From the repository root, after Android Studio configures local.properties:
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

The normal APK is `app/build/outputs/apk/debug/app-debug.apk`. If OneDrive turns generated files into cloud placeholders, use the optional `-PchatsphereBuildRoot=C:/path/outside/OneDrive/chatsphere-build` argument. If source files are affected too, use `tools/Prepare-LocalBuild.ps1` as documented in SETUP.md to make a byte-verified build copy outside OneDrive. Keep the original repository as the editing/review source.

Firebase remains authoritative for shared profiles, conversations and messages. SQLite is an account-scoped cache and durable outbox. No Firebase Storage, paid AI API, Google Maps SDK billing account, Cloud Functions deployment, or new cloud hosting service is required by the default implementation.

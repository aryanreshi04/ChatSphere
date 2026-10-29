# ChatSphere 2.0 implementation audit and plan

Audit date: 25 October 2026 (Asia/Calcutta).

## Starting point

- Remote: `https://github.com/aryanreshi04/ChatSphere.git`.
- Starting commit: `869333a61895b316be7e27da367a84a86d34978c`.
- Workspace was an empty, uncommitted `main` with no remote or application files. No user edits existed to overwrite.
- The requested `chatsphere-2.0` does not exist remotely. The user explicitly selected the existing `chatsphere2.0` branch; it was fetched and checked out locally. Nothing is pushed.
- No AGENTS.md found in the repository or checked ancestor directories.

## Verified architecture

Single Android app, package `com.jos.myapplication`, Kotlin/Compose, Navigation Compose, Firebase email/password Auth and Cloud Firestore. SDK 37, minimum 28; AGP 9.4.1, Gradle 9.6.0, daemon Java 25. Existing build plugins resolved. Initial build stopped at missing SDK location. Installed SDK located at `%LOCALAPPDATA%/Android/Sdk`; configure ignored local.properties and rerun before feature changes.

Profiles: `users/{uid}` with name/email; profile photos are local URI preferences, not cloud uploads. Home uses registered profiles and participant-filtered conversations as contacts; no separate persisted saved-contacts collection exists. Keep that behavior.

Conversations: `conversations/{sorted_uid_pair_joined_by_underscore}` with participants, lastMessage, lastMessageTime (client epoch milliseconds), lastMessageId. Messages: `messages/{autoId}` with senderId, text, timestamp. Creation transaction preserves history. Sending uses an atomic batch but allocates a new ID on every attempt and has no in-flight guard. Listener orders all messages by client timestamp. No read receipts or presence service exists despite the conversation UI displaying Online/read ticks.

Hidden chats: `users/{uid}/hiddenChats/{conversationId}` stores hiddenMessageId. A new lastMessageId makes a hidden conversation visible again. Preserve this per-user design.

SQLite: ChatSphere.db version 1 contains only users(id, name, email); registration writes on the UI thread. onUpgrade currently drops the users table. Replace with additive migrations, preserve legacy users, add account-scoped cache and durable outbox; Firebase remains authoritative for shared data.

No Firestore rules, indexes, backend, maps, ML, or FCM service checked in. Notifications screen only stores preferences. Most attachment controls are placeholders. No existing integration tests beyond generated examples. Auth, messages and profile flows are implemented but have not yet been verified against two real accounts.

## Phases and verification

1. Establish baseline build using installed SDK; retain existing build versions unless evidence requires changes.
2. Connectivity, additive SQLite cache/outbox, stable message IDs, honest pending/cache/failure states, bounded message history and retry. Preserve original auth/profile/home style.
3. Configurable campus map screen and destination selection, explicit example labeling, voluntary one-shot location, external Google Maps directions. Mapping-provider decision awaits user response; billing is never enabled.
4. System photo picker, sampled JPEG compression and strict size/type validation, image/location message rendering, explicit sharing confirmation. Recommended free-first media path: small compressed images in a separate participant-protected Firestore media subcollection, not Firebase Storage. Clearly document Firestore storage/read/transfer quotas and exclude image bodies from indexes.
5. Separate student assistant UI with unavailable-service states; optional local Ollama backend only if approved. Real bundled ML Kit Smart Reply inference on-device, English limitations, no private chat uploads.
6. Notification permission/channel/preferences/deep-link validation and FCM client. Trusted local sender only if approved; no Blaze functions or privileged client credentials. Clearly label closed-app push unverified until server/device tests.
7. Participant-only rules and emulator tests, backend validation tests where applicable, migration tests, Android build/unit/lint checks, setup/cost/privacy/device acceptance documentation.

Build after each coherent phase and repair compilation before continuing. Credential/device-dependent checks must be recorded as untested, never inferred from compilation. Do not deploy rules, activate services, enable billing, push or merge.

## Free-first decisions

Existing Spark Auth/Firestore retained. Firestore is quota limited, not unlimited: 1 GiB data, 50,000 reads/day, 20,000 writes/day, 20,000 deletes/day, 10 GiB outbound/month in the documented free tier. Rule reads/listeners also consume quota. Firebase Storage and Google Maps SDK require billing; excluded from the default implementation. FCM delivery is no-cost but needs a trusted sender. Firebase Functions deployment requires Blaze; excluded. ML Kit Smart Reply runs on-device and uses Google's SDK terms, not an open-source model license.

Official sources and exact setup/test steps will be consolidated in SETUP.md and TESTING.md with the final implementation status.

## Implementation decisions and phase evidence

- User selected `chatsphere2.0`, optional OpenStreetMap tiles, and the optional local Ollama/FCM backend. They clarified that live location is the main map use case; campus examples remain optional configuration rather than invented real destinations.
- Baseline debug APK and original unit test passed after locating the SDK. Connectivity/cache, map/auth/navigation, and multimedia/ML phases each compiled and passed unit tests. Later debug/release compilation and lint passed after fixing the reported source/configuration issues.
- OneDrive generated-file placeholders required an optional external build-output property. The first cold-booted emulator disconnected during APK installation. The connected-test engine also produced a misleading zero-test success; direct AndroidJUnitRunner output is used as the acceptance evidence instead.
- Actual Android 17 testing exposed mandatory local-network permission for SDK 37. Added explicit, feature-scoped permission flows for local AI and the separate emulator app; internet chat/maps do not prompt for LAN access.
- On-device Smart Reply inference, SQLite migration, bundled map initialization and missing-backend UI were observed passing. The local-network permission fix restored emulator Auth and text/photo/location exchange. The final outage test uses actual device transport changes because Firestore's disableNetwork switch did not prevent transaction RPCs in the tested SDK.
- Ten Firestore rules tests and four backend contract/security tests passed. Backend and development-tool dependency audits returned zero known vulnerabilities after compatible transitive dependency overrides.
- Final normal debug/release builds, 5 JVM tests, lint and all 7 direct Android instrumentation tests passed. The two-account device test includes actual transport loss/recovery, queue retention, safe retries, forbidden-destination failure and incoming listener recovery. Final counts, artifact paths, manual configuration and remaining unverified features are recorded in DELIVERY_REPORT.md. No rules, indexes, accounts, billing, paid plans, pushes or merges were changed remotely.

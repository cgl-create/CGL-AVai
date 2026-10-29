# CGL+ AVai — product and integration architecture

CGL+ AVai is the umbrella project for two Android apps:

- **AVai** — AI workspace at AVai/. It owns provider API-key profiles, cloud and local model execution, chat history, memory notes, and companion authorization.
- **Friend** — conversation companion at Friend/ with the eyes-and-mouth animated face, voice input/output, local chat management, and wellbeing-oriented system instructions.
- The original web/PWA app remains at the repository root.

## Implemented in the current source branch

- Native Kotlin + Jetpack Compose apps with separate build targets and a combined workflow.
- AVai cloud providers: Gemini, OpenAI Responses API and Anthropic Messages API. Model IDs are editable; the defaults are maintained as best-effort current model names.
- Named API-key profiles in AVai, with active-profile selection. Provider secrets are encrypted locally using Android Keystore-backed AES-GCM storage and never travel to Friend.
- Chat list/history in AVai and Friend, including separate sessions, open, new and delete. Provider/model choices and backend mode are saved per Friend conversation.
- Friend defaults to AVai mode. Requests carry only bounded prompt/context/history/tone and an optional provider/model override; AVai looks up its own key, makes the provider call, and returns the answer over a Messenger reply.
- AVai stores mirrored Friend conversations in its encrypted local chat store.
- Consent UI: Allow once, Always allow and Deny. A one-time grant expires after five minutes and is consumed by the next valid request. Persistent grants can be revoked in AVai settings.
- The exported AVai Messenger service is guarded by a signature-level Android permission, verifies the caller UID/package and shared signing certificate, and checks consent before inference. Both apps must be installed from builds sharing a signing certificate. Prefer the combined CI artifact pair.
- Offline model import and inference using Google's LiteRT-LM Android runtime: the user selects a .litertlm file, AVai copies it to app-private storage, computes a SHA-256 fingerprint, checks file/storage constraints, invokes the on-device runtime, and can remove the imported model. The local fingerprint is not a trusted-publisher authenticity check; model license and download source must be checked by the user.
- Firebase email/password and Google sign-in UI, disabled with an explicit missing-configuration message unless each app has its matching real Firebase configuration.

## Important unimplemented or not verified end-to-end

- Real Firebase sign-in against CGL+ Accounts remains unverified until the real google-services.json files are installed for both packages, email/password and Google providers are configured for both app registrations, and the actual signing certificate's SHA-1/SHA-256 fingerprints are registered. See FIREBASE_SETUP.md.
- No Firestore chat, memory or API-key sync is implemented. Authentication is identity-only; private conversation data and keys do not upload just because a user signs in.
- No automatic model downloader/catalogue with official checksums or license verification is implemented. The model importer calculates a local SHA-256, which detects later file changes only if a trusted expected digest is available; it does not authenticate a file independently.
- No Play Store release signing, production security review, or real-device/system integration testing has been completed. CI compilation does not prove all runtime paths work on every phone.
- The apps do not yet have cross-device chat sync, shared cloud memory, universal model compatibility or universal model performance guarantees.

## Data and security boundaries

AVai is the source of truth for provider configurations and API keys. Friend receives a narrow capability to request inference and never receives provider credentials. The bridge payload must never include API keys, Firebase ID tokens, or secrets. Requests must stay bounded, caller identity must be verified, and consent must be checked per request.

API keys, local chat history and memory use app-private encrypted storage. This is defense in depth, not a promise against rooted/compromised devices. Provider requests leave the device and go to the selected provider; local inference is performed on-device with the imported model.

Firebase Authentication provides account identity. It must not be treated as permission to synchronize chats, memory or secrets. Any future sync must be opt-in, disclosed, governed by Firebase Security Rules, and must never upload API keys to Firestore.

## Firebase prerequisites

The existing Firebase project is cgl-accounts. Register Android apps with:
- AVai: com.cgl.avai
- Friend: com.cgl.friend

Install the matching google-services.json in each app module and configure OAuth SHA fingerprints for the cert used by installed builds. Do not invent Firebase configuration or commit signing keys/service-account secrets. Build workflows accept optional configuration secrets as documented in FIREBASE_SETUP.md.

## Model/device requirements

LiteRT-LM requires a supported .litertlm model. Imported model size is not its runtime memory requirement. The available-RAM check is a warning, not a compatibility guarantee. Test each model on the target device and display model license/source, runtime constraints, storage size and performance/thermal caveats before recommending broad use.

## Verification and release

The combined workflow builds AVai and Friend in one runner, producing AVai-debug-apk and Friend-debug-apk with the same signing certificate. Install these two artifacts from the same successful combined run to test the signature-protected bridge. Separate workflow runs may have different debug certificates unless the stable debug keystore secret is configured. Debug APKs are for testing, not Play Store releases.

Do not label a feature fully complete until its behavior is tested on devices. In particular, Firebase requires real config, and the bridge/offline model require real hardware validation before public release.

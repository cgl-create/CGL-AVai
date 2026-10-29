# CGL+ AVai

**CGL+ AVai** is a two-app Android project: **AVai** is the AI workspace; **Friend** is the face-and-voice companion.

## AVai (AVai/)

- Native Kotlin + Jetpack Compose chat workspace.
- Gemini, OpenAI API, Anthropic Claude, and an on-device LiteRT-LM provider.
- Named API-key profiles per cloud provider. Keys are encrypted in app-private storage with Android Keystore; no API key is sent to Friend.
- Model ID selection and saved chat sessions with open/delete, including provider/model choices per conversation.
- Local user memory notes and encrypted local conversation history.
- Import and keep up to 20 compatible .litertlm models in app-private storage, switch between them, calculate local SHA-256 fingerprints, check available storage/RAM, run a selected model on-device, and delete models when no longer needed.
- Consent UI and revocable permission state for Friend's inference requests.
- CGL+ Accounts email/password and Google sign-in UI, ready for the real Firebase app configuration.

## Friend (Friend/)

- Animated face drawn with **only two eyes and a mouth**.
- Voice input using Android speech recognition and text-to-speech replies.
- Supportive conversation UI, editable local memory, and imported ChatGPT conversation excerpts.
- Multiple saved chat sessions, local history, new/open/delete chat actions.
- Uses AVai by default. You can select a cloud provider/model per conversation in Friend settings; AVai executes the request and keeps provider keys on its side. Friend can also be switched to direct mode with keys stored in Friend.
- CGL+ Accounts sign-in UI.

## Friend ↔ AVai security boundary

The app-to-app bridge uses an explicit Android Messenger service protected by a signature-level permission. AVai verifies that the calling UID includes com.cgl.friend and that both installed apps share a signing certificate, then checks the user's **Allow once / Always allow / Deny** grant before running inference. One-time access expires after five minutes and is consumed on the next valid request; persistent access can be revoked from AVai settings.

Only message/context fields travel through the bridge. **API keys, Firebase ID tokens, and provider secrets never travel from AVai to Friend.** A successful Friend conversation is mirrored to AVai's encrypted local chat store so it can appear in AVai's chat history. The apps must be installed from the same signed build for the signature-protected bridge to work.

## Offline models

AVai uses Google's [LiteRT-LM Android runtime](https://ai.google.dev/edge/litert-lm/android). Import a model file ending in .litertlm from a trusted publisher and read its license and device requirements first. AVai records a local SHA-256 fingerprint; the fingerprint is **not** a trusted-publisher signature or proof that a downloaded file is authentic. Model file size is not the same as runtime RAM usage, and a model may be too large or slow for a particular phone. Test any model on the target device. Models are user-imported and are not bundled into the APK.

## Firebase / CGL+ Accounts setup still required

The code cannot guess private Firebase app configuration. Register these two Android app IDs in the existing cgl-accounts Firebase project:

- AVai: com.cgl.avai
- Friend: com.cgl.friend

Add the matching google-services.json for each app and register the SHA fingerprints of the signing certificate used for Google sign-in. See [FIREBASE_SETUP.md](./FIREBASE_SETUP.md). Until real app configuration and valid OAuth signing fingerprints are installed, the sign-in UI deliberately reports that setup is missing. Firebase Authentication is currently for identity only; chats/memory/API keys are **not** synced to Firestore.

## Build APKs

GitHub Actions provides:
- [Combined build — both APKs on the same runner](./.github/workflows/android-apps-build.yml)
- [AVai APK workflow](./.github/workflows/avai-android-build.yml)
- [Friend APK workflow](./.github/workflows/friend-android-build.yml)

Use the artifacts from the **same successful combined workflow run**: AVai-debug-apk and Friend-debug-apk. Install **AVai first, then Friend**, and use the same-run pair because the signature-level bridge requires both APKs to be signed by the same certificate. If you use separate workflow runs, configure the stable debug keystore secret described in FIREBASE_SETUP.md. These are debug builds, not Play Store release builds.

## Developer setup

- JDK 17 and Android SDK Platform 36.
- Open AVai/ or Friend/ in Android Studio and run assembleDebug.
- Optional Firebase setup is provided by the real per-app configuration files, or by the documented GitHub Actions secrets.
- The combined workflow compiles both apps. Build success does not replace testing on a physical device.

## Known remaining release work

- Add the real Firebase app config and verify email/password + Google sign-in against the actual CGL+ Accounts project.
- Device-test the signature-protected bridge, consent revocation, offline model loading, speech permissions, and model performance.
- Production signing, privacy/accessibility review, model-download source/license checks, and signed release packaging.
- This project does not implement Firestore cloud sync, automatic model downloading, automatic cross-device memory sync, or Play Store release signing yet.

Friend is an AI companion, not a therapist or emergency service. AI responses can be inaccurate. User data should remain user-controlled; never store API keys in Firestore or commit API keys, signing keys, or Firebase service-account credentials.

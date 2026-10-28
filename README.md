# CGL+ AVai

**CGL+ AVai** is the umbrella project for two Android apps: **AVai** and **Friend**.

- **AVai** is the AI workspace: multi-provider API connections, editable model IDs, normal AI chat, local encrypted chat history, and user-controlled memory.
- **Friend** is the companion app with the animated eyes-and-mouth face, voice input/output, wellbeing-oriented chat, and a preview entry point for requesting AVai access.
- The existing AVai web/PWA experience remains at the repository root while native development continues.

## Native apps

### AVai — `AVai/`
Native Kotlin + Jetpack Compose app. Includes a chat workspace, model selection for Gemini/OpenAI API/Anthropic, local encrypted API-key storage via Android Keystore, saved conversation history, editable memory notes, and a consent-screen prototype for Friend.

### Friend — `Friend/`
Native Kotlin + Jetpack Compose app. Includes the eyes-and-mouth avatar, Android speech recognition and text-to-speech, wellbeing-focused conversation UI, local encrypted storage, memory notes and chat import.

## Build APKs

GitHub Actions has separate workflows:

- [Build AVai APK](./.github/workflows/avai-android-build.yml) — artifact name `AVai-debug-apk`.
- [Build Friend APK](./.github/workflows/friend-android-build.yml) — artifact name `Friend-debug-apk`.

Open the repository's **Actions** tab, select a successful workflow run, download its artifact ZIP, extract it, and install the included `app-debug.apk` on an Android device. These are debug builds, not Play Store release builds.

## Important implementation status

This is an active prototype, not yet the complete two-app product. AVai and Friend currently still have their own AI request paths. The Friend-to-AVai button opens AVai's consent-screen prototype, but **the secure inter-app inference bridge is not active yet**; the consent screen explicitly says so and does not transfer API keys. Firebase/CGL+ Accounts sign-in, cloud sync, local/offline LLM downloads, shared cross-app chat history, and production permission persistence also remain to be implemented and tested.

Firebase configuration files and OAuth signing fingerprints must come from the real Firebase project. Never commit API keys, `google-services.json` credentials belonging to a private app config, signing keys, or secrets to a public repository. API keys should remain on-device and must never be stored in Firestore or passed through deep-link query parameters.

## AI provider notes

AVai uses provider API keys supplied by the user. An OpenAI API key is billed separately from a ChatGPT subscription. Model IDs are editable because model availability and account access can vary by provider and date. Verify the selected model and your provider's billing/quota before use.

## Safety and privacy

Friend is an AI companion, not a therapist or emergency service. AI outputs can be inaccurate. Users should control whether messages are sent to cloud providers and whether history/memory is retained. AVai and Friend currently keep local app data encrypted using Android Keystore-backed AES-GCM; this is defense in depth, not a guarantee against a rooted or otherwise compromised device.

## Developer setup

Open `AVai/` or `Friend/` in Android Studio with JDK 17 and Android SDK Platform 36. The corresponding GitHub Actions workflows build each debug APK.

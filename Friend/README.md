# Friend by CGL+ — Android app

**Friend** is the native Kotlin + Jetpack Compose companion app in `Friend/`. It is the voice-and-face interface for CGL+ AI; AVai owns inference by default.

## Features
- Animated digital face drawn with **only two eyes and a mouth**.
- Android speech recognition and text-to-speech.
- Local chat history, new/open/delete conversations and editable memory notes.
- Import selected JSON conversation exports or plain-text transcripts.
- CGL+ dark UI with yellow `#f2c94c` accents.
- Uses AVai by default, sharing its selected model/provider without giving Friend AVai's API keys.
- Optional direct provider mode remains available in Friend settings.

## CGL+ AI architecture

Friend uses the same CGL+ AI orchestration design as AVai: identity and policy, input understanding, relevant knowledge/context retrieval, user-controlled memory, reasoning orchestration and replaceable language generation.

This is inspired by AHi1's separation of identity, knowledge, memory and communication. It does **not** copy AHi1's four-region neural simulation. The custom orchestration is not itself a trained language model. By default, AVai runs an imported LiteRT-LM model or the user's selected provider connection.

## Chromebook / ChromeOS support

Friend is configured for resizable ChromeOS app windows and does not require a touchscreen or microphone. Its content width adapts to wide Chromebook windows, and keyboard shortcuts are available: **Ctrl+Enter** sends the current message and **Ctrl+N** starts a new chat. Typing remains available when microphone hardware or speech recognition is unavailable.

The Chromebook must support Android apps / Google Play. When using AVai's local LiteRT-LM engine, compatible CPU architecture and model/runtime binaries are required. Use a cloud provider in AVai if a local model cannot run on that Chromebook.

## No accounts or sign-in

Friend does not use Firebase Authentication, Google sign-in, email/password sign-in, registration or CGL+ Accounts. The app opens directly. Chats, memory and imported excerpts are stored locally.

## Build

Open the `Friend` folder in Android Studio, use JDK 17 and Android SDK Platform 36, sync Gradle and run on an Android device or emulator. Accept microphone permission for voice input.

Command-line build: `./gradlew :app:assembleDebug`.

Use AVai and Friend APK artifacts from the same successful combined workflow run. Install AVai first so the signature-protected bridge can connect.

## Memory and privacy

- Memory and saved conversations are local and encrypted using Android Keystore-backed AES-GCM storage.
- Imported excerpts are reference data; review them before retaining.
- Friend sends the prompt, relevant memory and bounded recent context to AVai by default. AVai may send them to the selected cloud provider under that provider's terms.
- With on-device inference selected in AVai, generation happens locally with the imported model.
- No chat, memory or API-key cloud sync is implemented.
- API keys entered in Friend's optional direct mode are saved locally in Friend; use AVai routing for strongest separation.

Friend is a supportive AI companion, not a licensed therapist or emergency service. AI responses can be inaccurate.

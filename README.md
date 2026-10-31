# CGL+ AVai

**CGL+ AVai** contains two native Android apps: **AVai**, the main CGL+ AI workspace, and **Friend**, its voice-and-face companion.

## CGL+ AI architecture

CGL+ AI takes conceptual inspiration from the original CGL+ LabZ AHi1 project, but it is a new assistant architecture—not a copy of AHi1's neural simulation.

Its design separates identity, input understanding, grounded knowledge, relevant user-controlled memory, reasoning orchestration and a replaceable language-generation engine. AVai is the workspace; Friend uses the same CGL+ AI behaviour through AVai by default. AHi1-inspired ideas are modularity, identity, knowledge, memory and communication; CGL+ AI does **not** reproduce AHi1's four-region brain simulation.

### Model status

The custom CGL+ AI orchestration and context layer is implemented in Kotlin. The actual language-model weights are **not yet a proprietary model trained by CGL+ and are not bundled here**. On-device language generation requires a user-imported compatible `.litertlm` model; cloud generation requires the user's own provider API key. This separates CGL+ software architecture from independently published model weights.

## AVai
- Native Kotlin + Jetpack Compose AI workspace.
- CGL+ AI cognitive orchestration and context assembly.
- Local-first default; import a compatible `.litertlm` model for on-device language generation.
- Optional Gemini, OpenAI API and Anthropic Claude connections.
- API-key profiles encrypted with Android Keystore-backed local storage.
- Saved chats, local history, user-controlled memory and model management.
- Consent-controlled Friend inference bridge. Provider credentials stay inside AVai.

## Friend
- Animated companion face consisting of **only two eyes and a mouth**.
- Android speech recognition and text-to-speech.
- Local chats, editable memory and imported conversation excerpts.
- Uses AVai by default; optional direct provider mode remains available.

## Chromebook / ChromeOS compatibility

AVai and Friend are configured as resizable Android apps for ChromeOS:
- Touchscreen hardware is optional; primary actions remain available through clickable controls.
- Both activities respond to window-size, orientation, screen-layout and keyboard-attachment changes.
- Main content is centered and width-limited on large displays, while retaining full-width layouts on phones.
- Keyboard shortcuts: **Ctrl+Enter** sends a message and **Ctrl+N** starts a new chat in AVai and Friend.
- Friend microphone hardware is optional; typing remains available when a microphone or speech-recognition service is unavailable.

Install them on a Chromebook only when that device supports Google Play / Android apps. The built AVai APK contains LiteRT-LM JNI libraries for **ARM64 (`arm64-v8a`) and x86-64 (`x86_64`)**; 32-bit ARM/x86 devices can use the app interface and cloud-provider mode but cannot run AVai local inference. Model format, memory and performance still need to match the device.

## No accounts or sign-in

AVai and Friend do not use Firebase Authentication, Google sign-in, email/password sign-in, registration or CGL+ Accounts. Both apps open directly. Chat history, memory, model files and API-key profiles remain local. No cloud sync is included.

## Friend ↔ AVai security

The Messenger bridge uses a signature-level permission, verifies caller UID/package and shared signing certificate, and checks **Allow once / Always allow / Deny** consent before inference. One-time access expires after five minutes. Only bounded prompt/context fields cross the bridge; **API keys never travel from AVai to Friend**. Install both APKs from the same successful combined workflow run.

## Offline models

AVai uses Google's [LiteRT-LM Android runtime](https://ai.google.dev/edge/litert-lm/android). Import a compatible `.litertlm` model from a trusted publisher and read its licence and device requirements. The local SHA-256 fingerprint is not proof of publisher authenticity. File size is not runtime RAM usage. Imported weights are not represented as CGL+-trained weights.

## Build APKs

Use AVai-debug-apk and Friend-debug-apk from the **same successful combined workflow run**. Install **AVai first, then Friend**. These are debug builds, not Play Store release builds.

## Chromebook validation still required

The manifests and layouts are prepared for ChromeOS, but an actual Chromebook runtime test is still required. Test a touchscreen and non-touchscreen device, resizable windows, keyboard/trackpad navigation, microphone availability and local-model ABI compatibility before declaring every Chromebook supported.

## Remaining work

- Train or fine-tune a legally usable model for an actual CGL+-owned language-model release; prepare a reviewed dataset, model card and evaluation suite.
- Add an official model catalogue with verified checksums and licence metadata.
- Device-test bridge consent/revocation, local inference, speech permissions and performance.
- Complete production signing, privacy/accessibility review and release packaging.

Friend is a supportive AI companion, not a therapist or emergency service. AI responses can be inaccurate. Never commit API keys or signing keys.

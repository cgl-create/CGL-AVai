# CGL+ AVai

**CGL+ AVai** is the umbrella project for two Android apps: **AVai** and **Friend**.

- **AVai** is the AI workspace: provider connections, multiple API-key profiles, local/offline LLMs, per-chat model selection, chat history, and user-controlled AI memory.
- **Friend** is the companion app with the animated eyes-and-mouth avatar, voice interaction, and a permission-based bridge to AVai.
- The existing AVai web/PWA experience is retained during the migration.

## Repository status

The existing native Android project is in [Friend/](./Friend/). The web AVai app is [index.html](./index.html). The current Friend debug APK workflow is [friend-android-build.yml](./.github/workflows/friend-android-build.yml).

The two-app architecture, security boundaries, consent flow, Firebase prerequisites, AI Match design, and staged delivery plan are documented in [ARCHITECTURE.md](./ARCHITECTURE.md).

**Important:** The repository's GitHub name is still `CGL-AVai-Therapist` until it is renamed in GitHub repository settings to `CGL-AVai`. Updating files cannot change the repository's actual GitHub name.

## Existing Friend features

- Animated avatar consisting of only eyes and mouth.
- Android speech recognition and text-to-speech.
- Provider selection for Gemini, OpenAI API, or Anthropic Claude.
- Encrypted on-device storage for provider keys and local conversation data.
- Persistent local chat history, editable memory notes, and text/JSON conversation import.

These existing features are not yet the complete AVai/Friend shared architecture described in the roadmap. In particular, the inter-app consent bridge, Firebase sign-in, shared chat store, multi-profile provider manager, and native AVai Android app still require implementation and device testing.

## Existing AVai web app

The web app remains a standalone static app with voice input/output, provider settings, and CGL+ dark/yellow styling. Its current browser-only configuration is not yet shared securely with Friend. Do not paste private API keys into public issue trackers or commit them to source.

## Safety and privacy

AVai and Friend are supportive AI tools, not clinicians or emergency services. Model responses can be inaccurate. Users should control whether conversations are sent to a cloud provider or retained in history. Never store API keys in Firestore or send them through deep-link query parameters.

## Development

Open [Friend/](./Friend/) in Android Studio with JDK 17 and Android SDK Platform 36 installed. Run the existing debug build workflow to verify Friend. The architecture roadmap explicitly separates implemented functionality from planned work.

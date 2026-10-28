# CGL+ AVai — product and integration architecture

CGL+ AVai is the umbrella project for two separate Android apps. The repository is named `CGL-AVai`.

- **AVai** — the AI workspace at `AVai/`. The initial native Compose app supports Gemini, OpenAI API and Anthropic providers, editable model IDs, encrypted on-device keys, chat history, multiple saved conversations, memory notes, and a prototype Friend-consent screen.
- **Friend** — the companion app at `Friend/`, with the animated eyes-and-mouth avatar, speech input/output, wellbeing-oriented chat, local chat history, memory notes and an AVai launch entry point.
- The existing static web/PWA app remains at the repository root.

## Current implementation state

Implemented in source:
- Native Android project scaffolding for both apps.
- AVai provider selection, editable model ID, per-provider key storage, encrypted local history and memory notes.
- AVai saved conversation index, open/delete controls, and separate new conversations.
- Friend avatar, speech recognition, text-to-speech, conversation history and memory import/editing.
- Separate APK build workflows plus a combined workflow for building both APKs.

Not implemented or not yet verified end-to-end:
- Firebase/CGL+ Accounts authentication.
- A functioning secure Friend-to-AVai inference bridge. Friend's “Connect to AVai” entry point opens the consent-screen prototype, but the screen clearly explains that consent currently does not grant inference access and no API keys are transferred.
- Persistent Allow once/Always allow/revocation, verified Binder/service caller, shared cross-app chat store, multi-profile-per-provider secrets, local/offline LLM runtime and AI Match catalogue.
- Device testing, production signing, accessibility review and release hardening.

Do not describe a feature as complete until its implementation and tests are present.

## Product boundaries

### AVai owns
- Provider configurations and secrets.
- Model/provider selection and AI requests.
- Chat history and user-controlled memory.
- Future Firebase Authentication for CGL+ Accounts.
- Companion access consent and revocation.

### Friend owns
- Avatar, voice input/output and companion-specific UI.
- The conversational experience.
- A future narrow capability to request inference from AVai.
- No direct access to AVai's API keys once the bridge is complete.

## Companion consent and transport

The intended consent text is:

> Allow Friend to use AVai with your AI configurations and connections?

The completed flow must identify the requesting app, explain scope, offer **Allow once**, **Always allow**, and **Deny**, and make persistent access revocable from AVai settings. A request must never silently grant access based only on a deep link or package name.

The planned Android communication mechanism is a narrowly scoped bound service with request/response messages and explicit consent checks. A signature-level permission is appropriate if both apps are distributed with the same signing certificate. AVai must verify the calling UID/package and enforce the stored grant for every inference request. Never place API keys, Firebase ID tokens, or full chat histories into exported intent URIs. The current intent entry point only displays a prototype dialog; it does not perform an AI hand-off.

## Storage and sync

- API keys, chat history and memory should stay encrypted locally using Android Keystore-backed encryption.
- Local model inference should remain on-device.
- Cloud-provider requests go only to the provider selected by the user.
- Firebase Authentication establishes account identity; it is not permission to upload private chats or API keys.
- Any future chat/memory sync must be opt-in, disclosed clearly, and governed by Firebase Security Rules. Never store API keys in Firestore.
- Users should be able to export and delete chats and memory.

## Firebase integration prerequisites

Use the existing CGL+ Accounts Firebase project only after both Android package IDs, Firebase app registrations, SHA fingerprints and OAuth providers are confirmed. Each application requires its own correct Firebase configuration. Do not invent `google-services.json`, commit service-account credentials, or put private server keys in source control. Firebase client configuration is not a service-account secret, but authentication must still enforce proper security rules and server-side authorization.

## AI Match and local models

A future catalogue should include model ID/version, quantization, download size, SHA-256, license, supported runtime, context limits, minimum memory guidance and capabilities. Verify hashes before loading. Recommendations must consider available memory and runtime support, not only installed RAM. Show download size and estimated runtime memory separately, warn about battery/heat/performance, require confirmation before downloads, and let users remove models.

## Release and build workflow

The repository provides individual workflows for AVai and Friend plus `.github/workflows/android-apps-build.yml` to build both debug APKs in one run. Debug APKs are test builds, not Play Store release artifacts. For a future signature-protected bridge, both APKs must be built/signed with the same certificate and tested together from the same compatible build.


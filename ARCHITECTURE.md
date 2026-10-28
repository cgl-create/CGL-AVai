# CGL+ AVai — product and integration architecture

CGL+ AVai is the umbrella project for two separate Android apps:

- **AVai** — the AI workspace. Users configure providers, manage multiple API-key profiles, install and manage local models, choose the active model per chat, and browse conversations and memory.
- **Friend** — the companion interface with the animated eyes-and-mouth avatar. Friend delegates AI requests to AVai only after the user grants explicit permission.

The existing web experience remains available during the migration. The repository rename to `CGL-AVai` should be performed in GitHub repository settings; this code change does not rename the GitHub repository itself.

## Product boundaries

### AVai owns
- Provider and model catalogue, provider connections and multiple user-owned API-key profiles.
- Optional local model downloads, model/runtime compatibility checks, and deletion.
- Chats, chat titles, message history, and user-controlled memory.
- Firebase Authentication for CGL+ Accounts.
- The consent screen for companion apps requesting access.

### Friend owns
- Avatar, voice input/output, and companion UI.
- Conversation UI and per-chat model selection.
- A local, revocable grant to request inference through AVai.
- No direct access to AVai's API keys or provider credentials.

## Companion consent

When Friend needs AVai, it opens AVai using an explicit Android intent/deep link. AVai shows a Spotify-style confirmation:

> Allow Friend to use AVai with your AI configurations and connections?

The screen must identify the requesting app, explain the scope, and offer **Allow once**, **Always allow**, and **Deny**. Persistent grants must be revocable in AVai settings. Do not silently grant access based only on a package name or an unverified deep link.

Android app communication should use a narrowly scoped bound service or explicit intent contract. Never put API keys, Firebase ID tokens, or full chat histories into an exported intent URI. For cross-process requests, AVai should verify the caller's package/signing certificate and enforce consent before performing inference. Use a signature-level permission when the apps are signed with the same release certificate. If the apps are distributed independently, use Android's verified caller identity and a secure request/response channel.

## Shared data and sync

Use a versioned internal data model for providers, model profiles, chats, messages, and memory. Keep provider secrets encrypted locally in AVai with Android Keystore. Friend should receive only a narrow inference capability, never raw API keys.

- Local model inference stays on-device.
- Cloud-provider requests are sent to the provider chosen by the user.
- Firebase Authentication establishes CGL+ Accounts identity; it is not a reason to upload private chats or API keys.
- Chat/memory sync should be opt-in, clearly disclosed, and encrypted where feasible. Never store API keys in Firestore.
- Users can export and delete their chat history and memory.

## Firebase integration prerequisites

Use the existing CGL+ Accounts Firebase project only after its Android application registrations, package names, SHA fingerprints, and OAuth providers have been confirmed. Each Android package needs the correct Firebase app configuration. Do not commit service-account credentials or private server keys. Firebase client configuration is not a service-account secret, but authentication still must enforce Firebase Security Rules and server-side authorization.

## AI Match (device-aware local models)

The model catalogue should include model ID/version, quantization, download size, SHA-256, license, supported runtime, context limits, minimum memory guidance, and supported capabilities. Verify the hash before loading. Recommendations must consider available memory and runtime support, not just total installed RAM. Show file size and estimated runtime memory separately, warn about battery/heat/performance, require user confirmation, and let users delete models.

## Delivery stages

1. **Product foundation:** rename repo to `CGL-AVai`, document the two-app split, preserve the current Friend build and existing web AVai.
2. **AVai Android shell:** create the native AVai app and establish shared versioned data contracts.
3. **Accounts:** register both package IDs with Firebase and add CGL+ Accounts sign-in.
4. **Consent bridge:** implement and test Friend-to-AVai consent, revocation, caller verification, and request/response handling.
5. **AI workspace:** multiple provider profiles/API keys, model selection per chat, chat list, persistent history, and user memory.
6. **AI Match:** model catalogue, device compatibility checks, download integrity verification, local runtime, and performance testing.
7. **Release hardening:** privacy review, security tests, accessibility, migration tests, and signed release builds.

Do not describe a stage as implemented until its code and tests are present.

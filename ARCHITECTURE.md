# CGL+ AVai — product and AI architecture

## Product structure
- **AVai** — main CGL+ AI workspace; owns provider connections, local model runtime, API keys, chat history, memory and companion authorization.
- **Friend** — companion interface with an eyes-and-mouth face, voice input/output and local chat management; uses AVai by default.
- The original web/PWA app remains at the repository root.

## CGL+ AI: AHi1-inspired, not a brain-simulation copy

AHi1 v1 is the original CGL+ LabZ experiment. It explored a distinct identity, computational core, symbolic knowledge, sensory inputs and Lloyd as a communication layer. Its current four-region network is synthetic demonstration data.

CGL+ AI adopts the **design principles**, not the old brain structure:
1. Identity and policy.
2. Input understanding and lightweight task-mode selection.
3. Relevant knowledge retrieval.
4. User-controlled memory retrieval.
5. Reasoning orchestration: understand, retrieve, evaluate, compose and verify.
6. Replaceable local or provider language generation.
7. AVai workspace and Friend companion communication.

There is no copied four-region simulation or simulated connectome. This cognitive orchestration layer is not itself a trained LLM.

## Implemented
- Native Kotlin + Jetpack Compose AVai and Friend apps.
- Same CGL+ AI design and matching task-mode/context logic in both clients.
- AVai LiteRT-LM local model import and inference.
- Optional Gemini, OpenAI Responses API and Anthropic Messages API providers.
- Android Keystore-backed encrypted API-key profiles in AVai.
- Local chat history and editable memory.
- Friend defaults to AVai; bounded messages/context cross the bridge, not credentials.
- Allow once / Always allow / Deny consent, one-time expiry and revocation.
- Signature-protected Messenger service verifies caller UID/package/signing certificate.
- No Firebase Authentication, Google sign-in, email/password sign-in, registration or CGL+ Accounts dependency.

## ChromeOS support

Both Android activities are explicitly resizable, do not require a touchscreen, handle common window/orientation/keyboard configuration changes, and provide Ctrl+Enter / Ctrl+N shortcuts. Compose layouts constrain their maximum desktop width while filling smaller windows. Friend microphone hardware is optional.

ChromeOS installation depends on the device supporting Android apps / Google Play. The current AVai APK contains LiteRT-LM JNI libraries for ARM64 (`arm64-v8a`) and x86-64 (`x86_64`). On other CPU architectures, the app UI and cloud-provider route remain available, but local LiteRT-LM inference is disabled with an explanatory error. Model format, RAM and performance still require real-device testing.

## Data and security boundaries

AVai is the source of truth for provider configurations and keys when Friend uses AVai. API keys must never travel through Binder, intents or Friend preferences in AVai mode. Requests are bounded and consent-checked.

Chats, memory, API-key profiles and imported models remain in app-private local storage. This does not guarantee protection on a rooted or compromised device. Cloud-provider requests transmit relevant prompts/context to that provider under its terms; local generation remains on-device.

Memory notes are user-controlled reference data. Imported excerpts are untrusted and must never override system policy or the current request.

## Current model boundary

CGL+ AI identity, context selection and orchestration are custom application code. The language-generation weights are not yet trained or released by CGL+. AVai requires an imported compatible `.litertlm` file or the user's own provider API key. Do not describe a third-party model as CGL+-trained.

A future CGL+ model release requires suitable base-model licence terms, a reviewed dataset, fine-tuning/quantization, reproducible conversion, safety/quality/privacy/device evaluation, a model card and release checksums.

## Verification

The combined GitHub Actions workflow builds both apps on one runner. Use both APK artifacts from the same successful run because the bridge requires a shared signing certificate. CI compilation is not end-to-end device validation; bridge consent, local inference, microphone permissions, TTS and performance still require physical-device testing.

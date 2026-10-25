# CGL+ AVai / Friend

## Native Android app — Friend

The Android Studio project lives in [Friend/](./Friend/). Open the Friend folder in Android Studio to configure and build the native Kotlin app. It includes an animated face, Android microphone transcription, spoken replies using Android TextToSpeech, and provider selection for Gemini, OpenAI API, or Claude.

Friend also has encrypted on-device API-key storage, persistent chat history, editable memory notes, and an importer for text from extracted ChatGPT JSON exports and text transcripts.

- [Android project setup and safety notes](./Friend/README.md)
- [Native app UI](./Friend/app/src/main/java/com/cgl/friend/MainActivity.kt)
- [AI provider API client](./Friend/app/src/main/java/com/cgl/friend/AiClient.kt)
- [Encrypted local storage](./Friend/app/src/main/java/com/cgl/friend/SecureStore.kt)
- [Memory importer](./Friend/app/src/main/java/com/cgl/friend/MemoryImporter.kt)

**Memory limitation:** Friend cannot automatically access your ChatGPT account history or saved memories via an API key. To bring in older conversations, request a ChatGPT data export, extract conversations.json, and import it in Friend's Memory screen. API usage is billed separately by the provider.

## AVai web app

AVai is a static, mobile-friendly web app with an animated digital face, speech input, spoken replies, selectable browser voices, and tone preferences. It is a supportive AI companion—not a therapist, clinician, or emergency service.

## Features

- Animated face with idle, listening, thinking, and speaking states.
- Six conversation tones: Gentle, Calm, Encouraging, Neutral, Reassuring, and To the point.
- Browser text-to-speech with installed voice selection, speed, and pitch controls.
- Speech-to-text microphone input where supported by the browser.
- Optional Google Gemini or OpenAI API connection for generated replies.
- Responsive dark interface with CGL+ yellow (#f2c94c) accents.
- No sign-in and no server-side conversation store. The chat is held in page memory and can be cleared with **New chat**.
- Minimal offline shell caching. AI replies require internet access.

## Run it

Open index.html in a modern browser for the basic interface. Voice recognition and installable PWA features generally work best when served over HTTPS or localhost.

To run locally with Python installed:

```bash
python -m http.server 8000
```

Then open http://localhost:8000.

## Enable AI replies

1. Open AVai and tap the gear icon.
2. Choose **Google Gemini API** or **OpenAI API**.
3. Enter your own API key and a model enabled for that account.
4. Save for this session, then start a conversation.

The API key is held in JavaScript memory only. It is not written to local storage, a file, or this repository, and it is lost when the page is refreshed or closed. The browser sends prompts directly to the selected provider, so the provider's own data-handling terms apply. The app does not claim that conversations are confidential.

**Never hard-code a private key into source code or share one API key among visitors.** A browser app cannot keep a shared API secret safe. For a public, multi-user service, add a server-side API proxy with authentication, rate limits, abuse protection, and secrets stored in server environment variables.

Model names and provider access can vary by account. This frontend makes direct provider requests and therefore depends on the provider allowing the request from the current browser origin. If a browser request fails due to CORS, configure a secure backend proxy rather than disabling browser security.

## Safety and boundaries

AVai is not a licensed therapist, cannot diagnose conditions, and cannot replace qualified care. It uses a supportive system instruction, but model safeguards are not guaranteed. If someone appears to be in immediate danger, seek help from a trusted person nearby and local emergency services. Do not rely on this page for crisis response.

Do not enter information you would not want sent to an AI provider. Use **New chat** to clear the visible conversation and in-memory conversation context.

## Deployment

This is a static site suitable for a static host such as GitHub Pages, Cloudflare Pages, or any HTTPS web server. The repository is currently configured as private; check your GitHub plan and Pages visibility requirements before publishing. Do not make the repository public just to deploy it without reviewing its contents first.

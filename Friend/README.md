# Friend by CGL+ — Android Studio project

**Friend** is a native Kotlin + Jetpack Compose Android app in the **Friend/** folder of this repository. It is a separate Android app project; the existing AVai web app remains unchanged.

## Implemented in this source

- Animated, gold CGL+ digital face with idle, listening, thinking, and speaking states.
- Android SpeechRecognizer microphone input. The transcript is placed in the compose box so you can review it before sending.
- Android TextToSpeech replies, voice selection from voices installed on the device, speaking-rate control, and an on/off toggle. The current speech output uses the device's TTS engine; it is not yet connected to the providers' realtime/native audio models.
- Selectable AI provider: Google Gemini, OpenAI API (the API used by your own app—not the ChatGPT app subscription), or Anthropic Claude.
- Provider-specific model names and provider-specific API keys.
- AES-256-GCM encryption through an app-specific key in Android Keystore for saved keys, chat transcript, notes, imported excerpts, and settings values stored through SecureStore.
- Local conversation history across app launches and AI-provider changes.
- Editable cross-session memory notes and an import tool for older JSON conversation exports and plain-text transcripts.
- CGL+ dark UI and yellow #f2c94c accent color.

## Open and build

1. Clone or download this repository from GitHub.
2. In Android Studio, choose **Open** and select the **Friend** folder (not app/).
3. Use JDK 17 and install Android SDK Platform 36 when prompted.
4. Let Android Studio sync the Gradle project and resolve dependencies.
5. Select the app run configuration, select an Android device or emulator, and press **Run**. The Gradle wrapper is included for command-line builds.
6. Accept the microphone permission when using voice input.

The project uses Android Gradle Plugin 8.13.2, Kotlin 2.2.20, compile SDK 36, min SDK 26, Jetpack Compose, and the official Gradle 8.13 wrapper. A local Android SDK build has **not** been executed in this environment, so treat the first Android Studio sync/build as a required verification step. From the Friend folder, the debug APK build command is ./gradlew :app:assembleDebug. Do not commit API keys or Gradle caches.

## Configure an AI provider

Open **Settings** inside Friend, select a provider, enter your own API key, check the model name, and save. The selected provider's key is saved encrypted on the device. The app doesn't store keys in this repository, and it does not ask for or use your ChatGPT password.

- **Gemini:** https://aistudio.google.com/apikey
- **OpenAI API:** https://platform.openai.com/api-keys
- **Claude:** https://platform.claude.com/settings/keys

API access, model availability, quotas, and billing depend on the relevant provider account. **ChatGPT and OpenAI API billing are separate.** A ChatGPT Plus/Pro subscription does not automatically include API credits. See OpenAI's billing explanation: https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform.

Initial model identifiers are Gemini `gemini-3.8-flash`, OpenAI `gpt-6-luna`, and Claude `claude-sonnet-5-5`. Provider model catalogs change, so you can edit each identifier in Friend settings if a model is unavailable to your account. No API key is included or hard-coded in this project.

## How Friend's memory works

Friend has its **own local memory**, separate from ChatGPT's account memory:

- **Chat history:** the recent conversation is stored encrypted on the device and is included in future prompts (within a bounded recent-history window).
- **Memory notes:** text you write and save in the Memory screen is included as context in future conversations, across all three providers.
- **Imported older chats:** Friend can import text from an extracted ChatGPT conversations.json file or a plain text transcript. It extracts text-only message excerpts up to a bounded limit. You can clear imported context in Memory.
- **Provider context:** each API request sends the relevant saved notes, imported excerpts, and a recent slice of the Friend transcript to whichever provider you selected. The provider receives that content as part of the request.

### Import older ChatGPT chats

1. In ChatGPT, open **Settings → Data Controls → Export Data** and request an export. This is an account data export; OpenAI sends the download instructions separately.
2. Download and unzip the export file on your device or computer.
3. In Friend, open **Memory → Import .json / .txt** and choose conversations.json. For very large exports, select smaller JSON files or create a plain-text transcript of the specific conversations you want Friend to use. The importer currently accepts files up to 8 MB.
4. Review the import and add a shorter, more useful set of stable facts/preferences to **Your saved notes**. Remove anything you no longer want retained.

Official export guidance: https://help.openai.com/en/articles/7260999-exporting-your-chatgpt-history-and-data.

**Important:** an API key does not unlock or synchronize your old ChatGPT conversations, saved memories, custom instructions, or ChatGPT account. Friend cannot automatically read the ChatGPT app, this conversation, or conversations outside Friend. To use that context, you must deliberately import a relevant export or write the information in Memory. The importer is best-effort, text-only, bounded, and does not recreate your ChatGPT account, original chat list, images, or attachments.

## Privacy and costs

- Values stored through Friend's local store are encrypted with AES-256-GCM using an Android Keystore key. allowBackup=false is set to avoid carrying encrypted app data to a new device without the matching key. This is meaningful protection at rest, not a guarantee against a rooted/compromised device, screen capture, malware, or memory inspection while the app is running.
- If you clear app storage, uninstall Friend, lose the device/key, or the encrypted data becomes unreadable, locally saved memory may be lost. Export anything you want to keep before device migration; a portable encrypted export/restore feature is not included yet.
- When you send a message, Friend sends your message plus some recent chat history and saved/imported memory to your selected provider. The provider handles that data under its own terms. Do not import or send sensitive information unless you're comfortable sharing it with that provider.
- API usage may incur charges. Set usage limits at the provider, keep keys private, and delete/revoke a key if you think it has been exposed.
- API keys live on the phone in encrypted app storage and are not embedded in source. Since this is a direct-to-provider prototype, a technically compromised phone or reverse engineered running process can still expose them. A public multi-user production app should use a secured backend rather than ask users to put a shared developer key in the APK.

## Wellbeing and safety boundary

Friend is a supportive AI companion, not a licensed therapist, clinician, or emergency service. It may be wrong or misunderstand context. It must not replace a qualified professional or a trusted person. If you are at immediate risk, contact a trusted person near you and local emergency services.

## File layout

- app/src/main/java/com/cgl/friend/MainActivity.kt — Compose UI, animation, speech input/output, settings and memory screens.
- app/src/main/java/com/cgl/friend/AiClient.kt — direct provider API integrations.
- app/src/main/java/com/cgl/friend/SecureStore.kt — AES-GCM encrypted app-local storage.
- app/src/main/java/com/cgl/friend/MemoryImporter.kt — text/JSON chat import.
- app/src/main/AndroidManifest.xml — permissions, label, and app configuration.

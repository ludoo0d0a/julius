# Voice and Speech Processing Architecture

This document explains how voice and speech are currently processed in the Julius application.

## 1. The "Sequential Cascade" Pipeline

Julius uses a turn-based, sequential pipeline where each step must complete before the next begins.

### Step 1: Speech-to-Text (STT)
*   **Phone path:** `AndroidVoiceManager` uses the native Android `SpeechRecognizer` API (`android.speech`). When the system detects the user has finished speaking (`onResults`), it produces a text transcript and emits it via `transcribedText`.
*   **Car mic path (Android Auto):** When "Use Car Microphone" is on, the app records raw audio and passes it to a **transcriber** callback. The transcriber is composed in `ConversationStore` according to the **STT engine** setting:
    *   **Local only:** Use only Vosk (on-device); no cloud fallback.
    *   **Local first:** Try Vosk first; if it returns null/blank, use the agent’s `transcribe()` when the agent supports STT (e.g. OpenAI, Deepgram).
    *   **Native only:** Do not use Vosk; use only the agent’s cloud STT when supported.
*   The setting is available in **Settings > STT engine (car)** and in Android Auto **Settings > STT engine (car)**.

### Step 2: Orchestration
*   **Implementation:** `ConversationStore` (Common module)
*   **Process:**
    1. It observes the `transcribedText` flow from the `VoiceManager`.
    2. It adds the transcript to the message history to maintain conversation context.
    3. It builds a combined prompt (system prompt + history + last message).
    4. It sets the state to `Processing`.
    5. it calls the selected `ConversationalAgent`.

### Step 3: LLM & Audio Generation (The Agent)
*   **Implementation:** `ConversationalAgent` (e.g., `OpenAIAgent`, `ElevenLabsAgent`, `GeminiAgent`)
*   **Process:** The agent makes a network request to its respective cloud provider.
    *   **OpenAI / ElevenLabs:** These agents return both **text and raw audio bytes** (TTS is performed in the cloud by the provider).
    *   **Gemini / Deepgram:** These agents currently return **only text**.

### Step 4: Text-to-Speech (TTS) & Playback
*   **Implementation:** `AndroidVoiceManager`
*   **Process:**
    *   **Cloud Audio:** If the agent provided audio bytes (e.g., OpenAI's "nova" voice), they are played via Android's `MediaPlayer`.
    *   **Local Synthesis:** If the agent provided only text (e.g., Gemini), the app uses the native Android `TextToSpeech` engine to synthesize the voice locally on the device.

---

## 2. Special Features

### Barge-in / interrupt while speaking
On the **phone `SpeechRecognizer` path**, Julius can keep the microphone active while TTS or agent audio plays so the user can interrupt long replies. This is controlled by **Interrupt while speaking** in Settings (mobile) and **Voice & Advanced Settings** (Android Auto): `SpeakingInterruptMode` in [`SettingsManager.kt`](../androidApp/src/main/kotlin/fr/geoking/julius/SettingsManager.kt).

| Mode | Behavior |
|------|----------|
| **Off** | Recognition is cancelled before playback; no listening during assistant audio. |
| **Hey Julius only** | A hidden barge-in recognizer runs; only **"hey julius"** or **"stop"** stops playback and captures input (fewer false triggers from echo). |
| **Any speech** | Any detected speech stops playback; the recognized text is sent as the next user turn (may false-trigger if the assistant’s voice is picked up by the mic). |

**Migration:** The legacy boolean `hey_julius_during_speaking_enabled` is migrated once: if it was **true**, the mode becomes **Hey Julius only**; if **false**, **Any speech** (new default for users who had not enabled the old toggle). After save, preferences use `speaking_interrupt_mode` and the legacy key is removed.

**Car microphone:** While a **car mic** recording pass is in progress, barge-in is not started. After playback, barge-in still uses the system `SpeechRecognizer` (device-dependent on Android Auto). Full-duplex capture from the car-piped stream would need a separate pipeline.

### Multi-Agent Flexibility
The architecture is designed to be highly modular. By implementing the `ConversationalAgent` interface, new AI models or voice providers can be added without changing the core UI or orchestration logic.

### Optional Offline STT (Vosk)
For the car mic path, Julius can use **Vosk** for on-device speech recognition. A Vosk model can be placed in `androidApp/src/main/assets/models/vosk/<model-name>/` (e.g. a small English model); it is copied to app storage on first use. The **STT engine** setting controls whether to use Vosk only, Vosk first with cloud fallback, or cloud only.

---

## 3. Evaluation and Future Improvements

### Current Strengths
*   **Modularity:** Easy to swap between different LLMs or TTS providers.
*   **Cost Efficiency:** Native Android STT/TTS reduces reliance on paid cloud APIs for every step.
*   **Offline Potential:** The use of native components provides a path toward partially offline operation.

### Current Limitations
*   **Latency:** The "cascade" nature (wait for STT -> wait for LLM -> wait for TTS) creates additive delays.
*   **Information Loss:** Converting speech to text removes emotional cues, tone, and prosody before the LLM even sees it.
*   **Turn-Based Flow:** Interaction feels like a "walkie-talkie" rather than a fluid conversation.

### Recommendations for Future Evolution
The next logical step for Julius would be moving toward **Realtime Streaming/Multimodal APIs** (e.g., OpenAI Realtime API or Gemini Multimodal Live):
1.  **Audio Streaming:** Stream mic audio directly to the model as it happens.
2.  **Streaming TTS:** Start playing audio while the LLM is still generating the rest of the response.
3.  **End-to-End Multimodality:** Allowing the model to "hear" the user directly, preserving emotional context and significantly reducing perceived latency.

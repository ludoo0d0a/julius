# Voice and Speech Processing Architecture

This document explains how voice and speech are currently processed in the Julius application.

## 1. The "Sequential Cascade" Pipeline

Julius uses a turn-based, sequential pipeline where each step must complete before the next begins.

### Step 1: Speech-to-Text (STT)
*   **Implementation:** `AndroidVoiceManager` (Android-specific)
*   **Mechanism:** Uses the native Android `SpeechRecognizer` API (`android.speech`).
*   **Process:**
    1. The app listens to microphone input.
    2. When the system detects the user has finished speaking (`onResults`), it produces a text transcript.
    3. The transcript is emitted through a Kotlin Flow (`transcribedText`).

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

### Barge-in Support
Julius implements a "Barge-in" feature. While the assistant is speaking, it continues to run a `SpeechRecognizer` in a special "barge-in" mode. If it detects the user has started speaking again, it immediately stops the current audio playback/TTS and starts a new listening session.

### Multi-Agent Flexibility
The architecture is designed to be highly modular. By implementing the `ConversationalAgent` interface, new AI models or voice providers can be added without changing the core UI or orchestration logic.

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

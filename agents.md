# Julius - Voice AI Assistant

## Context

### Project Overview
Julius is a **Kotlin Multiplatform (KMP)** voice assistant application designed for **Android** and **Android Auto**. It provides hands-free voice interaction capabilities using various AI agents and voice processing services.

### Architecture

#### Module Structure
- **`:shared`**: Core business logic, data models, and cross-platform code
  - Contains `ConversationStore` for state management
  - Implements `VoiceManager` interface for voice processing
  - Houses multiple `ConversationalAgent` implementations
  - Supports Android and iOS targets (iOS targets configured but not actively used)

- **`:androidApp`**: Android application entry point
  - Mobile UI using Jetpack Compose
  - Android Auto integration via Car App Library
  - Platform-specific voice implementation (`AndroidVoiceManager`)
  - Dependency injection setup with Koin

#### Key Components

**Agents (`shared/src/commonMain/kotlin/com/antigravity/voiceai/agents/`):**
- `ConversationalAgent`: Interface for all AI agents
- `OpenAIAgent`: OpenAI GPT integration
- `GeminiAgent`: Google Gemini integration
- `ElevenLabsAgent`: ElevenLabs voice synthesis + Perplexity chat
- `DeepgramAgent`: Deepgram voice processing
- `PerplexityAgent`: Perplexity implementation for web-aware responses

**Core Services:**
- `ConversationStore`: Manages conversation state, message history, and coordinates between voice manager and agents
- `VoiceManager`: Interface for voice input/output (listening, transcription, speaking)
- `AndroidVoiceManager`: Android-specific implementation of VoiceManager
- `SettingsManager`: Manages app settings and API keys via SharedPreferences

**State Management:**
- Uses Kotlin Flow (`StateFlow`, `MutableStateFlow`) for reactive state
- `ConversationState`: Contains messages, current status, and transcript
- `VoiceEvent`: Enum for voice states (Listening, Processing, Silence, Speaking)

**Dependency Injection:**
- Uses Koin for DI
- `DynamicAgentWrapper`: Allows runtime agent switching without Koin reload
- Agent selection based on `SettingsManager` configuration

#### Android Auto Integration
- `VoiceAppService`: Car App Service entry point
- `VoiceSession`: Manages Android Auto session lifecycle
- `MainScreen`: Android Auto UI screen
- Configured in `AndroidManifest.xml` with appropriate metadata

#### API Keys & Configuration
- API keys stored in `local.properties`:
  - `elevenlabs.key`
  - `gemini.key`
- Keys are read at build time and exposed via `BuildConfig`
- Settings can be overridden at runtime via `SettingsManager`

## Available Agents

Julius supports multiple AI agents, each with different capabilities, pricing models, and use cases. Agents can be switched at runtime via the app settings.

### 1. OpenAI Agent (`OpenAIAgent`)

**Model Used:** GPT-4o for chat, TTS-1-HD for text-to-speech

**How it works:**
- Uses GPT-4o for generating conversational responses
- Uses OpenAI's TTS-1-HD model for high-quality voice synthesis
- Returns both text and audio in `AgentResponse`

**Pros:**
- ✅ High-quality, natural-sounding voice synthesis
- ✅ Excellent conversational capabilities with GPT-4o
- ✅ Integrated solution (chat + TTS from one provider)
- ✅ Fast response times
- ✅ Reliable and well-documented API

**Cons:**
- ❌ Most expensive option (premium pricing)
- ❌ Requires OpenAI API key
- ❌ No free tier for production use
- ❌ Costs accumulate quickly with high usage

**Pricing (as of 2024):**
- **GPT-4o Chat:**
  - Input: ~$2.50 per 1M tokens
  - Output: ~$10 per 1M tokens
- **TTS-1-HD:**
  - ~$15 per 1M characters
- **Estimated cost per conversation:** ~$0.01-0.05 per interaction (varies by length)

**Best for:** Users who prioritize voice quality and have budget for premium service

---

### 2. Gemini Agent (`GeminiAgent`)

**Model Used:** Gemini 1.5 Flash

**How it works:**
- Uses Gemini 1.5 Flash for generating responses
- Returns text only (audio is null, uses system TTS)
- Leverages Google's free tier for cost-effective usage

**Pros:**
- ✅ **Generous free tier** (60 requests/minute, 1,500 requests/day)
- ✅ Very low cost even on paid tier
- ✅ Fast response times with Flash model
- ✅ Good conversational quality
- ✅ No separate TTS cost (uses Android system TTS)

**Cons:**
- ❌ Lower voice quality (relies on Android system TTS)
- ❌ Less natural-sounding compared to premium TTS services
- ❌ Requires Google API key (Gemini API)

**Pricing (as of 2024):**
- **Free Tier:**
  - 60 requests per minute
  - 1,500 requests per day
  - 1M tokens per day
- **Paid Tier (beyond free limits):**
  - Input: ~$0.075 per 1M tokens
  - Output: ~$0.30 per 1M tokens
- **Estimated cost per conversation:** $0 (free tier) or ~$0.001-0.005 per interaction

**Best for:** Users on a budget, high-volume usage, or those who don't need premium voice quality

---

### 3. Perplexity Agent (`PerplexityAgent`)

**Model Used:** Perplexity AI (llama-3.1-sonar-small-128k-online)

**How it works:**
- Uses Perplexity's online model for real-time web-aware responses
- Returns text only (audio is null, uses system TTS)
- Model is optimized for up-to-date information retrieval

**Pros:**
- ✅ **Web-aware responses** (can access current information)
- ✅ Good for questions requiring recent data
- ✅ Competitive pricing
- ✅ Fast inference with small model
- ✅ No separate TTS cost (uses Android system TTS)

**Cons:**
- ❌ Lower voice quality (relies on Android system TTS)
- ❌ May have rate limits
- ❌ Requires Perplexity API key
- ❌ Less conversational than larger models

**Pricing (as of 2024):**
- **Pay-per-use:**
  - Input: ~$0.20 per 1M tokens
  - Output: ~$0.20 per 1M tokens
- **Estimated cost per conversation:** ~$0.002-0.01 per interaction

**Best for:** Users who need current information, web search capabilities, or cost-effective real-time answers

---

### 4. ElevenLabs Agent (`ElevenLabsAgent`)

**Model Used:** Perplexity AI (for chat) + ElevenLabs TTS (for voice)

**How it works:**
- Uses `PerplexityAgent` for generating text responses
- Uses ElevenLabs API for high-quality voice synthesis
- Combines web-aware chat with premium voice quality

**Pros:**
- ✅ **Best voice quality** (ElevenLabs is industry-leading)
- ✅ Web-aware responses via Perplexity
- ✅ Natural, human-like voice synthesis
- ✅ Multiple voice options available
- ✅ Good balance of quality and cost

**Cons:**
- ❌ Requires two API keys (Perplexity + ElevenLabs)
- ❌ More expensive than text-only agents
- ❌ Higher latency (two API calls)
- ❌ ElevenLabs free tier is limited

**Pricing (as of 2024):**
- **Perplexity (Chat):**
  - Input: ~$0.20 per 1M tokens
  - Output: ~$0.20 per 1M tokens
- **ElevenLabs (TTS):**
  - Free tier: 10,000 characters/month
  - Paid: ~$5 per 100,000 characters (Starter plan)
  - Higher tiers: Better rates with volume
- **Estimated cost per conversation:** ~$0.01-0.03 per interaction (depends on response length)

**Best for:** Users who want premium voice quality with web-aware responses, best overall experience

---

### 5. Deepgram Agent (`DeepgramAgent`)

**Status:** ⚠️ **Not yet implemented**

**Intended Model:** Deepgram for speech-to-text and potentially text-to-speech

**Planned Features:**
- Real-time speech recognition
- Potentially voice synthesis
- Low-latency processing

**Pros (expected):**
- ✅ Fast, real-time transcription
- ✅ Good accuracy
- ✅ Pay-per-minute pricing (usage-based)

**Cons (expected):**
- ❌ Requires Deepgram API key
- ❌ Implementation incomplete
- ❌ May require additional setup

**Pricing (as of 2024 - estimated):**
- **Speech-to-Text:**
  - Free tier: 12,000 minutes/month
  - Paid: ~$0.0043 per minute (beyond free tier)
- **Note:** Full pricing depends on implementation details

**Best for:** Future use when implementation is complete

---

## Agent Comparison Summary

| Agent | Voice Quality | Cost | Web-Aware | Free Tier | Best Use Case |
|-------|--------------|------|-----------|-----------|---------------|
| **OpenAI** | ⭐⭐⭐⭐⭐ | 💰💰💰 | ❌ | ❌ | Premium quality, budget not a concern |
| **Gemini** | ⭐⭐ | 💰 | ❌ | ✅ | Budget-conscious, high volume |
| **Perplexity** | ⭐⭐ | 💰 | ✅ | ❌ | Current information, cost-effective |
| **ElevenLabs** | ⭐⭐⭐⭐⭐ | 💰💰 | ✅ | ⚠️ Limited | Best overall (quality + web-aware) |
| **Deepgram** | ❓ | ❓ | ❓ | ✅ | Future implementation |

**Recommendations:**
- **For testing/development:** Gemini (free tier)
- **For production with budget:** Gemini or Perplexity
- **For premium experience:** OpenAI or ElevenLabs
- **For best value:** ElevenLabs (quality + web-aware)

## Setup Instructions

### Prerequisites
- **Android Studio Koala** or newer (recommended for KMP support)
- **JDK 17** or higher
- **Android SDK** with API level 26+ (minSdk) and 36 (targetSdk/compileSdk)

### Initial Setup

1. **Clone the repository** (if not already done)

2. **Configure Android SDK path**
   - Create or update `local.properties` in the root directory:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```

3. **Add API Keys** (optional, can be set later in app settings)
   - Add to `local.properties`:
   ```properties
   elevenlabs.key=your_elevenlabs_api_key
   gemini.key=your_gemini_api_key
   ```
   - Note: These are used as defaults but can be overridden in app settings

4. **Open in Android Studio**
   - Open the project root directory
   - Wait for Gradle sync to complete

5. **Sync Gradle**
   - Android Studio should auto-sync, or use: `File > Sync Project with Gradle Files`

6. **Build the project**
   - Build > Make Project (or `Cmd/Ctrl + F9`)
   - Or run: `./gradlew build`

### Running the Application

1. **For Android Mobile:**
   - Select `:androidApp` configuration
   - Choose a device/emulator (API 26+)
   - Run (or `Shift + F10`)

2. **For Android Auto:**
   - Deploy the app to a device
   - Connect to Android Auto (physical car or emulator)
   - The app should appear in Android Auto launcher

### Development Workflow

**Adding a new agent:**
1. Create new class in `shared/src/commonMain/kotlin/com/antigravity/voiceai/agents/`
2. Implement `ConversationalAgent` interface
3. Add `AgentType` enum value in `SettingsManager.kt`
4. Update `DynamicAgentWrapper.process()` to handle new agent type
5. Update settings UI if needed

**Modifying voice processing:**
- Android-specific: `AndroidVoiceManager.kt`
- Interface: `VoiceManager.kt` in shared module
- State handling: `ConversationStore.kt`

**Testing Android Auto:**
- Use Android Auto Desktop Head Unit (AA Desktop Head Unit)
- Or deploy to a physical device with Android Auto support

## Memories

### Important Code Patterns

1. **Agent Selection Pattern:**
   - Agents are selected dynamically at runtime via `DynamicAgentWrapper`
   - This allows switching agents without restarting the app or reloading Koin modules
   - Agent selection is based on `SettingsManager.settings.value.selectedAgent`

2. **Voice Event Flow:**
   - `VoiceManager` emits `VoiceEvent` through a Flow
   - `ConversationStore` observes these events and updates state
   - Transcription text flows separately via `transcribedText` Flow

3. **Audio Handling:**
   - Agents can return audio directly (`AgentResponse.audio`)
   - If audio is provided, `playAudio()` is used
   - Otherwise, `speak()` performs TTS on the text

4. **State Management:**
   - All UI observes `ConversationStore.state` (StateFlow)
   - State updates happen in coroutines within the provided scope
   - Messages are stored with unique IDs based on timestamps

5. **API Key Management:**
   - Build-time keys from `local.properties` → `BuildConfig`
   - Runtime keys from `SharedPreferences` → `SettingsManager`
   - Runtime keys take precedence over build-time keys
   - Keys can be set via Settings UI

### Key Files to Remember

- **`androidApp/src/main/kotlin/com/antigravity/voiceai/di/AppModule.kt`**: DI configuration, agent wrapper setup
- **`shared/src/commonMain/kotlin/com/antigravity/voiceai/ConversationStore.kt`**: Core conversation logic
- **`androidApp/src/main/kotlin/com/antigravity/voiceai/SettingsManager.kt`**: Settings and agent type management
- **`androidApp/src/main/kotlin/com/antigravity/voiceai/AndroidVoiceManager.kt`**: Platform-specific voice implementation

### Technology Versions
- Kotlin: 2.3.0
- Kotlin Multiplatform: 2.3.0
- Jetpack Compose: 1.10.0
- Compose Compiler: 1.5.13
- Android Car App Library: 1.7.0
- Ktor: 3.3.3
- Koin: 4.1.1
- Coroutines: 1.10.2
- Gradle: 8.13.2
- AGP: 8.13.2

### Build Configuration Notes
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`
- JVM target: 1.8
- Application ID: `com.antigravity.voiceai`
- Package namespace: `com.antigravity.voiceai` (androidApp), `com.antigravity.voiceai.shared` (shared)

### Android Auto Specifics
- Service: `VoiceAppService` in `androidApp/src/main/kotlin/com/antigravity/voiceai/auto/`
- Category: `androidx.car.app.category.MESSAGING`
- Minimum Car API Level: 1
- Requires `automotive_app_desc.xml` resource

### Common Issues & Solutions
- **API keys not working**: Check `local.properties` and app settings. Runtime settings override build-time keys.
- **Android Auto not appearing**: Ensure device/emulator has Android Auto support and app is properly signed
- **Voice not working**: Check `RECORD_AUDIO` permission is granted
- **Agent switching not working**: Verify `DynamicAgentWrapper` is being used and settings are saved


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

**Agents (`shared/src/commonMain/kotlin/fr/geoking/julius/agents/`):**
- `ConversationalAgent`: Interface for all AI agents
- `OpenAIAgent`: OpenAI GPT integration
- `GeminiAgent`: Google Gemini integration
- `ElevenLabsAgent`: ElevenLabs voice synthesis + Perplexity chat
- `DeepgramAgent`: Deepgram voice processing
- `PerplexityAgent`: Perplexity implementation for web-aware responses
- `EmbeddedAgent`: On-device offline LLM using Llamatik

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
- API keys in `local.properties` or env use the same key name (e.g. `OPENAI_KEY`, `GEMINI_KEY`, `ELEVENLABS_KEY`)
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

### 6. Embedded Agent (`EmbeddedAgent`)

**Model Used:** On-device LLM via Llamatik (supports various GGUF models)

**How it works:**
- Uses Llamatik library (Kotlin Multiplatform) with llama.cpp backend
- Loads GGUF format models from app assets or file system
- Runs inference completely offline on-device
- Returns text only (uses system TTS for voice output)
- No API keys or network connectivity required

**Pros:**
- ✅ **100% offline** - Works without internet connection
- ✅ **No API costs** - Free to use after initial model download
- ✅ **Privacy-focused** - All processing happens locally
- ✅ **No API keys needed** - Zero configuration required
- ✅ **Works in airplane mode** - Perfect for offline scenarios
- ✅ **Kotlin Multiplatform** - Uses Llamatik for cross-platform support

**Cons:**
- ❌ **Large app size** - Model files can be 650MB - 2GB+ (depending on model)
- ❌ **Slower inference** - On-device CPU is slower than cloud GPUs
- ❌ **Limited quality** - Smaller models have reduced capability vs. cloud models
- ❌ **Memory intensive** - Requires 4-8GB+ RAM for reasonable performance
- ❌ **Model download required** - Must manually add GGUF model files to assets
- ❌ **No web access** - Cannot access current information or real-time data
- ❌ **Battery impact** - CPU-intensive inference drains battery faster

**Recommended Models:**
- **TinyLlama (Q4_0):** ~650MB - Fastest, lower quality, good for older devices
- **Phi-2 (Q4_0):** ~1.6GB - Good balance of quality and speed
- **Gemma 2B (Q4_0):** ~1.4GB - Optimized for mobile, Google's recommendation
- **Llama-3.1-8B (Q4_0):** ~4.5GB - Better quality, requires 8GB+ RAM

**Pricing:**
- **Model download:** Free (download GGUF models from Hugging Face or similar)
- **Inference:** $0.00 per request (100% free after model acquisition)
- **App size impact:** +650MB to 2GB+ depending on model choice

**Setup Instructions:**
1. Download a GGUF model file (e.g., `phi-2.Q4_0.gguf` from Hugging Face)
2. Place it in `androidApp/src/main/assets/models/` directory
3. Create the `models` directory if it doesn't exist
4. The default path is `models/phi-2.Q4_0.gguf` (can be customized in code)
5. Select "Embedded" agent in app settings (no API key needed)

**Technical Details:**
- Uses Llamatik library v0.8.0+ for Kotlin Multiplatform
- Supports GGUF quantization formats (Q4_0, Q5_0, Q8_0, etc.)
- Models are loaded from Android assets at runtime
- Initialization happens on first use (lazy loading)
- System TTS is used for voice output (Android TextToSpeech)

**Estimated cost per conversation:** $0.00 (one-time model download)

**Best for:** 
- Privacy-conscious users who want complete offline functionality
- Users in areas with poor/no internet connectivity
- Development/testing without API costs
- Educational or demonstration purposes
- Users with sufficient device storage and RAM

---

## Agent Comparison Summary

| Agent | Voice Quality | Cost | Web-Aware | Free Tier | Best Use Case |
|-------|--------------|------|-----------|-----------|---------------|
| **OpenAI** | ⭐⭐⭐⭐⭐ | 💰💰💰 | ❌ | ❌ | Premium quality, budget not a concern |
| **Gemini** | ⭐⭐ | 💰 | ❌ | ✅ | Budget-conscious, high volume |
| **Perplexity** | ⭐⭐ | 💰 | ✅ | ❌ | Current information, cost-effective |
| **ElevenLabs** | ⭐⭐⭐⭐⭐ | 💰💰 | ✅ | ⚠️ Limited | Best overall (quality + web-aware) |
| **Deepgram** | ❓ | ❓ | ❓ | ✅ | Future implementation |
| **Embedded** | ⭐⭐ | 💰 (free) | ❌ | ✅ | Offline/privacy, no internet needed |

**Recommendations:**
- **For testing/development:** Gemini (free tier) or Embedded (offline)
- **For production with budget:** Gemini or Perplexity
- **For premium experience:** OpenAI or ElevenLabs
- **For best value:** ElevenLabs (quality + web-aware)
- **For offline/privacy:** Embedded (no API costs, complete privacy)

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
   - Add to `local.properties` (same names as env vars):
   ```properties
   ELEVENLABS_KEY=your_elevenlabs_api_key
   GEMINI_KEY=your_gemini_api_key
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
1. Create new class in `shared/src/commonMain/kotlin/fr/geoking/julius/agents/`
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

**Running on a physical car (phone-projected Android Auto):**  
Sideloaded/debug builds are hidden from the car launcher unless you allow non-Play apps:

1. **Build the right variant**  
   Use the **full** flavor so the app has car permissions (`ACCESS_SURFACE`, `NAVIGATION_TEMPLATES`). In Android Studio: **Build > Select Build Variant** → choose `fullDebug` (or `fullRelease` with your keystore). Then install to the phone (Run or `./gradlew :androidApp:installFullDebug`).

2. **Signing**  
   Debug builds are signed with the debug keystore automatically; no extra config needed for development. For release, configure `signingConfigs` in `androidApp/build.gradle.kts` and use `fullRelease`.

3. **Enable Android Auto developer mode (on the phone)**  
   - Open **Settings** → search for **Android Auto** (or **Settings > Apps > Android Auto >** “Configure in Android Auto” / “Additional settings”).  
   - In Android Auto settings, go to **About** and tap **Version** (or “Version and permission info”) **about 10 times** until a toast says you’re a developer.  
   - Open the **⋮** menu → **Developer settings**.

4. **Allow sideloaded apps in the car launcher**  
   In **Developer settings**, turn **Unknown sources** (or “Add new apps”) **ON**. By default, only Play-installed apps are shown; this makes your installed Julius build visible in the car.

5. **Connect and check**  
   Connect the phone to the car (USB or wireless Android Auto). Julius should appear in the car’s app launcher. If it doesn’t, unplug/reconnect or restart Android Auto; some phones (e.g. Samsung) may “sleep” unused apps—open Julius on the phone once and retry.

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

- **`androidApp/src/main/kotlin/fr/geoking/julius/di/AppModule.kt`**: DI configuration, agent wrapper setup
- **`shared/src/commonMain/kotlin/fr/geoking/julius/ConversationStore.kt`**: Core conversation logic
- **`androidApp/src/main/kotlin/fr/geoking/julius/SettingsManager.kt`**: Settings and agent type management
- **`androidApp/src/main/kotlin/fr/geoking/julius/AndroidVoiceManager.kt`**: Platform-specific voice implementation

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
- Application ID: `fr.geoking.julius`
- Package namespace: `fr.geoking.julius` (androidApp), `fr.geoking.julius.shared` (shared)

### Android Auto Specifics
- Service: `VoiceAppService` in `androidApp/src/main/kotlin/fr/geoking/julius/auto/`
- Category: `androidx.car.app.category.MESSAGING`
- Minimum Car API Level: 1
- Requires `automotive_app_desc.xml` resource

### Common Issues & Solutions
- **API keys not working**: Check `local.properties` and app settings. Runtime settings override build-time keys.
- **Android Auto not appearing**: Ensure device/emulator has Android Auto support and app is properly signed
- **Voice not working**: Check `RECORD_AUDIO` permission is granted
- **Agent switching not working**: Verify `DynamicAgentWrapper` is being used and settings are saved
- **"GoogleCertificatesRslt: not allowed" / "Unable to update local snapshot for consentverifier"**: Google Play Services (e.g. Maps) verifies your app's signing certificate. For **debug** builds, add your debug keystore SHA-1 and SHA-256 in [Google Cloud Console](https://console.cloud.google.com/) → your project → **APIs & Services** → **Credentials** → your Android API key → **Application restrictions** → add the fingerprint(s). Get them with `./gradlew signingReport` or from `~/.android/debug.keystore`. This warning is harmless for development; Maps still works. Release builds must use the keystore registered for your Play app.


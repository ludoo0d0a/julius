# Voice agents (`AgentType`)

Runtime selection via Settings; wired in `AppModule.kt` → `DynamicAgentWrapper`. Default: **Gemini**.

Source of truth: `AgentType` enum in `SettingsManager.kt`.

## Remote (cloud) agents

| AgentType | Class | API key(s) | Chat | TTS | STT | Notes |
|-----------|-------|------------|------|-----|-----|-------|
| OpenAI | `OpenAIAgent` | `OPENAI_KEY` | ✅ | Cloud (TTS-1-HD) | ✅ | Premium integrated voice |
| Gemini | `GeminiAgent` | `GEMINI_KEY` | ✅ | System | ❌ | Default; generous free tier |
| ElevenLabs | `ElevenLabsAgent` | `PERPLEXITY_KEY` + `ELEVENLABS_KEY` | ✅ (Perplexity) | Cloud (ElevenLabs) | ✅ | Web-aware chat + premium voice |
| Deepgram | `DeepgramAgent` | `DEEPGRAM_KEY` | Partial | System | ✅ | STT-focused |
| FirebaseAI | `FirebaseAIAgent` | `FIREBASE_AI_KEY` | ✅ | System | ❌ | Model: `FIREBASE_AI_MODEL` |
| OpenCodeZen | `OpenCodeZenAgent` | `OPENCODE_ZEN_KEY` | ✅ | System | ❌ | |
| CompletionsMe | `CompletionsMeAgent` | `COMPLETIONS_ME_KEY` | ✅ | System | ❌ | |
| ApiFreeLLM | `ApiFreeLLMAgent` | `APIFREELLM_KEY` | ✅ | System | ❌ | |
| DeepSeek | `DeepSeekAgent` | `DEEPSEEK_KEY` | ✅ | System | ❌ | |
| Groq | `GroqAgent` | `GROQ_KEY` | ✅ | System | ❌ | Fast inference |
| OpenRouter | `OpenRouterAgent` | `OPENROUTER_KEY` | ✅ | System | ❌ | Many models |

`PerplexityAgent` exists as a library class used **inside** `ElevenLabsAgent`; it is not a separate `AgentType`.

## On-device (embedded) agents

All use GGUF / local inference unless noted. Place models per agent settings (download UI or `androidApp/src/main/assets/models/`).

| AgentType | Backend | Notes |
|-----------|---------|-------|
| Llamatik | `LlamatikAgent` | Primary on-device path (Llamatik / llama.cpp) |
| GeminiNano | Llamatik or LiteRT | Gemma-class default |
| MediaPipe | Llamatik or LiteRT | Phi-2–class label |
| AiEdge | Llamatik or LiteRT | |
| RunAnywhere | Llamatik | |
| MlcLlm | Llamatik | |
| LlamaCpp | Llamatik | |
| PocketPal | Llamatik | |
| Offline | `OfflineAgent` | Minimal offline stub + extended tools |

**Llamatik library version:** see `llamatik-library` in `gradle/libs.versions.toml`.

## Quick picks

| Goal | Suggested agent |
|------|-----------------|
| Dev / free tier | Gemini |
| Best cloud voice | OpenAI or ElevenLabs |
| Web-aware answers | ElevenLabs (Perplexity chat) |
| Privacy / offline | Llamatik, GeminiNano, or Offline |
| Fast cloud inference | Groq |

## Keys

Full key list: [ENV_VARS.md](ENV_VARS.md). Build-time keys seed app settings on first launch; runtime Settings override.

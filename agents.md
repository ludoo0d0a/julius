# Julius - Voice AI Assistant

Julius is a Kotlin Multiplatform (KMP) voice assistant for Android and Android Auto, providing hands-free interaction through various AI services.

## Architecture

- **`:shared`**: Core logic, conversation management (`ConversationStore`), and agent implementations.
- **`:androidApp`**: Jetpack Compose UI (Mobile & Auto), platform-specific voice handling (`AndroidVoiceManager`), and Koin DI.

## Conversational Agents

- **OpenAI**: High-quality chat and integrated TTS using GPT-4o.
- **Gemini**: Cost-effective interaction using Gemini 1.5 Flash and system TTS.
- **Perplexity**: Real-time, web-aware responses with system TTS.
- **ElevenLabs**: Premium voice synthesis combined with Perplexity's web search.
- **Embedded**: Fully offline privacy-focused assistant using on-device LLMs.
- **Deepgram**: Specialized voice processing for low-latency interactions.

## Key Features & Design Patterns

- **Multi-Agent Architecture**: Dynamic runtime switching between AI providers and decentralized model selection.
- **Voice Interaction**: Supports hands-free wake-word detection ("Julius"), barge-in (interruptible speech), and Android Auto integration.
- **Contextual Awareness**: Incorporates user identity and preferences into agent prompts for personalized experiences.
- **Mapping & POIs**: Integrated location-based services with support for multiple swappable data providers.
- **State Management**: Reactive UI driven by a central `ConversationStore` using Kotlin Flows.

## Development

- **Build System**: Standard Gradle-based KMP setup with build-time and runtime API key management.
- **Testing**: Includes mock-based integration tests for agents and platform-specific UI testing.

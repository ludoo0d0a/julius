# Vosk offline STT integration plan (updated)

## STT engine setting

Add a single setting that controls how STT is chosen:

| Value | Meaning | Behavior (car mic path) |
|-------|---------|--------------------------|
| **Local only** | Use only Vosk | Transcriber = local (Vosk) only; no fallback to agent. If Vosk returns null (e.g. not loaded), emit empty / no transcript. |
| **Local first** | Prefer Vosk, then agent | Transcriber = try local (Vosk) first; if null or blank, then `agent.transcribe(audioData)` when `agent.isSttSupported`. |
| **Native only** | No Vosk | Transcriber = only agent when `agent.isSttSupported`; otherwise null. Same as current behavior without Vosk. |

- **Phone path** is unchanged: it uses `SpeechRecognizer`; the transcriber is only used in the **car mic path** (`processCarAudio`).
- **Native only** means “use only the native/agent path” for the transcriber (i.e. no Vosk in the composition). It does not mean “use only Android SpeechRecognizer” on phone (that remains as is).

### Implementation details for the setting

- **Shared**: Define an enum, e.g. `SttEnginePreference` (or `SttEngine`) with values: `LocalOnly`, `LocalFirst`, `NativeOnly`. It can live in shared if you want the UI to reference it from a common type, or in androidApp if the setting is Android-only.
- **Settings**: In [AppSettings](androidApp/src/main/kotlin/fr/geoking/julius/SettingsManager.kt) add e.g. `sttEnginePreference: SttEnginePreference = SttEnginePreference.LocalFirst`, with load/save in SettingsManager (e.g. store as string or ordinal).
- **Composition in ConversationStore**:
  - When building the transcriber in `ConversationStore.init`, the logic depends on the preference. Because the preference is stored in Android (SettingsManager), the **composed transcriber** should be provided by the Android layer so it can read the setting:
    - **Option A**: Pass a single `LocalTranscriber` that internally handles the preference: e.g. `ConfigurableLocalTranscriber(settingsManager, voskTranscriber)` which:
      - For **Native only**: always returns null from `transcribe` (so ConversationStore will use only agent when supported).
      - For **Local only**: calls Vosk and returns its result (and ConversationStore must be changed so it does *not* fall back to agent when local returns null — i.e. “local only” means “use only local result, never agent”).
      - For **Local first**: calls Vosk and returns its result; when null, return null so ConversationStore falls back to agent.
    - **Option B**: ConversationStore receives a **transcriber provider** that returns the full `(ByteArray) -> String?` based on current settings (and optionally agent), so all three modes are implemented in one place (Android). Then ConversationStore just calls `voiceManager.setTranscriber(provider(agent))` and does not implement the composition itself.

  Recommended: **Option A** with a small extension in shared for “local only”:

  - In shared, `ConversationStore` keeps: `localTranscriber: LocalTranscriber` and a flag or enum from the platform: e.g. `sttPreference: SttEnginePreference` (or a simple `Boolean useLocalOnly`). Then composition:
    - If `sttPreference == NativeOnly`: ignore local, use only `if (agent.isSttSupported) agent.transcribe(it) else null`.
    - If `sttPreference == LocalOnly`: use only `localTranscriber.transcribe(it)` (no agent fallback).
    - If `sttPreference == LocalFirst`: use `localTranscriber.transcribe(it) ?: if (agent.isSttSupported) agent.transcribe(it) else null`.

  So shared needs to know the enum; the enum can live in shared (e.g. in a small `SttEnginePreference.kt`) and the value is passed from Android when creating ConversationStore. That requires ConversationStore to take `sttEnginePreference: SttEnginePreference` (from shared) and to receive `localTranscriber: LocalTranscriber`. The Android app then passes `settingsManager.settings.value.sttEnginePreference` (and a snapshot at init time). If the user changes the setting at runtime, you either need to re-set the transcriber (e.g. ConversationStore exposes `updateTranscriber(preference, localTranscriber)` and the settings screen calls it when preference changes) or the transcriber wrapper reads the setting every time (so no re-set needed). Prefer “read setting every time” in the wrapper to avoid lifecycle issues: e.g. `ConfigurableLocalTranscriber` already has access to SettingsManager; and for the mode, either pass a lambda `() -> SttEnginePreference` or have ConversationStore’s transcriber be a wrapper that gets the current preference and delegates accordingly. Simplest: **ConversationStore** takes `localTranscriber: LocalTranscriber` and `sttPreference: () -> SttEnginePreference` (provider). In init it sets:

  `voiceManager.setTranscriber { audio ->
    when (sttPreference()) {
      NativeOnly -> if (agent.isSttSupported) agent.transcribe(audio) else null
      LocalOnly -> localTranscriber.transcribe(audio)
      LocalFirst -> localTranscriber.transcribe(audio) ?: if (agent.isSttSupported) agent.transcribe(audio) else null
    }
  }`

  So the enum lives in shared, and Android passes `sttPreference = { get<SettingsManager>().settings.value.sttEnginePreference }`. That way the setting is read at each transcription and no re-set is needed.

- **UI**: Add a dropdown or radio group in settings (and in Android Auto settings if desired) for “STT engine” with the three options: “Local only (Vosk)”, “Local first (Vosk, then cloud)”, “Native only (cloud / agent only)”.

## Rest of the plan (unchanged)

- **Shared**: `LocalTranscriber` interface + `NoLocalTranscriber`; ConversationStore composes with `localTranscriber` and `sttPreference()` as above.
- **Android**: Vosk dependency, model in assets, `VoskTranscriber` implementing `LocalTranscriber`, audio format 16 kHz 16-bit mono (convert if needed for CarAudioRecord).
- **DI**: Register `VoskTranscriber`, pass `localTranscriber` and `sttPreference` into ConversationStore.
- **Docs**: Update VOICE_PROCESSING.md and AGENTS.md to describe the STT engine options and Vosk.

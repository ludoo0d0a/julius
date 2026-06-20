# Android Auto

## Debug with DHU

See [ANDROID_AUTO_DHU_DEBUG.md](ANDROID_AUTO_DHU_DEBUG.md) and `./scripts/debug-play-dhu.sh`.

## Physical car (sideloaded builds)

Play Store builds appear automatically; debug/sideloaded builds need developer mode:

1. Install debug or release: `./gradlew :androidApp:installDebug`
2. On phone: **Settings → Android Auto → About → tap Version ~10×** → Developer settings
3. Enable **Unknown sources** / **Add new apps**
4. Connect USB or wireless AA; open Julius on phone once if the launcher hides it
5. In Julius Auto settings: enable **Use car microphone** for car STT path

## Car App Library constraints

- **PaneTemplate:** `Row.setOnClickListener` inside a `Pane` crashes — use `ListTemplate`, or pane/header `Action`s (max 2 on pane).
- **MapWithContentTemplate:** Top-level `ActionStrip` allows **one** action when using surface rendering.
- **Headers (1.7+):** Prefer `Header.Builder.addEndHeaderAction` / `setStartHeaderAction`; avoid deprecated `setActionStrip` on template builders.

Service entry: `VoiceAppService` · category `androidx.car.app.category.NAVIGATION` · min car API 1.

## Offline STT in car

Settings → **STT engine (car)**: Local only / Local first. Requires Vosk model at  
`androidApp/src/main/assets/models/vosk/<model-name>/`  
(e.g. [vosk-model-small-en-us-0.15](https://alphacephei.com/vosk/models)).

Details: [VOICE_PROCESSING.md](VOICE_PROCESSING.md).

# Environment variables and local.properties

The same key name is used in **local.properties** and as an **environment variable**. Example: `OPENAI_KEY` in both places.

## SDK path (required in CI)

The Android Gradle Plugin reads `sdk.dir` from `local.properties` (no env alias). In CI, create the file before running Gradle:

```bash
echo "sdk.dir=$ANDROID_HOME" >> local.properties
```

## Keys (local.properties and env)

Use these names in `local.properties` or set the same name as an env var (e.g. for CI/GitHub Secrets).

| Key | Usage |
|-----|--------|
| `VERSION_CODE` | Optional integer override for versionCode (e.g. `123`). |
| `ELEVENLABS_KEY` | ElevenLabs API key. |
| `GEMINI_KEY` | Google Gemini API key. |
| `DEEPGRAM_KEY` | Deepgram API key. |
| `OPENAI_KEY` | OpenAI API key. |
| `PERPLEXITY_KEY` | Perplexity API key. |
| `FIREBASE_AI_KEY` | Firebase AI API key. |
| `FIREBASE_AI_MODEL` | Model name (default: `gemini-1.5-flash-latest`). |
| `OPENCODE_ZEN_KEY` | OpenCode Zen API key. |
| `COMPLETIONS_ME_KEY` | Completions.me API key. |
| `APIFREELLM_KEY` | ApiFreeLLM API key. |
| `JULES_KEY` | Jules (jules.google.com) API key for the Jules screen. |
| `GOOGLE_MAPS_KEY` | Google Maps API key. **Required for map screen** (tiles); without it the map stays grey. |

Keys are read at **build time** in this order: `local.properties` then environment variables. In CI, set env vars on the step that runs Gradle (e.g. `env:` in the build job). Build-time values are baked into the app and, on first run, persisted into app settings so they appear in Settings and are reused.

## Example local.properties

```properties
sdk.dir=/path/to/your/android/sdk
OPENAI_KEY=sk-...
GEMINI_KEY=...
GOOGLE_MAPS_KEY=...
```

## GitHub Actions example

Set **both** `JULES_KEY` and `GOOGLE_MAPS_KEY` in CI if you use the Jules screen and the map; otherwise the Jules key will appear empty in settings and the map will show no tiles (grey with Google logo).

```yaml
- name: Set up local.properties for SDK
  run: echo "sdk.dir=$ANDROID_HOME" >> local.properties

- name: Build
  env:
    VERSION_CODE: ${{ secrets.VERSION_CODE }}
    ELEVENLABS_KEY: ${{ secrets.ELEVENLABS_KEY }}
    GEMINI_KEY: ${{ secrets.GEMINI_KEY }}
    DEEPGRAM_KEY: ${{ secrets.DEEPGRAM_KEY }}
    OPENAI_KEY: ${{ secrets.OPENAI_KEY }}
    PERPLEXITY_KEY: ${{ secrets.PERPLEXITY_KEY }}
    FIREBASE_AI_KEY: ${{ secrets.FIREBASE_AI_KEY }}
    FIREBASE_AI_MODEL: ${{ secrets.FIREBASE_AI_MODEL }}
    OPENCODE_ZEN_KEY: ${{ secrets.OPENCODE_ZEN_KEY }}
    COMPLETIONS_ME_KEY: ${{ secrets.COMPLETIONS_ME_KEY }}
    APIFREELLM_KEY: ${{ secrets.APIFREELLM_KEY }}
    JULES_KEY: ${{ secrets.JULES_KEY }}
    GOOGLE_MAPS_KEY: ${{ secrets.GOOGLE_MAPS_KEY }}
  run: ./gradlew :androidApp:assembleFullRelease
```

Create the secrets in **Settings → Secrets and variables → Actions** and add only the keys your build needs.

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
| `GOOGLE_MAPS_KEY` | Google Maps API key. |

## Example local.properties

```properties
sdk.dir=/path/to/your/android/sdk
OPENAI_KEY=sk-...
GEMINI_KEY=...
GOOGLE_MAPS_KEY=...
```

## GitHub Actions example

```yaml
- name: Set up local.properties for SDK
  run: echo "sdk.dir=$ANDROID_HOME" >> local.properties

- name: Build
  env:
    ELEVENLABS_KEY: ${{ secrets.ELEVENLABS_KEY }}
    GEMINI_KEY: ${{ secrets.GEMINI_KEY }}
    OPENAI_KEY: ${{ secrets.OPENAI_KEY }}
    PERPLEXITY_KEY: ${{ secrets.PERPLEXITY_KEY }}
    GOOGLE_MAPS_KEY: ${{ secrets.GOOGLE_MAPS_KEY }}
    # Add only the secrets you use
  run: ./gradlew :androidApp:assembleFullRelease
```

Create the secrets in **Settings → Secrets and variables → Actions** and add only the keys your build needs.

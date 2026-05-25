package fr.geoking.julius.feature.voice

enum class VoskModelVariant(
    val displayName: String,
    val sizeDescription: String,
    val approxSizeBytes: Long,
    val downloadUrl: String
) {
    English(
        displayName = "English (Small)",
        sizeDescription = "~40 MB",
        approxSizeBytes = 40_000_000L,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    ),
    French(
        displayName = "French (Small)",
        sizeDescription = "~45 MB",
        approxSizeBytes = 45_000_000L,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
    ),
    German(
        displayName = "German (Small)",
        sizeDescription = "~45 MB",
        approxSizeBytes = 45_000_000L,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
    ),
    Spanish(
        displayName = "Spanish (Small)",
        sizeDescription = "~40 MB",
        approxSizeBytes = 40_000_000L,
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    )
}

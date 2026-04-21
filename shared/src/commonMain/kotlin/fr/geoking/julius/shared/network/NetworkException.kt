package fr.geoking.julius.shared.network

class NetworkException(
    val httpCode: Int?,
    message: String,
    val url: String? = null,
    val provider: String? = null
) : Exception(buildMessage(httpCode, message, url, provider)) {
    companion object {
        private fun buildMessage(httpCode: Int?, message: String, url: String?, provider: String?): String {
            return buildString {
                if (provider != null) append("[$provider] ")
                append(message)
                if (httpCode != null) append(" (HTTP $httpCode)")
                if (url != null) append("\nURL: $url")
            }
        }
    }
}

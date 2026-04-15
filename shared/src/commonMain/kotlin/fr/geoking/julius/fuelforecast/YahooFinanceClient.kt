package fr.geoking.julius.fuelforecast

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DailyClose(val day: String, val close: Double)

@Serializable
private data class YahooChartResponse(
    val chart: YahooChartData
)

@Serializable
private data class YahooChartData(
    val result: List<YahooChartResult>? = null,
    val error: YahooChartError? = null
)

@Serializable
private data class YahooChartResult(
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators
)

@Serializable
private data class YahooIndicators(
    val quote: List<YahooQuote>
)

@Serializable
private data class YahooQuote(
    val close: List<Double?>
)

@Serializable
private data class YahooChartError(
    val code: String,
    val description: String
)

/**
 * Daily market data from Yahoo Finance v8 chart API.
 * Replaces Stooq as it now requires an API key and captcha.
 */
class YahooFinanceClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://query1.finance.yahoo.com/v8/finance/chart/"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches daily closes for a given symbol.
     * @param symbol Yahoo Finance symbol (e.g., "BZ=F", "HO=F", "EURUSD=X")
     * @param range range to fetch (e.g., "1mo", "3mo", "1y")
     */
    suspend fun fetchDailyCloses(symbol: String, range: String = "1mo"): List<DailyClose> {
        val url = "$baseUrl$symbol?interval=1d&range=$range"
        val response = try {
            http.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            }
        } catch (e: Exception) {
            return emptyList()
        }

        if (response.status.value != 200) return emptyList()

        val body = response.bodyAsText()
        val parsed = try {
            json.decodeFromString<YahooChartResponse>(body)
        } catch (e: Exception) {
            return emptyList()
        }

        val result = parsed.chart.result?.firstOrNull() ?: return emptyList()
        val timestamps = result.timestamp ?: return emptyList()
        val closes = result.indicators.quote.firstOrNull()?.close ?: return emptyList()

        val out = mutableListOf<DailyClose>()
        for (i in timestamps.indices) {
            val ts = timestamps[i]
            val close = closes.getOrNull(i) ?: continue
            if (close > 0) {
                out.add(DailyClose(day = formatUnixTimestamp(ts), close = close))
            }
        }
        return out
    }

    /**
     * Converts Unix timestamp (seconds) to YYYY-MM-DD string.
     * Uses Howard Hinnant's algorithm for UTC.
     */
    private fun formatUnixTimestamp(ts: Long): String {
        val secondsInDay = 86400L
        val days = ts / secondsInDay

        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + (if (mp < 10) 3 else -9)
        val year = y + (if (m <= 2) 1 else 0)

        return "${year.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }
}

object YahooSymbols {
    /** Brent (USD/bbl) on Yahoo Finance. */
    const val BRENT = "BZ=F"

    /** NYMEX heating oil (USD/gal). */
    const val HEATING_OIL = "HO=F"

    /** EUR/USD spot. */
    const val EURUSD = "EURUSD=X"
}

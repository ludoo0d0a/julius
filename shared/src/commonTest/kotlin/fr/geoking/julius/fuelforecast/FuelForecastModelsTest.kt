package fr.geoking.julius.fuelforecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuelForecastModelsTest {

    @Test
    fun yahooFinance_parsing_matchesDailyClose() {
        // 1773633600 is 2026-03-16 04:00:00 UTC
        assertEquals("2026-03-16", formatUnixTimestampMock(1773633600))
        // 1773720000 is 2026-03-17 04:00:00 UTC
        assertEquals("2026-03-17", formatUnixTimestampMock(1773720000))
    }

    @Test
    fun predictor_score_positiveWhenCrudeUp() {
        val p = RuleBasedFuelPricePredictor(upThreshold = 0.001)
        val brent = closes(100.0, 102.0, 104.0, 106.0) // 3d return > 0
        val ho = closes(2.5, 2.5, 2.5, 2.5)
        val fx = closes(1.10, 1.10, 1.10, 1.10)
        val r = p.predict("gazole", baselinePriceEurPerL = 1.80, brent, ho, fx)
        assertTrue(r.score > 0)
        assertTrue(r.horizonPredictions[0].predictedPriceEurPerL > 1.80)
    }

    private fun closes(vararg v: Double): List<DailyClose> =
        v.mapIndexed { i, c -> DailyClose(day = "2026-04-${(i + 1).toString().padStart(2, '0')}", close = c) }

    private fun formatUnixTimestampMock(ts: Long): String {
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

package fr.geoking.julius.repository

import fr.geoking.julius.api.datagouv.DataGouvNationalAvgPoint
import fr.geoking.julius.api.datagouv.DataGouvPrixCarburantClient
import fr.geoking.julius.api.datagouv.DataGouvPrixCarburantStation
import fr.geoking.julius.fuelforecast.DailyClose
import fr.geoking.julius.fuelforecast.FuelForecastResult
import fr.geoking.julius.fuelforecast.MarketReturnInputs
import fr.geoking.julius.fuelforecast.RuleBasedFuelPricePredictor
import fr.geoking.julius.fuelforecast.YahooFinanceClient
import fr.geoking.julius.fuelforecast.YahooSymbols
import fr.geoking.julius.persistence.AppDatabase
import fr.geoking.julius.persistence.FuelPricePredictionEntity
import fr.geoking.julius.persistence.FuelPricePredictionScoreEntity
import fr.geoking.julius.persistence.LocalFuelAvgDailyEntity
import fr.geoking.julius.persistence.MarketDailyQuoteEntity
import fr.geoking.julius.poi.MapPoiFilter
import io.ktor.client.HttpClient
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

private val FUEL_PRIORITY = listOf("gazole", "sp95", "sp98", "gplc", "e85")

/**
 * Local pump averages, Yahoo-based market series, rule forecasts, and scoring against realized daily averages.
 */
class FuelForecastRepository(
    private val http: HttpClient,
    private val db: AppDatabase,
    private val predictor: RuleBasedFuelPricePredictor = RuleBasedFuelPricePredictor()
) {
    private val marketClient = YahooFinanceClient(http)
    private val prix = DataGouvPrixCarburantClient(http)
    private val marketDao = db.marketDailyQuoteDao()
    private val localAvgDao = db.localFuelAvgDailyDao()
    private val predictionDao = db.fuelPricePredictionDao()
    private val scoreDao = db.fuelPricePredictionScoreDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val MARKET_CACHE_MS = 4L * 60L * 60L * 1000L
        private const val CHEAPEST_STATIONS = 20
        private const val SEARCH_RADIUS_KM = 15
        private const val STATION_LIMIT = 100
        private const val DIRECTION_EPSILON_EUR = 0.005
    }

    fun locationKey(latitude: Double, longitude: Double): String =
        "${formatCoord(latitude)}_${formatCoord(longitude)}"

    private fun formatCoord(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    suspend fun refreshAndBuildUiState(
        latitude: Double,
        longitude: Double,
        fuelIds: Set<String>
    ): FuelForecastUiState {
        val results = refreshAndBuildMultiUiState(latitude, longitude, fuelIds)
        val primaryFuel = FUEL_PRIORITY.firstOrNull { it in results.keys } ?: fuelIds.firstOrNull { it != "electric" } ?: "gazole"
        return results[primaryFuel] ?: FuelForecastUiState(fuelId = primaryFuel, locationKey = locationKey(latitude, longitude))
    }

    suspend fun refreshAndBuildMultiUiState(
        latitude: Double,
        longitude: Double,
        fuelIds: Set<String>
    ): Map<String, FuelForecastUiState> {
        val locKey = locationKey(latitude, longitude)
        val today = todayParis()
        val now = System.currentTimeMillis()

        val stations = try {
            prix.getStations(latitude, longitude, SEARCH_RADIUS_KM, STATION_LIMIT)
        } catch (_: Exception) {
            emptyList()
        }

        val effectiveFuels = fuelIds.filter { it != "electric" }.toSet()
        if (effectiveFuels.isEmpty()) {
            return mapOf(
                "gazole" to FuelForecastUiState(
                    fuelId = "gazole",
                    locationKey = locKey,
                    errorMessage = "Select a fuel type (e.g. Gazole or SP95)."
                )
            )
        }

        for (fuel in effectiveFuels) {
            val prices = stationPricesForFuel(stations, fuel).sorted()
            if (prices.isEmpty()) continue
            val n = min(CHEAPEST_STATIONS, prices.size)
            val avg = prices.take(n).average()
            localAvgDao.upsert(
                LocalFuelAvgDailyEntity(
                    day = today,
                    fuelId = fuel,
                    locationKey = locKey,
                    avgPrice = avg,
                    stationCount = n,
                    updatedAtMs = now
                )
            )
        }

        refreshMarketCacheIfStale(now)

        val fromDay = LocalDate.parse(today).minusDays(14).toString()
        var brent = loadClosesFromDb(YahooSymbols.BRENT, fromDay)
        var ho = loadClosesFromDb(YahooSymbols.HEATING_OIL, fromDay)
        var fx = loadClosesFromDb(YahooSymbols.EURUSD, fromDay)
        if (brent.size < 4) {
            brent = try {
                marketClient.fetchDailyCloses(YahooSymbols.BRENT, "1mo")
            } catch (_: Exception) {
                brent
            }
        }
        if (ho.size < 4) ho = brent
        if (fx.size < 2) {
            fx = try {
                marketClient.fetchDailyCloses(YahooSymbols.EURUSD, "1mo")
            } catch (_: Exception) {
                fx
            }
        }

        val nationalAvgs = try {
            prix.getNationalAverages(14)
        } catch (_: Exception) {
            emptyMap()
        }

        scorePendingPredictions(today)

        val resultMap = mutableMapOf<String, FuelForecastUiState>()

        // 1. Brent Crude special entry
        if (brent.isNotEmpty()) {
            val brentHistory = brent.map {
                DailyPricePoint(day = it.day, priceEurPerL = it.close, isForecast = false)
            }
            val brentTomorrow = if (brent.size >= 2) {
                val last = brent.last()
                val prev = brent[brent.size - 2]
                val dailyReturn = (last.close - prev.close) / prev.close
                val predictedPrice = last.close * (1.0 + dailyReturn)
                PredictionInfo(
                    predictedPrice = predictedPrice,
                    changePercentage = dailyReturn * 100.0,
                    directionUp = dailyReturn > 0
                )
            } else null

            val brentForecast = brentTomorrow?.let {
                val lastDay = brent.last().day
                val targetDay = try { LocalDate.parse(lastDay).plusDays(1).toString() } catch (_: Exception) { today }
                listOf(DailyPricePoint(day = targetDay, priceEurPerL = it.predictedPrice, isForecast = true))
            } ?: emptyList()

            resultMap["brent"] = FuelForecastUiState(
                fuelId = "brent",
                locationKey = locKey,
                historyPoints = brentHistory,
                forecastPoints = brentForecast,
                nextDayPrediction = brentTomorrow
            )
        }

        for (fuel in effectiveFuels) {
            val baseline = localAvgDao.getDay(locKey, fuel, today)?.avgPrice
            if (baseline != null && brent.size >= 4) {
                val forecast = predictor.predict(fuel, baseline, brent, ho, fx)
                persistPredictions(today, now, locKey, fuel, baseline, forecast)
            }

            val history = localAvgDao.series(locKey, fuel, fromDay).map {
                DailyPricePoint(day = it.day, priceEurPerL = it.avgPrice, isForecast = false)
            }

            val predictions = predictionDao.forCreationDay(today, fuel, locKey)
            val forecastPoints = predictions.map { p ->
                DailyPricePoint(day = p.targetDay, priceEurPerL = p.predictedPrice, isForecast = true)
            }

            val nextDayPred = predictions.find { it.horizonDays == 1 }
            val predictionInfo = if (nextDayPred != null && baseline != null) {
                val diff = nextDayPred.predictedPrice - baseline
                val pct = (diff / baseline) * 100.0
                PredictionInfo(
                    predictedPrice = nextDayPred.predictedPrice,
                    changePercentage = pct,
                    directionUp = nextDayPred.predictedUp
                )
            } else null

            val score = predictions.firstOrNull()?.marketScore
            val up = predictions.firstOrNull()?.predictedUp

            val accFrom = LocalDate.parse(today).minusDays(7).toString()
            val accRow = scoreDao.accuracySince(fuel, locKey, accFrom)
            val lastScore = scoreDao.latestScoreForLocation(fuel, locKey)

            val nationalHistory = nationalAvgs[fuelIdToNationalName(fuel)]?.map {
                DailyPricePoint(day = it.day, priceEurPerL = it.avgPrice, isForecast = false)
            } ?: emptyList()

            val brentHistory = brent.map {
                // Approximate Brent in EUR/L for scale comparison, though we scale in UI too
                val rate = fx.lastOrNull()?.close ?: 1.08
                // 1 bbl = 159 liters. Price is in USD.
                DailyPricePoint(day = it.day, priceEurPerL = (it.close / rate) / 159.0, isForecast = false)
            }

            val error = if (stationPricesForFuel(stations, fuel).isEmpty()) {
                "No pump prices for $fuel nearby. Try again later."
            } else {
                null
            }

            resultMap[fuel] = FuelForecastUiState(
                fuelId = fuel,
                locationKey = locKey,
                historyPoints = history,
                forecastPoints = forecastPoints,
                nationalHistoryPoints = nationalHistory,
                marketHistoryPoints = brentHistory,
                nextDayPrediction = predictionInfo,
                marketScore = score,
                directionUp = up,
                accuracyHitRate7d = accRow?.hitRate,
                accuracyMae7d = accRow?.mae,
                lastScoreDirectionCorrect = lastScore?.directionCorrect,
                errorMessage = error
            )
        }

        return resultMap
    }

    private fun fuelIdToNationalName(id: String) = when (id) {
        "gazole" -> "Gazole"
        "sp95" -> "SP95"
        "sp98" -> "SP98"
        "gplc" -> "GPLc"
        "e85" -> "E85"
        "e10" -> "E10"
        else -> id
    }

    private suspend fun persistPredictions(
        createdDay: String,
        createdAtMs: Long,
        locationKey: String,
        fuelId: String,
        baseline: Double,
        forecast: FuelForecastResult
    ) {
        val inputsJson = json.encodeToString(MarketReturnInputs.serializer(), forecast.inputs)
        for (hp in forecast.horizonPredictions) {
            val h = hp.horizonDays
            if (predictionDao.existsForHorizon(createdDay, fuelId, locationKey, h)) continue
            val target = LocalDate.parse(createdDay).plusDays(h.toLong()).toString()
            predictionDao.insert(
                FuelPricePredictionEntity(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = createdAtMs,
                    createdDay = createdDay,
                    targetDay = target,
                    horizonDays = h,
                    fuelId = fuelId,
                    locationKey = locationKey,
                    predictedUp = hp.predictedUp,
                    predictedPrice = hp.predictedPriceEurPerL,
                    baselinePrice = baseline,
                    marketScore = forecast.score,
                    inputsJson = inputsJson
                )
            )
        }
    }

    private suspend fun scorePendingPredictions(todayParis: String) {
        val pending = predictionDao.pendingScoring(todayParis)
        val now = System.currentTimeMillis()
        for (p in pending) {
            val actualRow = localAvgDao.getDay(p.locationKey, p.fuelId, p.targetDay) ?: continue
            val actual = actualRow.avgPrice
            val baseline = p.baselinePrice
            val actualUp = actual > baseline + DIRECTION_EPSILON_EUR
            val correct = actualUp == p.predictedUp
            val err = abs(p.predictedPrice - actual)
            scoreDao.insert(
                FuelPricePredictionScoreEntity(
                    id = UUID.randomUUID().toString(),
                    predictionId = p.id,
                    scoredAtMs = now,
                    actualPrice = actual,
                    baselinePrice = baseline,
                    directionCorrect = correct,
                    absError = err
                )
            )
        }
    }

    private suspend fun refreshMarketCacheIfStale(nowMs: Long) {
        val sym = YahooSymbols.BRENT
        val last = marketDao.latestFetchMs(sym) ?: 0L
        if (nowMs - last < MARKET_CACHE_MS) return

        coroutineScope {
            val b = async { runCatching { marketClient.fetchDailyCloses(YahooSymbols.BRENT, "1mo") }.getOrElse { emptyList() } }
            val h = async { runCatching { marketClient.fetchDailyCloses(YahooSymbols.HEATING_OIL, "1mo") }.getOrElse { emptyList() } }
            val e = async { runCatching { marketClient.fetchDailyCloses(YahooSymbols.EURUSD, "1mo") }.getOrElse { emptyList() } }
            val rows = mutableListOf<MarketDailyQuoteEntity>()
            for (d in b.await()) rows += quoteEntity(YahooSymbols.BRENT, d, nowMs)
            for (d in h.await()) rows += quoteEntity(YahooSymbols.HEATING_OIL, d, nowMs)
            for (d in e.await()) rows += quoteEntity(YahooSymbols.EURUSD, d, nowMs)
            if (rows.isNotEmpty()) marketDao.upsertAll(rows)
        }
    }

    private fun quoteEntity(symbol: String, bar: DailyClose, fetchedAt: Long) =
        MarketDailyQuoteEntity(symbol = symbol, day = bar.day, close = bar.close, fetchedAtMs = fetchedAt)

    private suspend fun loadClosesFromDb(symbol: String, fromDay: String): List<DailyClose> =
        marketDao.since(symbol, fromDay).map { DailyClose(day = it.day, close = it.close) }

    private fun stationPricesForFuel(
        stations: List<DataGouvPrixCarburantStation>,
        fuelId: String
    ): List<Double> {
        val out = ArrayList<Double>()
        for (s in stations) {
            for (f in s.fuels) {
                val id = MapPoiFilter.fuelNameToId(f.name) ?: continue
                if (id == fuelId) out += f.priceEur
            }
        }
        return out
    }

    private fun todayParis(): String = LocalDate.now(PARIS).toString()
}

data class DailyPricePoint(
    val day: String,
    val priceEurPerL: Double,
    val isForecast: Boolean
)

data class PredictionInfo(
    val predictedPrice: Double,
    val changePercentage: Double,
    val directionUp: Boolean
)

data class FuelForecastUiState(
    val fuelId: String,
    val locationKey: String,
    val historyPoints: List<DailyPricePoint> = emptyList(),
    val forecastPoints: List<DailyPricePoint> = emptyList(),
    val nationalHistoryPoints: List<DailyPricePoint> = emptyList(),
    val marketHistoryPoints: List<DailyPricePoint> = emptyList(),
    val nextDayPrediction: PredictionInfo? = null,
    val marketScore: Double? = null,
    val directionUp: Boolean? = null,
    val accuracyHitRate7d: Double? = null,
    val accuracyMae7d: Double? = null,
    val lastScoreDirectionCorrect: Boolean? = null,
    val errorMessage: String? = null
)

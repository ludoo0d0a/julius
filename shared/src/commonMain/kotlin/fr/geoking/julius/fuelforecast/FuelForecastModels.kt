package fr.geoking.julius.fuelforecast

data class DailyClose(
    val day: String,
    val close: Double
)

data class HorizonPrediction(
    val horizonDays: Int,
    val predictedPriceEurPerL: Double
)

data class FuelPricePrediction(
    val score: Double,
    val horizonPredictions: List<HorizonPrediction>
)

class RuleBasedFuelPricePredictor(
    private val upThreshold: Double = 0.01
) {
    fun predict(
        fuelName: String,
        baselinePriceEurPerL: Double,
        brent: List<DailyClose>,
        heatingOil: List<DailyClose>,
        fxEurUsd: List<DailyClose>
    ): FuelPricePrediction {
        val score = crudeScore(brent)
        val bump = baselinePriceEurPerL * score * 0.05
        val predicted = baselinePriceEurPerL + bump

        return FuelPricePrediction(
            score = score,
            horizonPredictions = listOf(
                HorizonPrediction(horizonDays = 3, predictedPriceEurPerL = predicted)
            )
        )
    }

    private fun crudeScore(series: List<DailyClose>): Double {
        if (series.size < 2) return 0.0
        val first = series.first().close
        val last = series.last().close
        if (first == 0.0) return 0.0
        val ret = (last - first) / first
        return when {
            ret > upThreshold -> 1.0
            ret < -upThreshold -> -1.0
            else -> 0.0
        }
    }
}


package fr.geoking.julius.persistence

/** Room aggregate row for [FuelPricePredictionScoreDao.accuracySince]. */
data class ForecastAccuracyStats(
    val hitRate: Double?,
    val mae: Double?
)

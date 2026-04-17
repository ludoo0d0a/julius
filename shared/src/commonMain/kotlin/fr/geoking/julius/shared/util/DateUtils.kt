package fr.geoking.julius.shared.util

import io.ktor.util.date.GMTDate

/**
 * Basic KMP date utilities using Ktor's GMTDate.
 */
object DateUtils {
    /**
     * Formats current time as ISO 8601 string: yyyy-MM-ddTHH:mm:ssZ
     */
    fun formatIsoNow(): String {
        return formatIso(GMTDate())
    }

    /**
     * Formats GMTDate as ISO 8601 string: yyyy-MM-ddTHH:mm:ssZ
     */
    fun formatIso(date: GMTDate): String {
        val year = date.year
        val month = (date.month.ordinal + 1).toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val hour = date.hours.toString().padStart(2, '0')
        val minute = date.minutes.toString().padStart(2, '0')
        val second = date.seconds.toString().padStart(2, '0')
        return "$year-$month-${day}T$hour:$minute:${second}Z"
    }
}

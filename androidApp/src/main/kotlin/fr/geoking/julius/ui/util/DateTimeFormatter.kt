package fr.geoking.julius.ui.util

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter as JDateTimeFormatter
import java.time.format.DateTimeParseException

object DateTimeFormatter {
    private val dateFormatter = JDateTimeFormatter.ofPattern("MMM d, yyyy")

    fun formatRelative(isoString: String?): String? {
        if (isoString.isNullOrBlank()) return null
        try {
            val odt = OffsetDateTime.parse(isoString)
            val instant = odt.toInstant()
            val now = Instant.now()
            val duration = Duration.between(instant, now)

            return when {
                duration.isNegative -> "just now"
                duration.toMinutes() < 1 -> "just now"
                duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
                duration.toHours() < 24 -> "${duration.toHours()}h ago"
                duration.toDays() < 7 -> "${duration.toDays()}d ago"
                else -> odt.format(dateFormatter)
            }
        } catch (e: DateTimeParseException) {
            return isoString
        }
    }
}

package fr.geoking.julius.ui.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.geoking.julius.poi.Poi

object NavigationHelper {
    fun navigateToPoi(context: Context, poi: Poi) {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
        val streetAddress = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.takeIf { it.isNotBlank() }
        val locationSummary = buildList {
            listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
            poi.countryLocal?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(", ").takeIf { it.isNotBlank() }

        val fullAddress = buildList {
            if (!streetAddress.isNullOrBlank()) add(streetAddress)
            if (!locationSummary.isNullOrBlank() && locationSummary != streetAddress) add(locationSummary)
        }.joinToString(", ")

        val query = buildList {
            if (title.isNotBlank()) add(title)
            if (fullAddress.isNotBlank()) add(fullAddress)
        }.joinToString(", ")

        val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }
}

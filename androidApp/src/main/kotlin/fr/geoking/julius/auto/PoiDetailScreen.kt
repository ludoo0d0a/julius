package fr.geoking.julius.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.R
import fr.geoking.julius.providers.Poi

/**
 * Android Auto screen showing full POI details and a "Go to this station" action
 * that starts navigation (e.g. to Android Auto driving app).
 */
class PoiDetailScreen(
    carContext: CarContext,
    private val poi: Poi
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
        val body = buildDetailMessage(poi)
        val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
            data = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(title)}")
        }
        return MessageTemplate.Builder(body)
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Navigate to")
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_poi_gas)
                                ).build()
                            )
                            .setOnClickListener {
                                carContext.startCarApp(navigateIntent)
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildDetailMessage(poi: Poi): String {
        val lines = mutableListOf<String>()
        poi.brand?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        poi.addressLocal?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { lines.add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        if (lines.isEmpty() && poi.address.isNotBlank()) lines.add(poi.address)
        poi.fuelPrices?.let { prices ->
            if (prices.isNotEmpty()) {
                lines.add("")
                prices.forEach { fp ->
                    val priceStr = if (fp.outOfStock) "—" else "€%.3f".format(fp.price)
                    val updated = fp.updatedAt?.let { " ($it)" } ?: ""
                    lines.add("${fp.fuelName}: $priceStr$updated")
                }
            }
        }
        return lines.joinToString("\n").ifBlank { "No extra details" }
    }
}

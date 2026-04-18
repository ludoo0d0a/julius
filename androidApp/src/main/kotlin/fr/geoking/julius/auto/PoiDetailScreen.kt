package fr.geoking.julius.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.R
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.api.belib.StationAvailabilitySummary

/**
 * Android Auto screen showing full POI details and a "Go to this station" action
 * that starts navigation (e.g. to Android Auto driving app).
 */
class PoiDetailScreen(
    carContext: CarContext,
    private val poi: Poi,
    private val availabilitySummary: StationAvailabilitySummary? = null,
    private val rating: Int? = null,
    isFavorite: Boolean = false,
    private val onToggleFavorite: (() -> Unit)? = null
) : Screen(carContext) {

    private var isFavorite: Boolean = isFavorite

    override fun onGetTemplate(): Template {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
        val body = buildDetailMessage(poi)
        val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
            data = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(title)}")
        }
        val navigateAction = Action.Builder()
            .setTitle("Navigate")
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_poi_gas)
                ).build()
            )
            .setOnClickListener {
                carContext.startCarApp(navigateIntent)
            }
            .build()

        val headerBuilder = Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)

        if (onToggleFavorite != null) {
            headerBuilder.addEndHeaderAction(
                Action.Builder()
                    .setTitle(if (isFavorite) "Saved" else "Save")
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                if (isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
                            )
                        ).build()
                    )
                    .setOnClickListener {
                        onToggleFavorite.invoke()
                        this@PoiDetailScreen.isFavorite = !this@PoiDetailScreen.isFavorite
                        invalidate()
                    }
                    .build()
            )
        }

        return MessageTemplate.Builder(body)
            .setHeader(headerBuilder.build())
            .addAction(navigateAction)
            .build()
    }

    private fun buildDetailMessage(poi: Poi): String {
        val lines = mutableListOf<String>()
        poi.brand?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        if (poi.isElectric) {
            poi.operator?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
            if (poi.isOnHighway) lines.add("Autoroute")
            poi.chargePointCount?.let { n ->
                lines.add(if (n == 1) "1 point de charge" else "$n points de charge")
            }
            availabilitySummary?.let { s ->
                lines.add("${s.availableCount} / ${s.totalCount} disponibles")
            }
        }
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
        poi.irveDetails?.let { d ->
            if (d.connectorTypes.isNotEmpty()) {
                val connectorLabels = d.connectorTypes.sorted().map { connectorLabel(it) }.joinToString(", ")
                lines.add("Connecteurs: $connectorLabels")
            }
            if (d.gratuit == true) lines.add("Gratuit")
            d.tarification?.takeIf { it.isNotBlank() }?.let { lines.add("Tarification: $it") }
            d.openingHours?.takeIf { it.isNotBlank() }?.let { lines.add("Horaires: $it") }
            if (d.reservation == true) lines.add("Réservation possible")
            listOfNotNull(
                if (d.paymentActe == true) "À l'acte" else null,
                if (d.paymentCb == true) "CB" else null,
                if (d.paymentAutre == true) "Autre" else null
            ).joinToString(", ").takeIf { it.isNotBlank() }?.let { lines.add("Paiement: $it") }
            d.conditionAcces?.takeIf { it.isNotBlank() }?.let { lines.add("Accès: $it") }
        }
        poi.restaurantDetails?.let { d ->
            if (d.isFastFood) lines.add("Fast food")
            d.brand?.takeIf { it.isNotBlank() }?.let { lines.add("Enseigne: $it") }
            d.cuisine?.takeIf { it.isNotBlank() }?.let { lines.add("Cuisine: $it") }
            d.openingHours?.takeIf { it.isNotBlank() }?.let { lines.add("Horaires: $it") }
        }
        return lines.joinToString("\n").ifBlank { "No extra details" }
    }

    private fun connectorLabel(id: String): String = when (id) {
        "type_2" -> "Type 2"
        "combo_ccs" -> "CCS"
        "chademo" -> "CHAdeMO"
        "ef" -> "E/F"
        "autre" -> "Autre"
        else -> id
    }
}

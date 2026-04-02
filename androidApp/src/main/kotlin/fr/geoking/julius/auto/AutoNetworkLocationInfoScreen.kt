package fr.geoking.julius.auto

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.feature.location.LocationHelper
import fr.geoking.julius.R
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkStatus
import fr.geoking.julius.shared.network.NetworkType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class AutoNetworkLocationInfoScreen(
    carContext: CarContext,
    private val networkService: NetworkService
) : Screen(carContext) {

    private var networkStatus: NetworkStatus = NetworkStatus()
    private var locationAddress: String = "Searching address..."
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var isLoadingLocation = true

    init {
        lifecycleScope.launch {
            networkService.status.collectLatest { status ->
                networkStatus = status
                invalidate()
            }
        }
        loadLocation()
    }

    private fun loadLocation() {
        lifecycleScope.launch {
            isLoadingLocation = true
            invalidate()

            val location = LocationHelper.getCurrentLocation(carContext)
            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude

                val geocoder = Geocoder(carContext, Locale.getDefault())
                try {
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        kotlin.coroutines.suspendCoroutine<String?> { continuation ->
                            geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resumeWith(Result.success(addresses.firstOrNull()?.let { formatAddress(it) }))
                                }
                                override fun onError(errorMessage: String?) {
                                    continuation.resumeWith(Result.success(null))
                                }
                            })
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { formatAddress(it) }
                    }
                    locationAddress = address ?: "Address not found"
                } catch (e: Exception) {
                    Log.e("AutoNetworkInfo", "Geocoding failed", e)
                    locationAddress = "Geocoding error"
                }
            } else {
                locationAddress = "Location not available"
            }
            isLoadingLocation = false
            invalidate()
        }
    }

    private fun formatAddress(address: Address): String {
        val sb = StringBuilder()
        for (i in 0..address.maxAddressLineIndex) {
            sb.append(address.getAddressLine(i))
            if (i < address.maxAddressLineIndex) sb.append(", ")
        }
        return sb.toString()
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Network info
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Network: ${if (networkStatus.isConnected) "Connected" else "Disconnected"}")
                .addText("Type: ${networkStatus.networkType.toReadableString()} | Operator: ${networkStatus.operatorName ?: "Unknown"}")
                .addText("Country: ${networkStatus.countryName ?: networkStatus.countryCode ?: "Unknown"} | Roaming: ${if (networkStatus.isRoaming) "Yes" else "No"}")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_speaker)).build())
                .build()
        )

        // Location info
        val locationRow = Row.Builder()
            .setTitle("Current Location")
        if (isLoadingLocation) {
            locationRow.addText("Loading coordinates...")
        } else {
            locationRow.addText("Lat: ${String.format("%.6f", latitude)}, Lon: ${String.format("%.6f", longitude)}")
            locationRow.addText(locationAddress)
        }
        locationRow.setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
        listBuilder.addItem(locationRow.build())

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener { loadLocation() }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Network & Location Info")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setActionStrip(actionStrip)
            .build()
    }

    private fun NetworkType.toReadableString(): String = when (this) {
        NetworkType.WIFI -> "WiFi"
        NetworkType.FIVE_G -> "5G"
        NetworkType.FOUR_G -> "4G"
        NetworkType.THREE_G -> "3G"
        NetworkType.TWO_G -> "2G"
        NetworkType.EDGE -> "Edge"
        NetworkType.GPRS -> "GPRS"
        NetworkType.UNKNOWN -> "Unknown"
        NetworkType.NONE -> "None"
    }
}

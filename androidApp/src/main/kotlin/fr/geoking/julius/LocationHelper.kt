package fr.geoking.julius

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    private const val TAG = "LocationHelper"

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context, timeoutMs: Long = 3000L): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 1. Try last known location first (instant)
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null && (System.currentTimeMillis() - location.time) < 300_000) { // 5 minutes fresh
                    Log.d(TAG, "Using fresh lastKnownLocation from $provider")
                    return location
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get lastKnownLocation from $provider", e)
            }
        }

        // 2. Request a fresh update if lastKnown is missing or stale
        Log.d(TAG, "Requesting fresh location update (timeout ${timeoutMs}ms)")
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                @Suppress("DEPRECATION")
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        LocationManager.NETWORK_PROVIDER
                    } else {
                        null
                    }

                    if (provider != null) {
                        @Suppress("DEPRECATION")
                        locationManager.requestSingleUpdate(provider, listener, null)
                    } else {
                        if (continuation.isActive) continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting single update", e)
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        } ?: run {
            // If timeout or null, return the best available lastKnown even if stale
            Log.d(TAG, "Fresh update timed out, falling back to any lastKnown")
            providers.firstNotNullOfOrNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

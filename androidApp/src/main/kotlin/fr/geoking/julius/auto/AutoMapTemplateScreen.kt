package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AutoMapTemplateScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private var surfaceRenderer: AutoSurfaceRenderer? = null
    private val lat = 48.8566
    private val lon = 2.3522
    private val zoom = 14

    init {
        lifecycle.addObserver(this)
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surfaceRenderer?.stop()
        val surface = surfaceContainer.surface
        if (surface == null) {
            surfaceRenderer = null
            return
        }
        val osmUrl: (Int, Int, Int) -> String = { z, x, y ->
            "https://tile.openstreetmap.org/$z/$x/$y.png"
        }
        surfaceRenderer = AutoSurfaceRenderer(
            carContext,
            surface,
            surfaceContainer.width,
            surfaceContainer.height,
            tileUrl = osmUrl
        ).apply {
            updateLocation(lat, lon, zoom)
            start()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onGetTemplate(): Template {
        return MapTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("MapTemplate (OSM)")
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder()
                            .setTitle("Zoom In")
                            .setOnClickListener { /* Logic here */ }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

package fr.geoking.julius.auto

import android.content.res.Configuration
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
import fr.geoking.julius.R

class AutoMapTemplateScreen(carContext: CarContext) : Screen(carContext) {

    private var zoom = 14
    private var isDarkMode = false

    private fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(4, 18)
        invalidate()
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoMapTemplateScreen") {
        val currentDarkMode = (carContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (currentDarkMode != isDarkMode) {
            isDarkMode = currentDarkMode
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Zoom In")
                    .setOnClickListener { bumpZoom(1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Zoom Out")
                    .setOnClickListener { bumpZoom(-1) }
                    .build()
            )

        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("MapTemplate (List Only)")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }
}

package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.R

/**
 * Fallback screen shown when MainScreen fails to load (e.g. Koin/DI init error on Android Auto).
 * Displays the error message for debugging on real devices.
 */
class ErrorScreen(
    carContext: CarContext,
    private val errorMessage: String,
    private val errorDetail: String? = null
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val detail = errorDetail?.take(200) ?: ""
        val fullText = if (detail.isNotEmpty()) "$errorMessage\n$detail" else errorMessage
        return PaneTemplate.Builder(
            Pane.Builder()
                .setImage(
                    androidx.car.app.model.CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.auto_theme_idle)
                    ).build()
                )
                .addRow(
                    Row.Builder()
                        .setTitle("Julius Error")
                        .addText(fullText.take(500))
                        .build()
                )
                .build()
        )
            .setTitle("Julius")
            .build()
    }
}

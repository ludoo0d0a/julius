package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class AutoRoutePreviewNavigationTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoRoutePreviewNavigationTemplateScreen") {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Fastest Route")
                    .addText("25 min")
                    .setOnClickListener { /* Select route */ }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Shortest Route")
                    .addText("30 min")
                    .setOnClickListener { /* Select route */ }
                    .build()
            )

        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Route Preview (List Only)")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Navigate")
                            .setOnClickListener { /* Start nav */ }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

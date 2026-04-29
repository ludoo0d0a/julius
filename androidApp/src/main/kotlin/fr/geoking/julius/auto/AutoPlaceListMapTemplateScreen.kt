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

class AutoPlaceListMapTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoPlaceListMapTemplateScreen") {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Eiffel Tower")
                    .addText("Champ de Mars, 5 Avenue Anatole France, 75007 Paris")
                    .setBrowsable(true)
                    .setOnClickListener { /* No-op */ }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Louvre Museum")
                    .addText("Rue de Rivoli, 75001 Paris")
                    .setBrowsable(true)
                    .setOnClickListener { /* No-op */ }
                    .build()
            )

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("PlaceListMapTemplate (List Only)")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setActionStrip(actionStrip)
            .setSingleList(listBuilder.build())
            .build()
    }
}

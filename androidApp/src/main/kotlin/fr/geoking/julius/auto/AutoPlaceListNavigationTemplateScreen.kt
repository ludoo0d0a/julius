package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class AutoPlaceListNavigationTemplateScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoPlaceListNavigationTemplateScreen") {
        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Eiffel Tower")
                    .addText("Champ de Mars, 5 Avenue Anatole France, 75007 Paris")
                    .setBrowsable(true)
                    .setOnClickListener { /* No-op */ }
                    .build()
            )

        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("PlaceListNavigationTemplate (List Only)")
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(
                        Action.Builder().setTitle("Exit").setOnClickListener { screenManager.pop() }.build()
                    )
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()
    }
}

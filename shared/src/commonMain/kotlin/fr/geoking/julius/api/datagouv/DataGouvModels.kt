package fr.geoking.julius.api.datagouv

data class DataGouvPrice(
    val fuelName: String,
    val price: Double
)

data class DataGouvStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String?,
    val prices: List<DataGouvPrice>
)


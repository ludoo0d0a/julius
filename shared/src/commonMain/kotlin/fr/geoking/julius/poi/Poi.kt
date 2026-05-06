package fr.geoking.julius.poi

data class Poi(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val isElectric: Boolean = false,
    val powerKw: Double? = null,
    val brand: String? = null,
    val operator: String? = null,
    val irveDetails: IrveDetails? = null
)


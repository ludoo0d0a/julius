package fr.geoking.julius.api.gas

import fr.geoking.julius.poi.FuelPrice

data class GasApiStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String?,
    val prices: List<FuelPrice>
)


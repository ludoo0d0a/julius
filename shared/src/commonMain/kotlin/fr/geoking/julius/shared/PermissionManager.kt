package fr.geoking.julius.shared

interface PermissionManager {
    fun hasPermission(permission: String): Boolean
    suspend fun requestPermission(permission: String): Boolean
}

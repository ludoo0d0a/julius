package fr.geoking.julius.shared.platform

interface PermissionManager {
    fun hasPermission(permission: String): Boolean
    suspend fun requestPermission(permission: String): Boolean
}

package fr.geoking.julius.agents

import android.app.ActivityManager
import android.content.Context

object MemoryHelper {
    fun getMemoryReport(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val availableMb = mi.availMem / (1024 * 1024)
            val totalMb = mi.totalMem / (1024 * 1024)
            "RAM: ${availableMb}MB available / ${totalMb}MB total (lowMemory=${mi.lowMemory}, threshold=${mi.threshold / (1024 * 1024)}MB)"
        } catch (e: Exception) {
            "RAM: unknown (${e.message})"
        }
    }
}

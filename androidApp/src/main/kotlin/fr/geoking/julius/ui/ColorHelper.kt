package fr.geoking.julius.ui

import androidx.compose.ui.graphics.Color

object ColorHelper {
    // Fuel Colors
    val ColorGazole = Color(0xFFEAB308)
    val ColorGazolePlus = Color(0xFFCA8A04)
    val ColorSP95 = Color(0xFF22C55E)
    val ColorSP95E10 = Color(0xFF4ADE80)
    val ColorSP98 = Color(0xFF15803D)
    val ColorE85 = Color(0xFFA855F7)
    val ColorGPLC = Color(0xFFF97316)

    // Power Colors
    val ColorPower0_20 = Color(0xFF86EFAC)
    val ColorPower20_50 = Color(0xFF22C55E)
    val ColorPower50_100 = Color(0xFF15803D)
    val ColorPower100_200 = Color(0xFFEAB308)
    val ColorPower200_300 = Color(0xFFF97316)
    val ColorPower300Plus = Color(0xFFEF4444)

    // Jules Colors
    val JulesBg = Color(0xFF0F172A)
    val JulesCardUser = Color(0xFF334155)
    val JulesCardAgent = Color(0xFF1E293B)
    val JulesAccent = Color(0xFF818CF8)
    val JulesErrorBg = Color(0xFF7F1D1D)
    val JulesHeaderBg = Color(0xFF1E293B)
    val JulesListBg = Color(0xFF1E293B)

    fun getFuelColor(fuelId: String?): Color? = when (fuelId) {
        "gazole" -> ColorGazole
        "gazole_plus" -> ColorGazolePlus
        "sp95" -> ColorSP95
        "sp95_e10" -> ColorSP95E10
        "sp98" -> ColorSP98
        "e85" -> ColorE85
        "gplc" -> ColorGPLC
        else -> null
    }

    fun getPowerColor(powerKw: Double): Color = when {
        powerKw < 20 -> ColorPower0_20
        powerKw < 50 -> ColorPower20_50
        powerKw < 100 -> ColorPower50_100
        powerKw < 200 -> ColorPower100_200
        powerKw < 300 -> ColorPower200_300
        else -> ColorPower300Plus
    }

    fun getPowerColorByLevel(level: Int): Color = when (level) {
        0 -> ColorPower0_20
        20 -> ColorPower20_50
        50 -> ColorPower50_100
        100 -> ColorPower100_200
        200 -> ColorPower200_300
        300 -> ColorPower300Plus
        else -> ColorPower0_20
    }
}

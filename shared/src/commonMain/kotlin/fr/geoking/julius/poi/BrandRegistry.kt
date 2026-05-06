package fr.geoking.julius.poi

/**
 * Minimal registry used by provider tests. The Android app has richer icon plumbing; on shared we only
 * need a consistent "do we know this brand" check.
 */
object BrandRegistry {
    private val knownBrands = setOf(
        "Tesla",
        "Ionity",
        "Total",
        "TotalEnergies",
        "Shell"
    )

    fun hasIcon(brand: String?): Boolean = brand != null && knownBrands.contains(brand)
}


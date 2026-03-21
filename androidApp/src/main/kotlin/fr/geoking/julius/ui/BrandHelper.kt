package fr.geoking.julius.ui

import fr.geoking.julius.R

/**
 * Maps Routex brand_id to company display name and icon for gas station detail.
 * Uses brand-specific drawable when available, otherwise default gas icon.
 */
object BrandHelper {

    /** Known brand_id (lowercase) -> company display name. */
    val brandNames = mapOf(
        "total" to "Total",
        "totalenergies" to "TotalEnergies",
        "bp" to "BP",
        "shell" to "Shell",
        "esso" to "Esso",
        "esso express" to "Esso Express",
        "eni" to "Eni",
        "repsol" to "Repsol",
        "omv" to "OMV",
        "avia" to "AVIA",
        "q8" to "Q8",
        "agip" to "Agip",
        "carrefour" to "Carrefour",
        "leclerc" to "E.Leclerc",
        "auchan" to "Auchan",
        "intermarche" to "Intermarché",
        "casino" to "Casino",
        "rel" to "REL",
        "rel.metz" to "REL",
        "circle k" to "Circle K",
        "eurogarages" to "Euro Garages",
        "aral" to "Aral",
        "jet" to "Jet",
        "elf" to "Elf",
        "migrol" to "Migrol",
        "coop" to "Coop",
        "migros" to "Migros",
        "tesla" to "Tesla",
        "ionity" to "Ionity",
        "fastned" to "Fastned",
        "allego" to "Allego",
        "lidl" to "Lidl",
        "chargy" to "Chargy",
        "atlante" to "Atlante",
        "zunder" to "Zunder",
    )

    /** brand_id (lowercase) -> brand icon drawable. Unlisted brands use ic_poi_gas. */
    private val brandIcons = mapOf(
        "total" to R.drawable.ic_brand_total,
        "totalenergies" to R.drawable.ic_brand_total,
        "bp" to R.drawable.ic_brand_bp,
        "shell" to R.drawable.ic_brand_shell,
        "esso" to R.drawable.ic_brand_esso,
        "esso express" to R.drawable.ic_brand_esso,
        "eni" to R.drawable.ic_brand_eni,
        "repsol" to R.drawable.ic_brand_repsol,
        "omv" to R.drawable.ic_brand_omv,
        "avia" to R.drawable.ic_brand_avia,
        "rel" to R.drawable.ic_brand_rel,
        "rel.metz" to R.drawable.ic_brand_rel,
        "circle k" to R.drawable.ic_brand_circlek,
        "aral" to R.drawable.ic_brand_aral,
        "carrefour" to R.drawable.ic_brand_carrefour,
        "leclerc" to R.drawable.ic_brand_leclerc,
        "e.leclerc" to R.drawable.ic_brand_leclerc,
        "auchan" to R.drawable.ic_brand_auchan,
        "tesla" to R.drawable.ic_brand_tesla,
        "ionity" to R.drawable.ic_brand_ionity,
        "fastned" to R.drawable.ic_brand_fastned,
        "allego" to R.drawable.ic_brand_allego,
        "lidl" to R.drawable.ic_brand_lidl,
        "chargy" to R.drawable.ic_poi_electric,
        "atlante" to R.drawable.ic_poi_electric,
        "zunder" to R.drawable.ic_poi_electric,
    )

    /** brand_id (lowercase) -> rounded brand icon drawable. Unlisted brands use ic_poi_gas_rounded. */
    private val roundedBrandIcons = mapOf(
        "total" to R.drawable.ic_brand_total_rounded,
        "totalenergies" to R.drawable.ic_brand_total_rounded,
        "bp" to R.drawable.ic_brand_bp_rounded,
        "shell" to R.drawable.ic_brand_shell_rounded,
        "esso" to R.drawable.ic_brand_esso_rounded,
        "esso express" to R.drawable.ic_brand_esso_rounded,
        "eni" to R.drawable.ic_brand_eni_rounded,
        "repsol" to R.drawable.ic_brand_repsol_rounded,
        "omv" to R.drawable.ic_brand_omv_rounded,
        "avia" to R.drawable.ic_brand_avia_rounded,
        "rel" to R.drawable.ic_brand_rel_rounded,
        "rel.metz" to R.drawable.ic_brand_rel_rounded,
        "circle k" to R.drawable.ic_brand_circlek_rounded,
        "aral" to R.drawable.ic_brand_aral_rounded,
        "carrefour" to R.drawable.ic_brand_carrefour_rounded,
        "leclerc" to R.drawable.ic_brand_leclerc_rounded,
        "e.leclerc" to R.drawable.ic_brand_leclerc_rounded,
        "auchan" to R.drawable.ic_brand_auchan_rounded,
        "tesla" to R.drawable.ic_brand_tesla_rounded,
        "ionity" to R.drawable.ic_brand_ionity_rounded,
        "fastned" to R.drawable.ic_brand_fastned_rounded,
        "allego" to R.drawable.ic_brand_allego_rounded,
        "lidl" to R.drawable.ic_brand_lidl_rounded,
        "chargy" to R.drawable.ic_poi_electric_rounded,
        "atlante" to R.drawable.ic_poi_electric_rounded,
        "zunder" to R.drawable.ic_poi_electric_rounded,
    )

    /** brand_id (lowercase) -> is gas station brand. */
    private val gasBrands = setOf(
        "total", "totalenergies", "bp", "shell", "esso", "esso express", "eni", "repsol", "omv", "avia",
        "q8", "agip", "carrefour", "leclerc", "auchan", "intermarche", "casino", "rel", "rel.metz",
        "circle k", "eurogarages", "aral", "jet", "elf", "migrol", "coop", "migros"
    )

    /** brand_id (lowercase) -> is electric charging brand. */
    private val electricBrands = setOf(
        "tesla", "ionity", "fastned", "allego", "lidl", "chargy", "atlante", "zunder", "totalenergies"
    )

    data class BrandInfo(
        val displayName: String,
        val iconResId: Int,
        val roundedIconResId: Int
    )

    fun getBrandInfo(brandId: String?): BrandInfo? {
        if (brandId.isNullOrBlank()) return null
        val normalized = brandId.trim().lowercase()

        // 1. Try fuzzy match first
        val fuzzyEntry = brandNames.entries.find { normalized.contains(it.key) }
        if (fuzzyEntry != null) {
            val key = fuzzyEntry.key
            return BrandInfo(
                displayName = fuzzyEntry.value,
                iconResId = brandIcons[key] ?: R.drawable.ic_poi_gas,
                roundedIconResId = roundedBrandIcons[key] ?: R.drawable.ic_poi_gas_rounded
            )
        }

        // 2. Exact match (redundant if fuzzy match caught it, but safe)
        if (brandNames.containsKey(normalized)) {
            return BrandInfo(
                displayName = brandNames[normalized]!!,
                iconResId = brandIcons[normalized] ?: R.drawable.ic_poi_gas,
                roundedIconResId = roundedBrandIcons[normalized] ?: R.drawable.ic_poi_gas_rounded
            )
        }

        // 3. No match: return null to let UI handle fallback icon (plug vs pump)
        return null
    }

    /** Returns list of brands for fuel (gas). */
    fun getGasBrands(): List<Pair<String, String>> {
        return brandNames.filterKeys { it in gasBrands }
            .entries.map { it.key to it.value }
            .distinctBy { it.second }
            .sortedBy { it.second }
    }

    /** Returns list of brands for electric charging. */
    fun getElectricBrands(): List<Pair<String, String>> {
        return brandNames.filterKeys { it in electricBrands }
            .entries.map { it.key to it.value }
            .distinctBy { it.second }
            .sortedBy { it.second }
    }
}

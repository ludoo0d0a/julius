package fr.geoking.julius.ui

import fr.geoking.julius.R

/**
 * Maps Routex brand_id to company display name and icon for gas station detail.
 * Uses brand-specific drawable when available, otherwise default gas icon.
 */
object BrandHelper {

    /** Known brand_id (lowercase) -> company display name. */
    private val brandNames = mapOf(
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
        "rel" to R.drawable.ic_brand_rel,
        "rel.metz" to R.drawable.ic_brand_rel,
        "circle k" to R.drawable.ic_brand_circlek,
        "aral" to R.drawable.ic_brand_aral,
    )

    data class BrandInfo(
        val displayName: String,
        val iconResId: Int
    )

    fun getBrandInfo(brandId: String?): BrandInfo? {
        if (brandId.isNullOrBlank()) return null
        val normalized = brandId.trim().lowercase()
        val name = brandNames[normalized] ?: brandId.trim().takeIf { it.isNotBlank() }
            ?: return null
        val iconResId = brandIcons[normalized] ?: R.drawable.ic_poi_gas
        return BrandInfo(
            displayName = name,
            iconResId = iconResId
        )
    }
}

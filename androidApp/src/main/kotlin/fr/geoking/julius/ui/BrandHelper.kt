package fr.geoking.julius.ui

import fr.geoking.julius.R
import java.text.Normalizer
import java.util.Locale

/**
 * Maps fuel station brand strings to display name and icon for map markers and detail.
 * Uses brand-specific drawable when available, otherwise default gas icon.
 */
object BrandHelper {

    /** Lookup key (lowercase, normalized) -> company display name. */
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
        "superu" to "Super U",
        "indigo" to "Indigo",
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

    /** Lookup key -> brand icon drawable. Unlisted brands use ic_poi_gas. */
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
        "intermarche" to R.drawable.ic_brand_intermarche,
        "casino" to R.drawable.ic_brand_casino,
        "superu" to R.drawable.ic_brand_superu,
        "indigo" to R.drawable.ic_brand_indigo,
    )

    /** Lookup key -> rounded brand icon drawable. Unlisted brands use ic_poi_gas_rounded. */
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
        "intermarche" to R.drawable.ic_brand_intermarche_rounded,
        "casino" to R.drawable.ic_brand_casino_rounded,
        "superu" to R.drawable.ic_brand_superu_rounded,
        "indigo" to R.drawable.ic_brand_indigo_rounded,
    )

    data class BrandInfo(
        val displayName: String,
        val iconResId: Int,
        val roundedIconResId: Int
    )

    fun getBrandInfo(brandId: String?): BrandInfo? {
        if (brandId.isNullOrBlank()) return null
        val key = normalizeLookupKey(brandId)
        if (key.isBlank()) return null
        val displayName = brandNames[key] ?: brandId.trim()
        val iconResId = brandIcons[key] ?: R.drawable.ic_poi_gas
        val roundedIconResId = roundedBrandIcons[key] ?: R.drawable.ic_poi_gas_rounded
        return BrandInfo(
            displayName = displayName,
            iconResId = iconResId,
            roundedIconResId = roundedIconResId
        )
    }

    /**
     * Strip accents, lowercase, and map common API / commercial variants to a single lookup key
     * (e.g. "SUPER U EXPRESS", "Hyper U" -> superu).
     */
    private fun normalizeLookupKey(raw: String): String {
        val base = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.FRANCE)
            .replace(Regex("\\s+"), " ")
        return when {
            base.contains("systeme u") || base.contains("super u") || base.contains("hyper u") ||
                base.contains("u express") || base.contains("station u") -> "superu"
            base.contains("intermarche") -> "intermarche"
            base.contains("casino") -> "casino"
            base.contains("indigo") -> "indigo"
            base.contains("total") && base.contains("access") -> "totalenergies"
            base.contains("esso") && base.contains("express") -> "esso express"
            else -> base.replace(". ", ".").trim()
        }
    }
}

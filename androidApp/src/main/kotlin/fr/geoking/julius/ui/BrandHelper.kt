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
        "totalenergies" to "Total",
        "bp" to "BP",
        "shell" to "Shell",
        "esso" to "Esso",
        "esso express" to "Esso",
        "eni" to "Eni",
        "repsol" to "Repsol",
        "omv" to "OMV",
        "avia" to "AVIA",
        "q8" to "Q8",
        "agip" to "Agip",
        "carrefour" to "Carrefour",
        "leclerc" to "Leclerc",
        "e.leclerc" to "Leclerc",
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
        "tesla" to "Tesla",
        "ionity" to "Ionity",
        "fastned" to "Fastned",
        "allego" to "Allego",
        "lidl" to "Lidl",
        "chargy" to "Chargy",
        "atlante" to "Atlante",
        "zunder" to "Zunder",
        "freshmile" to "Freshmile",
        "superu" to "Super U",
        "système u" to "Système U",
        "coopérative u" to "Coopérative U",
        "match" to "Match",
        "supermarché match" to "Supermarché Match",
        "powerdot" to "Powerdot",
        "driveco" to "Driveco",
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
        "q8" to R.drawable.ic_brand_q8,
        "agip" to R.drawable.ic_brand_agip,
        "eurogarages" to R.drawable.ic_brand_eurogarages,
        "jet" to R.drawable.ic_brand_jet,
        "elf" to R.drawable.ic_brand_elf,
        "migrol" to R.drawable.ic_brand_migrol,
        "coop" to R.drawable.ic_brand_coop,
        "migros" to R.drawable.ic_brand_migros,
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
        "tesla" to R.drawable.ic_brand_tesla,
        "ionity" to R.drawable.ic_brand_ionity,
        "fastned" to R.drawable.ic_brand_fastned,
        "allego" to R.drawable.ic_brand_allego,
        "lidl" to R.drawable.ic_brand_lidl,
        "chargy" to R.drawable.ic_brand_chargy,
        "atlante" to R.drawable.ic_brand_atlante,
        "zunder" to R.drawable.ic_brand_zunder,
        "freshmile" to R.drawable.ic_brand_freshmile,
        "superu" to R.drawable.ic_brand_superu,
        "système u" to R.drawable.ic_brand_superu,
        "coopérative u" to R.drawable.ic_brand_superu,
        "match" to R.drawable.ic_brand_match,
        "supermarché match" to R.drawable.ic_brand_match,
        "powerdot" to R.drawable.ic_brand_powerdot,
        "driveco" to R.drawable.ic_brand_driveco,
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
        "q8" to R.drawable.ic_brand_q8_rounded,
        "agip" to R.drawable.ic_brand_agip_rounded,
        "eurogarages" to R.drawable.ic_brand_eurogarages_rounded,
        "jet" to R.drawable.ic_brand_jet_rounded,
        "elf" to R.drawable.ic_brand_elf_rounded,
        "migrol" to R.drawable.ic_brand_migrol_rounded,
        "coop" to R.drawable.ic_brand_coop_rounded,
        "migros" to R.drawable.ic_brand_migros_rounded,
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
        "tesla" to R.drawable.ic_brand_tesla_rounded,
        "ionity" to R.drawable.ic_brand_ionity_rounded,
        "fastned" to R.drawable.ic_brand_fastned_rounded,
        "allego" to R.drawable.ic_brand_allego_rounded,
        "lidl" to R.drawable.ic_brand_lidl_rounded,
        "chargy" to R.drawable.ic_brand_chargy_rounded,
        "atlante" to R.drawable.ic_brand_atlante_rounded,
        "zunder" to R.drawable.ic_brand_zunder_rounded,
        "freshmile" to R.drawable.ic_brand_freshmile_rounded,
        "superu" to R.drawable.ic_brand_superu_rounded,
        "système u" to R.drawable.ic_brand_superu_rounded,
        "coopérative u" to R.drawable.ic_brand_superu_rounded,
        "match" to R.drawable.ic_brand_match_rounded,
        "supermarché match" to R.drawable.ic_brand_match_rounded,
        "powerdot" to R.drawable.ic_brand_powerdot_rounded,
        "driveco" to R.drawable.ic_brand_driveco_rounded,
    )

    /** brand_id (lowercase) -> is gas station brand. */
    private val gasBrands = setOf(
        "total", "totalenergies", "bp", "shell", "esso", "esso express", "eni", "repsol", "omv", "avia",
        "q8", "agip", "carrefour", "leclerc", "auchan", "intermarche", "casino", "rel", "rel.metz",
        "circle k", "eurogarages", "aral", "jet", "elf", "migrol", "coop", "migros",
        "superu", "système u", "match", "supermarché match"
    )

    /** brand_id (lowercase) -> is electric charging brand. */
    private val electricBrands = setOf(
        "tesla", "ionity", "fastned", "allego", "lidl", "chargy", "atlante", "zunder", "total", "totalenergies",
        "freshmile", "superu", "système u", "coopérative u", "match", "supermarché match",
        "powerdot", "driveco", "carrefour", "leclerc", "auchan"
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

    /**
     * Display label + [brandIcons] drawable for each [getElectricBrands] entry (fallback: generic plug).
     */
    fun getElectricBrandIconEntries(): List<Pair<String, Int>> {
        return getElectricBrands().map { (key, label) ->
            label to (brandIcons[key] ?: R.drawable.ic_poi_electric)
        }
    }

    /**
     * Display label + [brandIcons] drawable for each [getGasBrands] entry (fallback: generic pump).
     */
    fun getGasBrandIconEntries(): List<Pair<String, Int>> {
        return getGasBrands().map { (key, label) ->
            label to (brandIcons[key] ?: R.drawable.ic_poi_gas)
        }
    }

    /**
     * One row per distinct rounded marker-head drawable (circle + logo), for design previews.
     * [Pair.first] is display name; [Pair.second] is `R.drawable.ic_brand_*_rounded` (or shared fallback).
     */
    fun distinctRoundedBrandHeads(): List<Pair<String, Int>> {
        return brandIcons.entries
            .mapNotNull { (key, _) ->
                val label = brandNames[key] ?: return@mapNotNull null
                val rounded = roundedBrandIcons[key] ?: return@mapNotNull null
                label to rounded
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase(Locale.getDefault()) }
    }

    /** Returns a human-readable label for IRVE connector type IDs. */
    fun connectorTypeLabel(id: String): String = when (id) {
        "type_2" -> "Type 2"
        "combo_ccs" -> "CCS"
        "chademo" -> "CHAdeMO"
        "ef" -> "E/F"
        "autre" -> "Autre"
        else -> id
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

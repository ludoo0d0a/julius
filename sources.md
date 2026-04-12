# Free API Source Providers for Fuel and IRVE

This document lists the free API source providers integrated into the Julius station finder and the status of fuel price coverage across Europe.

## Integrated Station-Specific Providers (Exact Prices)

| Provider Name | Region / Country | Category | Source Type | Status / Notes |
|---------------|------------------|----------|-------------|----------------|
| **DataGouv / Gas API** | France | Fuel | REST API | **Cracked**: Official JSON feeds from prix-carburants.gouv.fr |
| **UK CMA Open Data** | United Kingdom | Fuel | JSON Feeds | **Cracked**: Multiple retailer-specific JSON endpoints (Asda, BP, Shell, etc.) |
| **Italy MIMIT** | Italy | Fuel | CSV Export | **Cracked**: Daily CSV export from Ministero delle Imprese |
| **Spain Minetur** | Spain | Fuel | REST API | **Cracked**: Official government REST API |
| **Germany Tankerkönig** | Germany | Fuel | REST API | **Cracked**: MTS-K official data via Tankerkönig API |
| **Austria E-Control** | Austria | Fuel | REST API | **Cracked**: Official E-Control Spritpreis API |
| **Portugal DGEG** | Portugal (Mainland) | Fuel | REST API | **Cracked**: Official DGEG JSON API |
| **Belgium Official** | Belgium | Fuel | Scraper | **Cracked**: Official national max prices scraped & applied to OSM |
| **Madeira Official** | Madeira, Portugal | Fuel | Scraper | **Cracked**: Regional max prices scraped & applied to OSM |
| **Chargy** | Luxembourg | IRVE (EV) | KML Feed | Official real-time status |
| **DataGouv IRVE** | France | IRVE (EV) | REST API | Official French open data |
| **Open Charge Map** | Global / Europe | IRVE (EV) | REST API | Community-driven global data |
| **OpenStreetMap** | Global / Europe | Both | Overpass API | Base station locations and metadata |

## European Fuel Price Coverage Status

The application aims for exact station-by-station prices. Where not yet "cracked" via a direct API, it uses official national/regional averages or regulated maximums as a fallback.

| Country | Code | Coverage Level | Source / Method | Status |
|---------|------|----------------|-----------------|--------|
| **France** | FR | Station-specific | DataGouv / Gas API | ✅ Cracked (Official API) |
| **United Kingdom** | GB | Station-specific | CMA Open Data | ✅ Cracked (JSON Feeds) |
| **Italy** | IT | Station-specific | MIMIT Open Data | ✅ Cracked (CSV) |
| **Spain** | ES | Station-specific | Minetur | ✅ Cracked (Official API) |
| **Germany** | DE | Station-specific | Tankerkönig | ✅ Cracked (Official API) |
| **Austria** | AT | Station-specific | E-Control | ✅ Cracked (Official API) |
| **Portugal** | PT | Station-specific | DGEG / Madeira Gov | ✅ Cracked (Official API) |
| **Belgium** | BE | Regulated Max | PetrolPrices FGOV | ✅ Cracked (Scraper) |
| **Luxembourg** | LU | Regulated Max | OpenVan.camp | ⚠️ National Max Fallback |
| **Slovenia** | SI | Regulated Max | OpenVan.camp | ⚠️ National Max Fallback |
| **Croatia** | HR | Regulated Max | OpenVan.camp | ⚠️ National Max Fallback |
| **Netherlands** | NL | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Denmark** | DK | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Sweden** | SE | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Norway** | NO | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Finland** | FI | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Poland** | PL | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Hungary** | HU | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Ireland** | IE | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Greece** | GR | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Romania** | RO | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Czechia** | CZ | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Slovakia** | SK | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |
| **Bulgaria** | BG | Reference Avg | OpenVan.camp | ⚠️ Average Fallback |

## Implementation Details

The application uses a unified `PoiProvider` interface to query these different sources. For fuel prices, it attempts to normalize fuel names (e.g., Gazole, SP95, SP98) to provide a consistent filtering experience across different countries.

### "Cracking" Methodology
- **JSON/REST APIs**: Preferred for real-time, low-overhead access.
- **CSV/Bulk Data**: Used for large datasets (like Italy), cached for 6-24 hours.
- **Scrapers**: Used when official data is only available on web portals (like Belgium or Madeira).
- **OSM Enrichment**: For countries without a public station list, we fetch stations from OpenStreetMap and "enrich" them with official price data.

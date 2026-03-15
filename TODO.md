# Julius – TODO & roadmap

## Features comparison: Julius vs ChargeMap-style apps

| Feature | Julius | ChargeMap-style (e.g. ChargeMap, PlugShare) |
|--------|--------|---------------------------------------------|
| **Map & POIs** | | |
| Map with charging stations | ✅ Multiple sources (DataGouvElec, OpenChargeMap, Routex, Etalab, etc.) | ✅ |
| Connector types (Type 2, CCS, CHAdeMO, E/F) | ✅ Display + filter | ✅ |
| Power (kW) | ✅ Display + min-power filter | ✅ |
| Tarification / pricing | ✅ Plain text (IRVE); fuel prices (DataGouv/Etalab) | ✅ Often structured |
| Opening hours | ✅ IRVE horaires (as-is) | ✅ |
| Filter by connector | ✅ | ✅ |
| Filter by power / operator | ✅ | ✅ |
| **Route & vehicle** | | |
| Route planning A→B | ✅ OSRM | ✅ |
| Charging stops along route | ✅ Suggested stops list | ✅ |
| Vehicle profile (range, consumption) | ✅ Persisted in settings | ✅ |
| **Station details** | | |
| Reservation possible | ✅ Display only | ✅ Often with booking |
| Payment methods | ✅ Display (à l’acte, CB, autre) | ✅ |
| Access (libre / réservé) | ✅ | ✅ |
| Real-time availability | ✅ Borne API when available | ✅ Varies by app |
| **User content** | | |
| Local ratings (1–5 stars) | ✅ SharedPreferences, no backend | ✅ Cloud ratings + comments |
| Favorites / saved stations | ❌ | ✅ |
| Photos / check-in | ❌ | ✅ Many apps |
| Comments / reviews (text) | ❌ | ✅ |
| **Platform & UX** | | |
| Android Auto | ✅ Map + POI list + detail | ✅ Some apps |
| Voice assistant | ✅ Core feature | ❌ |
| Navigation to station | ✅ Intent to maps/nav | ✅ |
| Account / sign-in | ❌ | ✅ For sync, payment |
| In-app payment / RFID | ❌ | ✅ Some networks |

**Summary:** Julius covers map, filters, IRVE extended data, route planning, vehicle profile, and local ratings using **public data only**. Missing vs typical EV apps: favorites, cloud ratings/comments, photos, account, and in-app payment/RFID (all require private/backend or partner APIs).

---

## Features

### List all available fuel cards

- **Goal:** Expose in the app (and optionally via voice) the list of supported / available fuel cards (Routex, Total, Edenred, Shell, GO, DKV, etc.) with short descriptions and coverage.
- **Scope:** UI (e.g. Map or Settings) to show “Fuel cards” with name, network coverage, and link to more info; optional voice intent “What fuel cards can I use?”.
- **Data:** Maintain a curated list (see [Fuel cards reference](#fuel-cards-reference) below); consider a small in-app screen or bottom sheet listing cards and which POI provider(s) best match each (e.g. Routex → Routex card stations).

---

## Fuel cards reference

Reference list of fleet / fuel cards (France & Europe) for the “list fuel cards” feature and future filtering. APIs are listed where known.

| Card / program        | Coverage (summary) | API / integration notes |
|-----------------------|--------------------|--------------------------|
| **Routex**            | ~18,000 stations in 32 European countries (BP, eni, OMV, Repsol/Statoil). | **Wigeogis SiteFinder** – used in-app: `https://app.wigeogis.com/kunden/routex-sitefinder/backend/getResults` (POST, no public key). See `RoutexClient.kt`. |
| **UTA Edenred**       | 3,100+ stations in France (Cora, Auchan, Leclerc, ENI, Esso, BP, Shell); pan-European via Ticket Fleet Pro. | No public station-finder API found. Edenred: [Ticket Fleet Pro](https://www.edenred.fr/ticket-fleet-pro). |
| **TotalEnergies Fleet** | ~3,500 stations in France, 25,000+ EV chargers; Europe-wide; tolls, parking, washing. | No public API. Station finder: [mobility.totalenergies.com/trouver-une-station](https://mobility.totalenergies.com/trouver-une-station). App: Mobility My Card. |
| **Shell Fleet ID**    | Shell, Esso, Avia; international; AI route optimization. France, Luxembourg, Belgium. | **Shell Developer Portal** – [developer.shell.com](https://developer.shell.com) – Card Management & Mobility APIs (REST, OAuth 2.0). [Shell Card Management API](https://developer.shell.com/product-catalog/shell-card-management-apis/product-overview). |
| **GO Fuel Card**      | 2,600+ stations in France (BP, Avia, Leclerc, ENI, Esso, Dyneff); expanding to 7,300 in Europe; cost control, CO₂ compensation. | No public API found. |
| **DKV**               | Large European network (fuel, EV, toll, service). | **DKV Mobility API Portal** – [api-portal.dkv-mobility.com](https://api-portal.dkv-mobility.com) – Masterdata, Transaction, Toll services. |
| **EuroShell Card**    | Shell network across Europe. | Same as Shell Fleet ID (Shell Developer APIs). |
| **Aral (BP)**         | Germany & Europe (BP/Aral). | Often covered by Routex (BP) or partner cards; no standalone public station API referenced. |
| **Repsol / Campsa**   | Spain & international. | Routex network includes Repsol; no separate public API noted. |
| **data.gouv.fr / Etalab** | All stations in France with prices (open data). | **Open data** – used in-app: Etalab fuel API, [data.gouv.fr](https://www.data.gouv.fr) (Prix des carburants), [gas-api.ovh](https://gas-api.ovh). See `EtalabClient`, `DataGouvClient`, `GasApiClient`. |

### Short descriptions (for in-app copy)

- **UTA Edenred:** Widely accepted at 3,100+ stations in France (e.g. Cora, Auchan, Leclerc, ENI, Esso, BP, Shell), plus pan-European coverage; includes Ticket Fleet Pro for broader multi-brand access.
- **TotalEnergies Fleet:** ~3,500 stations in France and 25,000 EV chargers; mixed fleets with tolls, parking, washing across Europe.
- **Shell Fleet ID:** Access to Shell, Esso, Avia; strong for international transport with AI route optimization; France, Luxembourg, Belgium.
- **GO Fuel Card:** 2,600+ stations in France (BP, Avia, Leclerc, ENI, Esso, Dyneff), expanding to 7,300 in Europe; cost control and CO₂ compensation.
- **Routex:** Alliance of BP, eni, OMV, Repsol; ~18,000 stations in 32 European countries; station finder powered by Wigeogis (integrated in Julius).
- **DKV:** European fuel, EV, toll and services; B2B APIs for masterdata and transactions.

### APIs summary (for implementation)

| Provider        | Purpose              | Auth        | Used in Julius |
|-----------------|----------------------|------------|-----------------|
| Wigeogis Routex | Station finder (Routex network) | None (POST) | Yes – `RoutexClient` |
| Etalab / data.economie.gouv.fr | Fuel prices & stations (France) | None       | Yes – `EtalabClient` |
| data.gouv.fr    | Prix carburants / IRVE | None       | Yes – `DataGouvClient`, `DataGouvElecProvider` |
| gas-api.ovh     | Fuel prices (France) | None       | Yes – `GasApiClient` |
| Shell Developer | Card management, mobility | OAuth 2.0  | No |
| DKV API Portal  | Masterdata, transactions, toll | Portal signup | No |

---

## Current todos (short list)

- [ ] **List all available fuel cards** – UI (Map/Settings) + optional voice; see [Features > List all available fuel cards](#list-all-available-fuel-cards) and [Fuel cards reference](#fuel-cards-reference).
- [ ] **Filter POIs by accepted fuel card** – e.g. “only Routex” when Routex provider selected; extend when Total/card-specific sources exist.
- [ ] **Voice:** “Where can I refuel with my Total card?” – map filtered by card once multiple card sources are available.

---

## Other ideas (backlog)

- Filter POIs by accepted fuel card (e.g. “only Routex” already implied when Routex provider is selected; extend to “Total only” when/if a Total source exists).
- Voice: “Where can I refuel with my Total card?” → map filtered by card compatibility once multiple card-specific sources are available.

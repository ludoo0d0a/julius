# Feasibility Study: CarPlay Integration for Julius

## Executive Summary
Integrating Apple CarPlay into the Julius project is highly feasible. The project's existing Kotlin Multiplatform (KMP) architecture already separates business logic (POI fetching, price forecasting) into a `shared` module, which is the most critical prerequisite.

To achieve a production-ready CarPlay version, the project should migrate its Android-specific persistence and settings to multiplatform equivalents and implement a native Swift-based CarPlay interface using standard Apple templates.

---

## 1. Proposed Architecture

We recommend a 4-layered architecture to maximize code sharing while adhering to Apple's strict CarPlay UI requirements:

- **`shared` (KMP)**:
    - Business Logic: POI providers, Fuel price registry, Brand matching.
    - Persistence: SQLDelight (Database) & Multiplatform Settings (Preferences).
    - Network: Ktor (already implemented).
- **`androidApp`**:
    - Mobile UI: Jetpack Compose.
    - Android Auto: Car App Library (already implemented).
- **`iosApp` (Swift/Compose)**:
    - Mobile UI: Compose Multiplatform (sharing ~80-90% of `androidApp` UI logic).
    - Framework: Native iOS wrapper to host the KMP shared module.
- **CarPlay Extension (Swift)**:
    - Interface: CarPlay Framework using standard Templates.
    - Communication: Native Swift calls to the `shared` KMP module to fetch data.

---

## 2. Key Technical Migrations

### 2.1 Persistence: Room to SQLDelight
The current project uses Room in the `androidApp` module. To share history and session data with iOS/CarPlay, this must move to `shared`.
- **Tool**: [SQLDelight](https://cashapp.github.io/sqldelight/)
- **Strategy**: Define the schema in `.sq` files in `shared`. SQLDelight will generate type-safe Kotlin APIs for both Android and iOS.
- **Migration**: A one-time migration script or a "clean slate" for the first iOS release.

### 2.2 Settings: SharedPreferences to Multiplatform Settings
`SettingsManager` currently relies on Android `SharedPreferences`.
- **Tool**: [Multiplatform Settings](https://github.com/russhwolf/multiplatform-settings)
- **Strategy**: Replace `SettingsManager` logic with `ObservableSettings`. This allows the CarPlay interface to reactively update when settings change on the phone.

---

## 3. CarPlay Implementation Strategy

Apple is very restrictive regarding CarPlay UIs. Custom map rendering (like the current Android Auto implementation) requires a special "Navigation" entitlement from Apple. Since the goal is to display POIs using "classic allowed maps," the **Point of Interest Template** is the ideal choice.

### 3.1 Template Usage: `CPPointOfInterestTemplate`
This template provides a system-managed map and a list of POIs.
- **Capacity**: Up to 12 POIs at a time.
- **Logic**:
    1.  CarPlay starts -> `CPSceneDelegate` initializes.
    2.  Swift code calls `shared.PoiProvider.searchFlow()` with the current map region.
    3.  Kotlin results are mapped to `CPPointOfInterest` objects in Swift.
    4.  The template is updated via `setPointsOfInterest()`.

### 3.2 Mapping Shared Data to CarPlay
```swift
// Example Swift Mapping Logic
func mapToCarPlay(poi: SharedPoi) -> CPPointOfInterest {
    let location = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: poi.latitude, longitude: poi.longitude)))
    return CPPointOfInterest(
        location: location,
        title: poi.name,
        subtitle: poi.address,
        summary: "Price: \(poi.price)€",
        detailTitle: poi.name,
        detailSubtitle: poi.address,
        detailSummary: poi.details,
        pinImage: UIImage(named: poi.brandIcon)
    )
}
```

---

## 4. Technical Hurdles & Risks

1.  **CarPlay Entitlements**: You must apply for the CarPlay entitlement in the Apple Developer Portal. Without it, you cannot even run the app on a real head unit (only in the simulator).
2.  **Thread Management**: Shared Kotlin flows (Coroutines) must be carefully consumed in Swift (using `Task` or a wrapper like `KMP-NativeCoroutines`).
3.  **Map Control**: `CPPointOfInterestTemplate` does not allow the same level of control as MapLibre. Features like custom tile layers or advanced clustering might be limited.

---

## 5. Implementation Roadmap

### Phase 1: Shared Core Refactoring
- Migrate `AppDatabase` (Room) to SQLDelight in `shared`.
- Migrate `SettingsManager` to Multiplatform Settings.
- Move `JulesRepository` logic from `androidApp` to `commonMain`.

### Phase 2: iOS Mobile App
- Create the `iosApp` Xcode project.
- Implement a basic phone UI using Compose Multiplatform.
- Verify data persistence and network calls on iOS.

### Phase 3: CarPlay Foundation
- Implement `CPSceneDelegate`.
- Create a basic `CPListTemplate` to ensure CarPlay connectivity.

### Phase 4: POI Display
- Implement `CPPointOfInterestTemplate`.
- Connect the shared `PoiProvider` to the CarPlay interface.
- Add "Navigate" button integration using `MKMapItem.openInMaps()`.

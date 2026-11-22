# IK TopoCalc — Android + Topographic Engine

**Application version:** 0.5.0 (see CHANGES_v0.5.md, CHANGES_v0.4.md, CHANGES_v0.3.md)  
**Engine:** 0.5.0 (included as the `:topo-engine` Kotlin/JVM module)

## Architecture

The Android application (Jetpack Compose, Room) depends on `:topo-engine`, a platform-independent Kotlin/JVM module. Survey mathematics is kept out of Compose UI code and can be verified without Android.

## Screens

| Tab | Content |
|---|---|
| Niveau | Optical leveling (open/closed line, k·√L tolerance, ΣLA−ΣLV check, stadia), PDF/Excel export |
| Théodolite | Horizontal angle, slope-distance reduction, curvature+refraction, inclined stadia |
| Calculs | Rayonnement + inverse · **Cheminement** (encadré / fermé / ouvert, angular + Bowditch) · **Implantation** (V0, Hz, Dh, ΔZ) |
| Coord. | **Lambert Maroc (Merchich) ↔ WGS84** (4 zones, EPSG:1166 or local 7-parameter Helmert), map view, WGS84 export, H = h − N |
| Projet | Projects, **points (CSV import/export)**, **saved calculations (reopen)** |

Angles can be entered in degrees or grads (switch in the top bar); typed values are converted when switching.

## Data

Room database version 2 (`project_points`, `saved_calculations`) with a migration from version 1: projects created with 0.2/0.3 are kept. Point names are unique per project; importing or saving a point with an existing name replaces it.

CSV import: separators `;`, `,`, tab or spaces; decimal comma accepted (except with `,` separator); optional header; columns Name X Y [Z] [Code] or Name Y X [Z] [Code]; UTF-8 or Windows-1252. Export: `Nom;X;Y;Z;Code`, UTF-8 with BOM (Excel).

## Coordinate conventions

- Projected metric coordinates for planar calculations: X = Easting, Y = Northing.
- Gisement measured clockwise from grid North (+Y); `dX = D × sin(G)`, `dY = D × cos(G)`.
- Traverse / stakeout angles: clockwise horizontal circle readings.
- Vertical angle alpha from horizontal, positive upward; zenith Z uses alpha = 90° − Z.
- Lambert Maroc zones (EPSG 26191, 26192, 26194, 26195): Lambert Conic Conformal 1SP on Clarke 1880 (IGN), central meridian −6 gr (−5.4°), parameters from the EPSG dataset.
- Merchich → WGS84 default: EPSG:1166 geocentric translations (31, 146, 47 m), published accuracy 7 m — NOT suitable for stakeout or cadastral work; enter local parameters for that.

## Verification

`VERIFICATION.txt` — 34 engine test groups (incl. two-face CG/CD reduction), including the official IOGP/EPSG worked examples for Lambert Conic Conformal 1SP (method 9801), geographic↔geocentric (9602) and 7-parameter Helmert (9606/9607), traverse closure with injected errors, and CSV formats. App-level adapter tests (leveling compensation, Excel writer, saved-calculation codec, file encoding) also pass. The Compose UI source was type-checked against stubs of the Compose/Android APIs; the migration SQL was executed on SQLite.

## Build in Android Studio

Open this folder as a Gradle project with JDK 17 and Android SDK 35 (the Gradle 8.9 wrapper is included). Sync Gradle, then build the `app` configuration, or run `./gradlew :app:assembleDebug`. Engine tests: `./gradlew :topo-engine:verifyEngine`. The Android application has not been built or run on an emulator/device in this environment: this ZIP is source code, not an installable APK.

## Not yet wired to screens

TIN creation, breaklines, contours, profiles, surface comparison, volumes, DXF export, intersections/resection and polygon area are present in the engine API but still need dedicated screens.

## Engineering caution

A passing software test suite does not replace validation against survey-control datasets and instrument field observations. Confirm CRS, datum transformation parameters, geoid, instrument constants, angular conventions and project tolerances before professional or contractual use.

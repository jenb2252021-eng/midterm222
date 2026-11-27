# The Lorax · Baguio Forest Registry

The Lorax is a JavaFX desktop registry for reforestation teams on a shared local network. The server stores the records, photos and review decisions. A ranger places a pin, records measurements and submits a tree. An administrator reviews the submission before it appears in the inventory.

## Run in VS Code

1. Open this folder with JDK 25 and the Java Extension Pack installed. Allow Maven project import to finish.
2. Open **Run and Debug**, select **The Lorax · Demonstration**, and press **F5**. This profile uses the separate `demo-data` folder. **The Lorax · Existing records** opens the original data. Stop other Lorax servers before switching datasets because clients automatically discover a running server.
3. On the host, enter administrator setup. For a new dataset, use the reserved name **admin** and choose a password of at least eight characters. Start the server and open the registry. For an existing dataset, sign in with the existing account. The updated app migrates its passwords to salted hashes on the server.
4. On another laptop on the same LAN, choose **The Lorax · Join a server**, connect to the host, and use **New ranger? Create an account**. If discovery fails, use the host address shown by the launcher. The default RMI registry port is 1099, the services use 1100–1103, and discovery uses UDP 50000. Both laptops must run the same version.

The Maven alternative for the existing dataset is `.\mvnw.cmd javafx:run` (macOS/Linux: `./mvnw javafx:run`) from the project directory with JAVA_HOME set to JDK 25. The project uses JavaFX 25, which ships the `jdk.jsobject` module that newer JDKs no longer include. Initial dependency setup can require internet. For maps without internet, select **Offline** within the bundled Baguio coverage. The local server must remain reachable for registry operations. RMI traffic is unencrypted.

## Dashboard

The **Registry** is the dashboard, and everything on it works with the live registry data (it refreshes every 5 seconds; the badge next to the title shows the last update).

- **Status tiles** (Total, Healthy, Monitoring, At Risk, Destroyed) are shortcuts: click one to filter the inventory to those trees.
- **Forest insights** charts are built from the records: health distribution, top species and plantings per year. Hover for exact numbers; click a slice, legend entry or bar to filter the inventory by that status, species or planting year.
- **Inventory**: search by species or ID, status chips with counts, sort (newest, species, tallest, needs attention first, planting date), **Cards** or a sortable **Table** view, and pages of 12 cards. "Clear filters" resets everything.
- **Cards and table rows**: click (or Enter) for details, **View in 3D**, edit and delete for records you own, and a right-click menu with the same actions plus "Copy coordinates".
- **Feedback**: saves, approvals and deletions show a short notification in the corner; errors still open a dialog; deleting, rejecting and signing out ask for confirmation. The Register Sapling form checks each field as you go and explains what to fix.
- **Sidebar**: the current page is highlighted, the Administration section folds away, and the ☰ button (or a window narrower than 1150 px) switches to an icon-only sidebar with tooltips.

## 3D Forest Map

The **3D Map** workspace shows every registered tree as an interactive 3D model, standing at its recorded coordinates on a 3D map of Baguio in a modern GIS style: OpenStreetMap basemap, beige 3D buildings, real hill terrain, a blue sky with horizon haze, and the same south-southeast default view. Open it from the sidebar, from **View 3D Forest Map** on the registry, or from **View in 3D** on any tree card. It replaces the old 2D Field Map and keeps all of its features: search by species or ID, the status filter with a live count, online/offline tiles, the health legend, and each tree's photo and record.

- **Map layers** (the **Layers** menu): basemap style (OpenStreetMap or Satellite), 3D buildings, terrain, tree labels and tree size. Buildings are OpenStreetMap footprints (via the free Overpass API) raised to their tagged height or floors × 3.1 m, coloured beige by height; they hide on satellite imagery. Terrain comes from open elevation tiles (Mapzen/AWS). Tiles, terrain, buildings and trees share one coordinate system, so each tree stands exactly at its pin. Zooming in loads sharper tiles around the view, and a scale bar shows distances.
- **3D trees from the registry records.** The model's shape follows the species: pines and other conifers get layered crowns, palms get fronds, and other trees get broadleaf crowns. Its size follows the recorded height and trunk diameter. Health shows in the foliage and in a coloured ground ring. Healthy trees are full and green. Monitored trees start yellowing. At-risk trees are thin and brown. Destroyed trees are a snapped stump with a fallen log. A map pin in the same colour keeps each tree findable when zoomed out.
- **Navigation.** Left-drag pans. Right-drag (or Ctrl/Shift + drag) rotates and tilts. Scroll zooms toward the cursor. Double-click flies to a spot. On the keyboard, arrows/WASD pan, Q/E rotate, +/- zoom and Esc clears the selection. The on-screen controls are a compass (click to face north), zoom, rotate, a 2D/3D tilt toggle and **Fit all trees**.
- **Monitoring.** Click a tree, or pick it from the list, to highlight it and fly to it. The card shows its location, the health status (highlighted), ID, species, height, trunk diameter, age since planting, planted date, coordinates, recorder, field notes, photo and **monitoring history**. **Open record**, **Edit tree** and **Show in registry** use the existing registry dialogs and permissions. Saved edits appear on the map within 5 seconds.
- **Offline.** Downloaded tiles, terrain and buildings are cached under `data/cache/osm`. **Offline** uses only the cache (and the bundled tiles for the OpenStreetMap style). The trees and their pins always appear, even with no map data.

The basemap is the standard OpenStreetMap tile service (no API key; follow its tile usage policy, which the app does by caching tiles and limiting downloads). Satellite imagery is Esri World Imagery; check its terms before any commercial use.

The history comes from a read-only `TreeService.getTreeHistory` call over the server activity log. Edits now record what changed (for example `Status: Healthy → Monitoring · Height: 3.4 → 3.6 m`). The tree records and JSON format are unchanged. The client and server must both run this version.

The map renders with JavaFX 3D on the GPU. If a graphics driver draws it with artefacts, start the client with `-Dlorax.map3d.antialiasing=false`. Check it with `verification/run.ps1 -Map3D`, which also writes `docs/screens/3d-map-*.png`.

## Three-minute presentation

- Open the demo inventory. Its four tree records are fictional examples based on the Figma prototype. Counts and map pins come from the records. Unphotographed trees show a leaf placeholder.
- As a ranger, choose **Register Sapling**. Enter species, measurements and planting date. Attach a tree photo you have permission to show, place a pin inside Baguio, and choose **Log to Forest**.
- Open **My submissions** as the ranger. Show the pending entry. On the admin client, open **Pending Review**, inspect the measurements and photo, then approve it.
- Return to **Registry**, search the species or ID, and show the approved record on the **3D Map** with **View in 3D**. Open **System States** on the admin client to see the server activity log.

The old paper's Chapter 5 results describe its November 7 build. They are historical results and should be updated after your team exercises this version on its presentation laptops.

## Final project materials

- [BRD addendum and verification matrix](docs/BRD-addendum.md)
- [Defense guide and two-laptop rehearsal](docs/Defense-guide.md)

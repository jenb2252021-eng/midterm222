Offline raster tiles (OpenStreetMap style)
==========================================

Leaflet loads local PNG files bundled under /map_web/tiles/:

  {z}/{x}/{y}.png

The Java app passes absolute jar:/file: URLs into the WebView so tiles resolve correctly
(relative paths often fail when field_map.html is loaded from a JAR).

Quick setup (Windows, from project root)
----------------------------------------

1) Grey placeholder for missing tiles:

   powershell -ExecutionPolicy Bypass -File scripts/create-grey-tile.ps1

2) One world tile at zoom 0 (optional sample):

   powershell -ExecutionPolicy Bypass -File scripts/download-sample-world-tile.ps1

3) Full command used for bulk download (respect OSM tile policy; rate-limited in script):

   powershell -ExecutionPolicy Bypass -File scripts/download-osm-tiles.ps1

Optional: high zoom in one region only (e.g. Baguio) without downloading the whole planet at z14
------------------------------------------------------------------------------------------------
Use -BoundingBox with minLon,minLat,maxLon,maxLat (WGS84). Example — Baguio area, zoom 10–14 only:

   powershell -ExecutionPolicy Bypass -File scripts/download-osm-tiles.ps1 `
     -MinZoom 10 -MaxZoom 14 -BoundingBox "120.4,16.3,120.7,16.5"

Extra detail for the same box (adds z15–16; run after the above or alone; takes several minutes):

   powershell -ExecutionPolicy Bypass -File scripts/download-osm-tiles.ps1 `
     -MinZoom 15 -MaxZoom 16 -BoundingBox "120.4,16.3,120.7,16.5"

The in-app offline map shows a notice: tiles are for Baguio and surroundings only; panning or zooming
elsewhere shows grey placeholder tiles.

Add lower zooms (0–9) for the same box if you need context when zooming out:

   powershell -ExecutionPolicy Bypass -File scripts/download-osm-tiles.ps1 `
     -MinZoom 0 -MaxZoom 14 -BoundingBox "120.4,16.3,120.7,16.5"

Full world, zoom 0–6 only (~5 400 tiles, tens of MB, long runtime):

   powershell -ExecutionPolicy Bypass -File scripts/download-osm-tiles.ps1 -MinZoom 0 -MaxZoom 6

After downloading, rebuild the project so target/classes/map_web/tiles/ contains the PNGs.

OpenStreetMap tile policy
-------------------------
https://operations.osmfoundation.org/policies/tiles/

Use for personal / educational offline caching only. The script sends a descriptive User-Agent
and delays between requests.

client_config.properties: map.offline.tiles=true uses this folder instead of live OSM.

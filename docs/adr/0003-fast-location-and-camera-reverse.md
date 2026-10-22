# ADR 0003: Fast location on camera open

## Status
Accepted

## Context
Commercial watermark cameras show a place almost immediately. Waiting for every provider and then reverse-geocoding (including Nominatim) made open feel like 4–5 seconds.

## Decision
- Emit progressive coordinate fixes: last-known / first usable network fix first; allow a better GPS fix within a shared ~2s window.
- Reverse-geocode asynchronously after coordinates; `locating` ends when coordinates are available.
- Camera-open reverse uses AMap then Android Geocoder only (`Geocoders.cameraReverse`); Nominatim stays off this path.
- Persist the last successful automatic place+coords for cold-start display.
- Persist the last saved MediaStore URI for the camera thumbnail across process death; clear it if the file is gone.

## Consequences
Settings "use current location" still uses `LocationSource.current()` (first usable fix + reverse). Weather starts as soon as coordinates exist.

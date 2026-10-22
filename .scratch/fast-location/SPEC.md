# Fast location (commercial-app style) — for next release v0.2.2

Do NOT bump versionCode/versionName or publish. Implement + unit tests only.

## Goal
Opening the camera should show a place almost immediately (like commercial watermark cameras), then refine. Today `PlatformLocationSource.current()` waits up to 10s for ALL providers, then reverse-geocodes synchronously (AMap → Android → Nominatim), and only then does the ViewModel update UI / clear `locating`.

## Required behavior
1. On open/resume: immediately show last-known place+coords from SettingsStore (if any), or lastKnown/network without waiting for GPS.
2. Do not wait for both GPS+network to finish before first UI update: first usable fix updates UI; a better GPS fix later may refine place/coords.
3. Reverse geocode async: show previous place text or a soft placeholder with coords immediately; update place text when reverse returns. Do NOT keep `locating` spinner waiting on reverse geocode.
4. Cap waiting for a fresh GPS fix (~1.5–2s). After that, proceed with network or lastKnown.
5. Critical path reverse geocode must NOT chain long Nominatim after AMap/Android failure — fail soft ("地址暂不可用（可手动设置）" or keep previous place). Nominatim may remain for non-critical forward search / settings if already wired, but not on camera open.
6. Weather runs in parallel once coordinates are known (already partly true; keep/improve: don't wait for address).
7. Persist last successful auto place+coords in SettingsStore so next cold start can show instantly.
8. `currentLocation()` used by Settings "use current location" can still return a complete reading, but should use the fast fix path (not the old 10s-all-providers block).

## Suggested design (prefer deep modules, small interfaces)
- Pure helpers for race/priority logic (easy to unit test without Robolectric where possible).
- `PlatformLocationSource` emits progressive fixes OR exposes a fast `obtainFix` + separate reverse; ViewModel orchestrates progressive UI.
- SettingsStore: `loadLastLocation()` / `saveLastLocation(place, coords)` (auto-observed only; not the manual override).
- Keep locating=true only while waiting for first usable coordinates (cache counts as already having coords → locating can end quickly or stay briefly until first fresh/network fix). Prefer: locating ends once we have coords to show (cache or first fix), not when address arrives.

## Tests
- Unit tests for race/priority: first usable wins; GPS refine; GPS wait cap → network/lastKnown; reverse not required for "fix ready".
- Update/adapt PlatformLocationSourceTest for new timing (no 10s shared wait-for-all).
- ViewModel / SettingsStore tests for last-location persist + immediate show.
- Keep `./gradlew testDebugUnitTest` green. No release, no git push, no version bump.

## Out of scope
- Version bump to 0.2.2 / versionCode 4
- GitHub/Gitee release
- UI redesign beyond spinner/place text behavior

## Added: persist last thumbnail (same next release)
Bug: after capture, bottom-left thumbnail works; after killing/reopening the app the thumbnail is empty.
- Persist last saved MediaStore URI (string) in SettingsStore across process death.
- On ViewModel init / launch, restore into `lastSaved` if the URI is still readable; if the file was deleted or unreadable, clear the persisted value.
- Add a unit test covering restore + clear-on-missing.
- Release notes line later: 重启后仍显示最近拍摄缩略图.

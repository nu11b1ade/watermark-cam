#!/usr/bin/env bash
set -euo pipefail

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

if [[ $# -lt 2 || $# -gt 3 ]]; then
  printf 'Usage: scripts/release.sh <versionCode> <versionName> [notes-file]\n' >&2
  exit 2
fi

for tool in sed python3 sha256sum; do
  command -v "$tool" >/dev/null || die "Required tool not found: $tool"
done

version_code="$1"
version_name="$2"
[[ "$version_code" =~ ^[0-9]+$ ]] || die 'versionCode must be a positive integer'
version_pattern='^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$'
[[ "$version_name" =~ $version_pattern ]] || die 'versionName must look like 0.2.0 or 0.2.0-rc.1'

# Resolve a caller-relative notes path before switching to the repository root.
notes_file=''
if [[ $# -eq 3 ]]; then
  [[ -f "$3" && -r "$3" ]] || die "Cannot read notes file: $3"
  notes_file="$(python3 - "$3" <<'PY'
import pathlib
import sys
path = pathlib.Path(sys.argv[1]).resolve()
path.read_text(encoding="utf-8")  # Validate the notes before changing the version or building.
print(path)
PY
)"
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(dirname -- "$script_dir")"
cd -- "$repo_root"
gradle_file='app/build.gradle.kts'
current_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$gradle_file")"
current_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*$/\1/p' "$gradle_file")"
[[ "$current_code" =~ ^[0-9]+$ && -n "$current_name" && "$current_name" != *$'\n'* ]] || die 'Cannot identify a single current version in app/build.gradle.kts'

# Python avoids shell integer overflow and normalizes inputs such as 0002 before sed writes Kotlin.
version_code="$(python3 - "$version_code" "$current_code" <<'PY'
import sys
new, current = map(int, sys.argv[1:])
if not current < new <= 2_147_483_647:
    sys.exit(f"versionCode must be greater than {current} and at most 2147483647")
print(new)
PY
)"

sed -i -E \
  -e "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+[[:space:]]*$/\1${version_code}/" \
  -e "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*)\"[^\"]*\"[[:space:]]*$/\1\"${version_name}\"/" \
  "$gradle_file"

./gradlew testDebugUnitTest assembleRelease

apk='app/build/outputs/apk/release/app-release.apk'
[[ -f "$apk" ]] || die 'app-release.apk was not produced. Configure release signing (see docs/release.md).'
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
apksigner=''
for candidate in "$sdk_dir"/build-tools/*/apksigner; do
  if [[ -x "$candidate" ]]; then apksigner="$candidate"; fi
done
if [[ -n "$apksigner" ]]; then
  "$apksigner" verify "$apk" || die 'APK signature verification failed'
else
  printf 'Warning: apksigner not found under %s/build-tools; verify the signature before uploading.\n' "$sdk_dir" >&2
fi

mkdir -p dist
destination="dist/watermark-cam-${version_name}.apk"
cp -- "$apk" "$destination"
sha256="$(sha256sum -- "$destination")"
sha256="${sha256%% *}"

python3 - "$version_code" "$version_name" "$notes_file" "$sha256" <<'PY'
import json
import pathlib
import re
import sys

code, version, notes_file, sha256 = sys.argv[1:]
notes = pathlib.Path(notes_file).read_text(encoding="utf-8") if notes_file else "修复问题，提升稳定性"
# Use the app's single endpoint configuration so changing the Gitee owner cannot drift here.
source = pathlib.Path("app/src/main/java/com/nu11b1ade/watermarkcam/update/UpdateConfig.kt").read_text(encoding="utf-8")
config = dict(re.findall(r'^\s*const val ([A-Z_]+) = "([^"]+)"', source, re.MULTILINE))
github = config["GITHUB_BASE"]
gitee = config["GITEE_BASE"].replace("$GITEE_OWNER", config["GITEE_OWNER"])
asset = f"releases/download/v{version}/watermark-cam-{version}.apk"
manifest = {
    "versionCode": int(code),
    "versionName": version,
    "notes": notes,
    "apkUrls": {"github": f"{github}/{asset}", "gitee": f"{gitee}/{asset}"},
    "sha256": sha256,
}
pathlib.Path("dist/latest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

printf '\nAPK: %s\nManifest: dist/latest.json\nSHA-256: %s\n' "$destination" "$sha256"
printf '\nNext manual steps:\n'
printf '1. Create GitHub release v%s and upload %s plus dist/latest.json. Mark it as the latest release.\n' "$version_name" "$destination"
printf '2. Upload the same two files to Gitee release v%s.\n' "$version_name"
printf '3. Replace both assets on the rolling Gitee release tagged latest (create it once if needed).\n'
printf '4. Verify both latest.json endpoints and the APK downloads. See docs/release.md.\n'

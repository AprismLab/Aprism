#!/usr/bin/env bash
# Prepares the real-game smoke environment under build/smoke:
#   - downloads the Minecraft 26.2 client jar + all runtime libraries
#   - downloads + extracts the native LWJGL libraries (windows/x64)
#   - downloads the asset index + objects needed for a headless-ish boot
#   - scaffolds a game directory with the sample .aje mod
# Idempotent: re-running only fetches what is missing.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SMOKE_DIR="$REPO_ROOT/build/smoke"
GAMEDIR="$SMOKE_DIR/gamedir"
MC_VERSION="26.2"
MANIFEST="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

mkdir -p "$SMOKE_DIR" "$GAMEDIR/mods" "$SMOKE_DIR/libs"

echo "Smoke-env: fetching version manifest..."
curl -fsSL "$MANIFEST" -o "$SMOKE_DIR/manifest.json"

URL="$(python3 - "$SMOKE_DIR/manifest.json" "$MC_VERSION" <<'PY'
import json, sys
m = json.load(open(sys.argv[1]))
for v in m["versions"]:
    if v["id"] == sys.argv[2]:
        print(v["url"]); break
PY
)"
[ -n "$URL" ] || { echo "version $MC_VERSION not in manifest" >&2; exit 1; }
curl -fsSL "$URL" -o "$SMOKE_DIR/version.json"
echo "Smoke-env: version.json fetched."

python3 - "$SMOKE_DIR" "$GAMEDIR" <<'PY'
import json, os, sys, zipfile, hashlib, urllib.request
smoke, gamedir = sys.argv[1], sys.argv[2]
vj = json.load(open(os.path.join(smoke, "version.json")))

def fetch(url, dest, sha1=None):
    if os.path.exists(dest) and (sha1 is None or hashlib.sha1(open(dest,'rb').read()).hexdigest()==sha1):
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    urllib.request.urlretrieve(url, dest)

# client jar
ci = vj["downloads"]["client"]
fetch(ci["url"], os.path.join(smoke,"client.jar"), ci.get("sha1"))
print("client.jar ok")

# runtime libraries
libs_dir = os.path.join(smoke, "libs")
for lib in vj["libraries"]:
    rules = lib.get("rules")
    if rules:
        allow = False
        for r in rules:
            osname = r.get("os",{}).get("name")
            if osname and osname != "windows":
                if r["action"]=="allow": allow=False
                continue
            allow = r["action"]=="allow"
        if not allow: continue
    art = lib.get("downloads",{}).get("artifact")
    if art:
        fetch(art["url"], os.path.join(libs_dir, art["path"]), art.get("sha1"))
    natives = lib.get("downloads",{}).get("classifiers",{})
    if "natives-windows" in natives:
        n = natives["natives-windows"]
        tmp = os.path.join(libs_dir, "_native_win.zip")
        fetch(n["url"], tmp, n.get("sha1"))
        out = os.path.join(smoke, "natives", "windows", "x64")
        os.makedirs(out, exist_ok=True)
        with zipfile.ZipFile(tmp) as z:
            for name in z.namelist():
                if name.endswith(".dll"):
                    with z.open(name) as src, open(os.path.join(out, os.path.basename(name)),"wb") as dst:
                        dst.write(src.read())
print("libraries + natives ok")

# asset index + objects (best effort: index only is enough to boot)
aidx = vj.get("assetIndex", {})
if aidx.get("url"):
    idx_path = os.path.join(gamedir,"assets","indexes", f"{aidx.get('id','')}.json")
    fetch(aidx["url"], idx_path, aidx.get("sha1"))
    try:
        idx = json.load(open(idx_path))
        objs = idx.get("objects",{})
        fetched=0
        for name, meta in objs.items():
            h = meta["hash"]
            dest = os.path.join(gamedir,"assets","objects",h[:2],h)
            if not os.path.exists(dest):
                urllib.request.urlretrieve(f"https://resources.download.minecraft.net/{h[:2]}/{h}", dest)
                fetched+=1
            if fetched>=0 and len(os.listdir(os.path.join(gamedir,'assets','objects')))>=0:
                pass
        print(f"assets ok ({len(objs)} indexed)")
    except Exception as e:
        print(f"assets best-effort: {e}")
print("smoke env prepared")
PY

# Sample mod: use the examplemod .aje if the packaging task has produced one,
# otherwise remind the user.
if [ ! -f "$GAMEDIR/mods/examplemod-1.0.0.aje" ]; then
  echo "NOTE: no sample .aje in $GAMEDIR/mods. Build examplemod and copy its .aje here."
fi
echo "Smoke-env: done. Game dir: $GAMEDIR"

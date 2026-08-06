# Aprism Mods Pack (.aje/.abe) Classification, Structure and Per-Platform Placement

> Document 7 of 8 | Aprism Loader Documentation Set
> Version: v26.0-Alpha1-Phase0 | Status: Development
> Author: BlockConnect@StarsailsClover
> Canonical language: English (Chinese copy maintained in parallel)

## 1. Executive Summary

Aprism ships mods in two unified pack formats: `.aje` (Aprism Java Edition pack) for Java Edition content loaded by the Aprism javaagent, and `.abe` (Aprism Bedrock Edition pack) for Bedrock Edition content loaded by the Aprism native loader. Both formats are ZIP containers carrying `aprism.manifest.json` at the root plus edition-specific payloads. A single artifact is installable by copy, by launcher-mediated placement, or by double-click import, and is self-describing enough to be validated before any class or native binary is touched.

This document is the canonical reference for pack classification, internal structure, manifest placement, and the exact per-platform directory locations where packs are installed. It governs launcher integrations, installer tooling, and third-party packagers. The placement paths recorded here are normative: a launcher that deviates from them breaks load discovery and is non-conformant.

## 2. Pack Classification

Aprism recognizes two top-level pack extensions, both backed by ZIP. The extension selects which Aprism subsystem consumes the pack; the manifest `type` field selects the language runtime and entrypoint contract.

### 2.1 .aje - Aprism Java Edition Pack

A `.aje` pack is a ZIP containing `aprism.manifest.json` at the root, one or more Java archives (`<modid>.jar` or per-loader jars), optional platform subdirectories (`fabric/`, `forge/`, `neoforge/`, `quilt/`, `liteloader/`), shared `resources/`, mixin configs under `mixins/`, and optional bundled libraries under `lib/`. The Aprism javaagent scans the active instance's `mods/` directory, opens each `.aje` (and any co-located `.jar`), validates the manifest, and registers the mod with the loader core.

### 2.2 .abe - Aprism Bedrock Edition Pack

A `.abe` pack is a ZIP containing `aprism.manifest.json` at the root plus a Bedrock-shaped payload: `behavior_pack/` and `resource_pack/` subdirectories (each carrying its own Bedrock `manifest.json` for native Bedrock compatibility), an optional `scripts/` directory for Script API mods, and an optional `native/` directory containing per-platform native mod binaries (`windows-x64`, `android-arm64`, `android-armv7`, `ios-arm64`, `linux-x64`). The Aprism native loader consumes the pack: script components are installed into the standard Bedrock `behavior_packs/` tree and registered per-world, while native components are installed into the Aprism-introduced `aprism_mods/` directory under `com.mojang/`.

### 2.3 Sub-classification by `type`

The manifest `type` field selects the runtime and entrypoint contract:

| `type` value | Runtime | Typical payload | Notes |
|---|---|---|---|
| `native` | C++ / Rust | `native/<platform>/<modid>.{dll,so,dylib}` | BE only; loaded by Aprism native loader |
| `script` | JavaScript / TypeScript | `scripts/*.js` (+ sourcemaps) | BE only; standard Bedrock Script API |
| `hybrid` | Native + Script | `native/` + `scripts/` | BE only; native binary calls into Script API |
| `converted` | Java projected to BE | `behavior_pack/` synthesized from JE assets | BE only; output of JE-to-BE conversion |
| `java` (default for `.aje`) | JVM | `<modid>.jar` + per-loader jars | JE only |

### 2.4 Comparison with adjacent formats

| Property | `.aje` | `.abe` | `.jar` | `.mcpack` | `.mcaddon` |
|---|---|---|---|---|---|
| Container | ZIP | ZIP | ZIP | ZIP | ZIP |
| Consumer | Aprism javaagent | Aprism native loader | Loader-specific (Fabric/NeoForge/etc.) | Bedrock engine | Bedrock engine |
| Manifest | `aprism.manifest.json` | `aprism.manifest.json` | `fabric.mod.json` / `neoforge.mods.toml` / `META-INF/MANIFEST.MF` | `manifest.json` | `manifest.json` (one or more) |
| Multi-loader | yes (per-loader subdirs) | n/a | no | n/a | n/a |
| Native binaries | no | yes (`native/`) | no | no | no |
| Script API | no | yes (`scripts/`) | no | yes (BP) | yes (BP) |
| Target edition | JE | BE | JE | BE | BE |
| Aprism-native | yes | yes | consumed via fallback | not consumed | not consumed |

## 3. .aje Pack Structure

### 3.1 Full directory tree (canonical)

```
my-mod-1.0.0.aje                              (ZIP container)
|
+-- aprism.manifest.json                       (REQUIRED, at ZIP root)
+-- my-mod.jar                                 (REQUIRED if no per-loader jar; main mod code)
+-- icon.png                                   (optional, pack icon)
|
+-- fabric/                                    (optional, Fabric-specific jar)
|   +-- my-mod-fabric.jar
|
+-- neoforge/                                  (optional, NeoForge-specific jar)
|   +-- my-mod-neoforge.jar
|
+-- forge/                                     (optional, Forge-specific jar)
|   +-- my-mod-forge.jar
|
+-- quilt/                                     (optional, Quilt-specific jar)
|   +-- my-mod-quilt.jar
|
+-- liteloader/                                (optional, LiteLoader legacy)
|   +-- my-mod.litemod
|
+-- resources/                                 (optional, shared assets)
|   +-- assets/
|   |   +-- my-mod/
|   |       +-- textures/
|   |       +-- lang/
|   |       +-- models/
|   +-- data/
|   |   +-- my-mod/
|   |       +-- recipes/
|   |       +-- loot_tables/
|   +-- pack.mcmeta                            (resource-pack metadata, if shipped as RP)
|
+-- mixins/                                    (optional, Mixin configs)
|   +-- my-mod.mixins.json
|   +-- my-mod.client.mixins.json
|
+-- lib/                                       (optional, bundled dependencies, JiJ-style)
    +-- net/
        +-- example/
            +-- somelib/
                +-- somelib-2.1.0.jar
```

### 3.2 Component reference

| Path | Required | Purpose |
|---|---|---|
| `aprism.manifest.json` | yes | Manifest; the only file Aprism reads first |
| `<modid>.jar` or `<modid>-common.jar` | yes (one of) | Main mod code; used when no per-loader jar matches the active loader |
| `fabric/<modid>-fabric.jar` | no | Fabric-specific overrides and entrypoints |
| `neoforge/<modid>-neoforge.jar` | no | NeoForge-specific overrides and entrypoints |
| `forge/<modid>-forge.jar` | no | Forge-specific overrides and entrypoints |
| `quilt/<modid>-quilt.jar` | no | Quilt-specific overrides and entrypoints |
| `liteloader/<modid>.litemod` | no | LiteLoader legacy mod (zip with `litemod.json`) |
| `resources/` | no | Shared assets merged into the game's virtual filesystem |
| `mixins/` | no | Mixin config JSONs referenced from the manifest `mixins` array |
| `lib/` | no | Bundled libraries, JiJ-style; added to the mod's isolated classloader |
| `icon.png` | no | Pack icon surfaced in launchers and Aprism UI |

### 3.3 Tree examples

**Single-loader mod (Fabric only):**

```
simple-mod-1.0.0.aje
+-- aprism.manifest.json
+-- simple-mod.jar
+-- mixins/
    +-- simple-mod.mixins.json
```

**Multi-loader mod (Fabric + NeoForge + Forge):**

```
cross-loader-2.3.1.aje
+-- aprism.manifest.json
+-- cross-loader-common.jar
+-- fabric/
|   +-- cross-loader-fabric.jar
+-- neoforge/
|   +-- cross-loader-neoforge.jar
+-- forge/
|   +-- cross-loader-forge.jar
+-- resources/
|   +-- assets/
+-- mixins/
    +-- cross-loader.mixins.json
```

**Mod with native components (rare for `.aje`; for example a JNI shim):**

```
jni-shim-0.4.0.aje
+-- aprism.manifest.json
+-- jni-shim.jar
+-- lib/
|   +-- native/
|       +-- windows-x64/
|       |   +-- jni-shim.dll
|       +-- linux-x64/
|           +-- jni-shim.so
+-- mixins/
    +-- jni-shim.mixins.json
```

## 4. .abe Pack Structure

### 4.1 Full directory tree (canonical)

```
my-be-mod-1.0.0.abe                           (ZIP container)
|
+-- aprism.manifest.json                       (REQUIRED, at ZIP root - Bedrock superset)
|
+-- behavior_pack/                             (optional, standard Bedrock BP)
|   +-- manifest.json                          (REQUIRED if BP present; Bedrock native)
|   +-- pack_icon.png
|   +-- entities/
|   +-- items/
|   +-- blocks/
|   +-- loot_tables/
|   +-- trading/
|   +-- scripts/                               (Script API JS/TS, if script-type mod)
|   |   +-- main.js
|   |   +-- main.js.map
|   +-- texts/
|       +-- en_US.lang
|       +-- languages.json
|
+-- resource_pack/                             (optional, standard Bedrock RP)
|   +-- manifest.json                          (REQUIRED if RP present; Bedrock native)
|   +-- pack_icon.png
|   +-- textures/
|   +-- models/
|   +-- sounds/
|   +-- texts/
|
+-- scripts/                                   (optional, top-level Script API for hybrid/converted)
|   +-- main.js
|
+-- native/                                    (optional, per-platform native binaries)
|   +-- windows-x64/
|   |   +-- my-be-mod.dll
|   +-- android-arm64/
|   |   +-- my-be-mod.so
|   +-- android-armv7/
|   |   +-- my-be-mod.so
|   +-- ios-arm64/
|   |   +-- my-be-mod.dylib
|   +-- linux-x64/
|       +-- my-be-mod.so
|
+-- icon.png                                   (optional, Aprism-side pack icon)
```

### 4.2 Component reference

| Path | Required | Purpose |
|---|---|---|
| `aprism.manifest.json` | yes | Aprism manifest; Bedrock superset carrying the `aprism` object |
| `behavior_pack/` | no (script/hybrid/converted) | Standard Bedrock behavior pack; carries its own `manifest.json` |
| `behavior_pack/scripts/` | no | Script API sources for `script` or `hybrid` packs |
| `resource_pack/` | no | Standard Bedrock resource pack; carries its own `manifest.json` |
| `scripts/` | no | Top-level Script API sources for `hybrid`/`converted` packs not embedded in the BP |
| `native/` | no | Per-platform native binaries for `native` or `hybrid` packs |
| `native/<platform>/` | conditional | One directory per target platform; required when `native/` is present |
| `icon.png` | no | Pack icon surfaced in Aprism UI |

### 4.3 Native platform directory names

| Directory | Target |
|---|---|
| `native/windows-x64/` | Windows UWP Bedrock (x64), Windows BDS |
| `native/android-arm64/` | Android 64-bit ARM (Android 12+ target) |
| `native/android-armv7/` | Android 32-bit ARM (legacy devices) |
| `native/ios-arm64/` | iOS via TrollStore (arm64) |
| `native/linux-x64/` | Linux BDS (x64) |

### 4.4 Tree examples

**Script API mod:**

```
script-farm-1.0.0.abe
+-- aprism.manifest.json
+-- behavior_pack/
    +-- manifest.json
    +-- scripts/
    |   +-- main.js
    +-- entities/
        +-- farmer_npc.json
```

**Native C++ mod:**

```
native-tweaks-0.9.0.abe
+-- aprism.manifest.json
+-- native/
    +-- windows-x64/
    |   +-- native-tweaks.dll
    +-- android-arm64/
    |   +-- native-tweaks.so
    +-- ios-arm64/
        +-- native-tweaks.dylib
```

**Hybrid mod (native + script):**

```
hybrid-combat-3.0.0.abe
+-- aprism.manifest.json
+-- behavior_pack/
|   +-- manifest.json
|   +-- scripts/
|   |   +-- combat.js
|   +-- entities/
+-- native/
    +-- windows-x64/
    |   +-- hybrid-combat.dll
    +-- android-arm64/
        +-- hybrid-combat.so
```

**JE-converted mod (output of JE-to-BE conversion):**

```
converted-je-mod-1.2.0.abe
+-- aprism.manifest.json
+-- behavior_pack/
|   +-- manifest.json
|   +-- items/
|   +-- blocks/
|   +-- recipes/
|   +-- scripts/
|       +-- converted-runtime.js
+-- resource_pack/
    +-- manifest.json
    +-- textures/
    +-- models/
```

## 5. Manifest Location Within Packs

### 5.1 .aje packs

The manifest is `aprism.manifest.json` at the ZIP root. Aprism opens the archive, lists the root entries, and reads the manifest directly. No subdirectory scan is performed for `.aje` packs; if the manifest is not at the root, the pack is rejected as malformed.

### 5.2 .abe packs

The Aprism manifest `aprism.manifest.json` is at the ZIP root. Alongside it, the standard Bedrock `manifest.json` files live inside `behavior_pack/` and `resource_pack/`. This dual-manifest design lets Bedrock engine consume the BP/RP natively (it only sees the Bedrock manifests) while Aprism reads the superset manifest for native binary, script, and hybrid information. The Aprism manifest's `aprism` object duplicates no field that Bedrock already requires; it adds Aprism-only metadata (native binary map, script entry, hybrid declaration, Aprism API range).

### 5.3 Discovery order

When Aprism encounters a pack (by directory scan or import event), it follows this discovery order:

1. Open the archive and look for `aprism.manifest.json` at the ZIP root.
2. If found, parse and schema-validate; this is authoritative.
3. If not found in a `.aje`, probe for legacy manifests in this order: `fabric.mod.json` (root or inside any jar), `META-INF/neoforge.mods.toml`, `META-INF/mods.toml`, `litemod.json`. The first hit is projected into an in-memory Aprism manifest.
4. If no manifest of any kind is found, the pack is rejected and logged.

## 6. Per-Platform Placement Guide (JE)

### 6.1 Placement table

| Platform | OS | Path | Notes |
|---|---|---|---|
| Vanilla launcher | Windows | `%APPDATA%\.minecraft\mods\` | Default Aprism install target |
| Vanilla launcher | macOS | `~/Library/Application Support/minecraft/mods/` | Aprism resolves `~` correctly |
| Vanilla launcher | Linux | `~/.minecraft/mods/` | Conventional location |
| Prism Launcher | any | `<instance>/mods/` | Per-instance isolation |
| MultiMC / PolyMC | any | `<instance>/mods/` | Same as Prism |
| ATLauncher | any | `<instance>/mods/` | Per-instance |
| CurseForge App | Windows | `<instance>/mods/` | Profile-scoped path |
| Android JE (PojavLauncher) | Android | `/sdcard/Android/data/net.kdt.pojavlaunch/files/.minecraft/mods/` | Path may vary by launcher build |
| Android JE (PojavLauncher, scoped storage) | Android 11+ | `/sdcard/Android/data/net.kdt.pojavlaunch/files/games/PojavLauncher/.minecraft/mods/` | Scoped-storage layout |

### 6.2 Aprism javaagent scan behavior

At `premain`, the Aprism javaagent resolves the active Minecraft instance root from the system property `aprism.instance.root` (set by the launcher) or falls back to the default `.minecraft` location for the host OS. It enumerates every file in `<instance>/mods/` ending in `.aje` or `.jar`. For `.aje`, the archive is opened in-place and the manifest is read without extraction. For `.jar`, the legacy discovery order (Section 5.3, step 3) is applied.

Mods are loaded in dependency order computed from the manifest `depends` graph. Mods with the same `id` at different versions are deduplicated; the higher SemVer wins, the lower is logged as shadowed. Mixing `.aje` and `.jar` is fully supported in the same `mods/` directory; Aprism treats both as first-class and merges their dependency graphs.

### 6.3 Mixing with traditional `.jar`

A user may keep existing Fabric `.jar` mods alongside new `.aje` packs. Aprism loads both. One rule: if a `.jar` and an `.aje` declare the same `id`, the `.aje` wins because it carries Aprism-only metadata the `.jar` cannot. The `.jar` is logged as shadowed.

## 7. Per-Platform Placement Guide (BE)

### 7.1 Placement table

| Platform | OS | `com.mojang` path | `aprism_mods` path | Notes |
|---|---|---|---|---|
| Bedrock UWP | Windows | `%LocalAppData%\Packages\Microsoft.MinecraftUWP_*\LocalState\games\com.mojang\` | `...\com.mojang\aprism_mods\` | Wildcard matches the versioned package family name |
| Bedrock Android | Android <= 10 | `/sdcard/games/com.mojang/` | `/sdcard/games/com.mojang/aprism_mods/` | Shared storage; no scoped-storage restrictions |
| Bedrock Android | Android 11 | `/sdcard/games/com.mojang/` | `/sdcard/games/com.mojang/aprism_mods/` | Scoped storage exempts `games/` for read; write needs MANAGE_EXTERNAL_STORAGE or SAF |
| Bedrock Android | Android 12+ | `/sdcard/Android/data/com.mojang.minecraftpe/files/games/com.mojang/` | `...\com.mojang\aprism_mods\` | App-specific external; no extra permission |
| Bedrock iOS | iOS | Minecraft container (Files app) | `Minecraft/aprism_mods/` inside the container | TrollStore required for native loader; script-only mods do not |
| Bedrock Dedicated Server | Windows | `<bds-root>\games\com.mojang\` | `<bds-root>\games\com.mojang\aprism_mods\` | BDS installation root |
| Bedrock Dedicated Server | Linux | `<bds-root>/games/com.mojang/` | `<bds-root>/games/com.mojang/aprism_mods/` | BDS installation root |

### 7.2 Where each component goes

**Native mod binaries (`.abe` `native/` payload):** extracted into `com.mojang/aprism_mods/<modid>/native/<platform>/`. The Aprism native loader scans `aprism_mods/` at startup, matches the running platform against `<platform>` directory names, and loads only the matching binary. `aprism_mods/` is Aprism-introduced and is not read by the Bedrock engine itself.

**Script API mod sources (`.abe` `behavior_pack/scripts/` or `scripts/`):** installed into `com.mojang/development_behavior_packs/<modid>/scripts/` (dev, hot-reload) or `com.mojang/behavior_packs/<modid>/scripts/` (stable). The BP is then registered per-world via `minecraftWorlds/<world>/world_behavior_packs.json` by appending the BP's UUID and version.

**Behavior and resource pack content:** installed into `behavior_packs/` and `resource_packs/` exactly as Bedrock expects; Aprism does not alter Bedrock's BP/RP loading.

### 7.3 Aprism native loader scan behavior

At injection time, the Aprism native loader enumerates `aprism_mods/*/aprism.manifest.json`. For each manifest, it validates the platform entry against the running host, opens `<modid>/native/<platform>/<modid>.<ext>`, verifies the signature if present (Section 10), and calls the loader entrypoint. Native mods are global; they are not per-world.

### 7.4 Bedrock engine loading of script parts

Script components are loaded by the Bedrock engine through the standard BP mechanism. Aprism only ensures the BP is on disk in `behavior_packs/` (or `development_behavior_packs/`) and that the BP UUID is registered in `world_behavior_packs.json` for each world where the mod should run. No Aprism-side hook is needed for script execution; once registered, Bedrock's own script runtime loads and runs the JS/TS.

## 8. Pack Installation Methods

### 8.1 Manual copy

The user copies the `.aje` or `.abe` file into the correct directory per Sections 6 and 7. No metadata update is required; Aprism discovers the pack on next launch. This is the lowest-friction method.

### 8.2 Launcher-mediated placement

Launchers that integrate with Aprism call the Aprism Installer API to place the pack and update world registration JSON. For BE script mods, the launcher appends the BP UUID to `world_behavior_packs.json` for the selected world(s). For BE native mods, the launcher extracts the binary into `aprism_mods/<modid>/native/<platform>/`. Launchers must resolve the active Minecraft package path before writing into `com.mojang/`.

### 8.3 Double-click import

Aprism registers file associations for `.aje` and `.abe` on Windows, macOS, and Linux desktops. Double-clicking a pack launches the Aprism Importer, which validates the pack, prompts for the target Minecraft instance (JE) or world scope (BE), and performs the install. On BE platforms without a desktop (Android, iOS), import is performed from within the Aprism companion app.

### 8.4 World-scoped vs global

- **JE:** all mods are global to the instance; no per-world scoping.
- **BE native mods:** global. `aprism_mods/` is not per-world; the native binary loads once at process start.
- **BE script mods:** world-scoped. The BP must be registered in `world_behavior_packs.json` for each world that should run the script. Aprism's installer can bulk-register across multiple worlds on request.

## 9. Pack Validation

Before any class or native binary is loaded, Aprism validates every pack through a fixed pipeline. Failure at any stage rejects the pack and logs the reason; remaining packs continue.

| Stage | Check | Failure action |
|---|---|---|
| 1. ZIP integrity | Open the archive; verify central directory and CRCs | Reject; log "corrupt archive" |
| 2. Manifest presence | `aprism.manifest.json` at root, or legacy manifest discoverable | Reject; log "no manifest" |
| 3. Manifest schema | JSON well-formed; required fields present; types valid | Reject; log schema error with field path |
| 4. Native platform match | For `.abe` `native/` payloads, the host platform directory exists | Skip native load; log "no binary for <platform>"; script-only fallback if hybrid |
| 5. Minecraft version range | `minecraft` range intersects the running Minecraft version | Reject; log "incompatible Minecraft version" |
| 6. Aprism API range | `aprismApi` range intersects the running Aprism API version | Reject; log "incompatible Aprism API" |
| 7. Java version (`.aje`) | `java` range intersects the running JDK version | Reject; log "incompatible Java" |
| 8. Dependency resolution | All `depends` entries resolvable in the mod set | Reject; log missing dependency |
| 9. Conflict check | No `breaks` entry matched by an installed mod | Reject; log conflict |
| 10. Signature (if signed) | Cosign signature valid against the bundle | Apply unsigned-mod policy (Section 10) |

## 10. Pack Signing (Optional but Recommended)

### 10.1 Cosign-signed bundles

Aprism recommends that pack authors sign `.aje` and `.abe` bundles with cosign keyless signing (the same mechanism used for Aprism release artifacts, see Document 1 Section 8). The signature is detached and shipped alongside the pack as `<pack>.sig` and `<pack>.bundle`. At load time, Aprism verifies the signature against the configured certificate identity (GitHub OIDC issuer, workload identity, or a pinned public key).

### 10.2 Signature verification at load time

Verification happens at validation stage 10 (Section 9). On success, the pack is marked `signed` and runs with no further restriction. On failure, the unsigned-mod policy applies.

### 10.3 Unsigned mod allowlist policy

The unsigned-mod policy is configured per instance and per edition:

- `allow` (default for dev instances): unsigned mods load with a warning logged.
- `warn`: unsigned mods load but surface a UI warning before first launch.
- `deny`: unsigned mods are rejected; only cosign-verified packs load.

For BE native mods, the default is `deny` on iOS (TrollStore) and BDS, and `warn` elsewhere, because native code execution without a verifiable origin is higher-risk. Overridable per instance by the user.

## 11. Migration from Existing Formats

### 11.1 `.jar` (Fabric) to `.aje`

| Step | Action |
|---|---|
| 1 | Run `aprism convert --in my-mod.jar --out my-mod.aje` |
| 2 | Tool reads `fabric.mod.json` and synthesizes `aprism.manifest.json` |
| 3 | The original jar becomes `<modid>.jar` at the pack root (treated as the common jar) |
| 4 | Mixin configs from `fabric.mod.json` move to `mixins/`; the manifest `mixins` array is rewritten to point to them |
| 5 | Access widener file, if present, moves to the pack root and the manifest `accessWidener` path is rewritten |
| 6 | The resulting `.aje` is loadable by Aprism with no further changes |

### 11.2 `.mcpack` / `.mcaddon` to `.abe`

| Step | Action |
|---|---|
| 1 | Run `aprism convert --in my-pack.mcpack --out my-pack.abe` |
| 2 | Tool reads the Bedrock `manifest.json` |
| 3 | The BP directory moves under `behavior_pack/`; the RP under `resource_pack/` |
| 4 | An `aprism.manifest.json` is synthesized at the root, embedding the Bedrock manifest under `aprism.bedrockManifest` and copying header/modules for native compatibility |
| 5 | If the source pack contained scripts, they are placed under `behavior_pack/scripts/` and the manifest `type` is set to `script` |
| 6 | Native binaries (none in a plain `.mcpack`) are absent; the manifest `type` defaults to `script` or `converted` by origin |

### 11.3 Automated conversion tool reference

The `aprism convert` CLI subcommand is the canonical conversion tool. It accepts `--in <path>`, `--out <path>`, `--format {aje,abe}`, and optional `--sign` to produce a cosign-signed output. Conversion is deterministic: the same input produces the same output bytes (modulo ZIP entry timestamps, zeroed). The tool is bundled with Aprism and documented in Document 3.

## 12. References

- Document 1: Aprism Loader Overall Architecture Design
- Document 2: Aprism JE / BE Mod Manifest Specification
- Document 3: Aprism Launcher Guide
- Document 4: Aprism Feasibility Report
- Minecraft Wiki: Behavior pack structure, `world_behavior_packs.json`
- Fabric Loader: `fabric.mod.json` schema; NeoForge: `neoforge.mods.toml`; LiteLoader: `litemod.json`
- Microsoft / Mojang: Bedrock Dedicated Server directory layout
- Google / Android: Scoped storage behavior on Android 11 and 12+
- Apple: TrollStore distribution model for sideloaded iOS applications
- cosign / Sigstore: Keyless code signing; CycloneDX: SBOM format (for signed bundles)

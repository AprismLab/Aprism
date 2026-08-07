# Aprism JE / BE Mod Manifest Specification

> Document 2 of 8 | Aprism Loader Documentation Set
> Version: v26.0-Alpha.1 | Status: Development
> Author: BlockConnect@StarsailsClover
> Canonical language: English (Chinese copy maintained in parallel)

## 1. Executive Summary

This document specifies the canonical manifest format used by the Aprism Loader to describe, validate, and load mods across Minecraft Java Edition (JE) and Bedrock Edition (BE). The manifest, named `aprism.manifest.json`, is a strict superset of the field-level semantics of `fabric.mod.json` (Fabric/Quilt), `neoforge.mods.toml` / `mods.toml` (NeoForge/Forge), `litemod.json` (LiteLoader), and Bedrock `manifest.json`. A single manifest can describe a mod that ships provider blocks for multiple JE loaders, or a Bedrock mod authored in C++, Lua, C#, Rust, or the Script API.

The manifest is the single source of truth for: mod identity, version, environment, entrypoints, mixins, dependency ranges, per-loader overrides, and language runtime selection. Aprism consumes `aprism.manifest.json` first; when absent, it falls back to auto-discovery of legacy manifests, which are projected into the Aprism schema at load time. The interface contract is monotonic: fields are only added, never removed or renamed; deprecation requires at least one release cycle of notice.

## 2. Design Principles

1. **Superset, not replacement.** Every field that exists in `fabric.mod.json`, `quilt.mod.json`, `neoforge.mods.toml`, `mods.toml`, `litemod.json`, and Bedrock `manifest.json` has a defined mapping into `aprism.manifest.json`. A mod authored against any single legacy format remains loadable.
2. **Auto-discovery fallback.** When `aprism.manifest.json` is not present in a pack, Aprism probes for legacy manifests in a defined order (see Section 5) and synthesizes an in-memory Aprism manifest. The synthesized manifest is equivalent to an explicit one for all resolution and validation purposes, except it cannot carry Aprism-only fields.
3. **Edition-aware.** JE packs (`.aje`) and BE packs (`.abe`) use the same file name but a different top-level shape. JE manifests are flat JSON objects; BE manifests are Bedrock `manifest.json` supersets that preserve `format_version`, `header`, `modules`, and `dependencies` for native Bedrock compatibility, with all Aprism-specific data nested under an `aprism` object.
4. **Language-agnostic.** A `language` / `type` field selects the runtime (java/cpp/lua/csharp/rust/javascript) and the entrypoint contract that applies. This follows the LeviLamina precedent on Bedrock and is generalized to JE.
5. **Per-loader provider blocks.** Multi-loader mods declare a top-level contract and override only what differs per loader (`fabric`, `neoforge`, `forge`, `quilt`, `liteloader`). Overrides are merged, not replaced.
6. **Monotonic interface.** `schemaVersion` is incremented only on additive changes that tooling cannot ignore safely. Once a field is published, its name and type are stable forever.

## 3. aprism.manifest.json Schema (JE `.aje` packs)

### 3.1 Field Reference

| Field | Type | Required | Description | Since |
|---|---|---|---|---|
| `schemaVersion` | integer | yes | Manifest schema version. Currently `1`. | 1 |
| `id` | string | yes | Mod identifier. Lowercase `[a-z0-9_-]`, 2-64 chars, must start with a letter. | 1 |
| `version` | string (SemVer) | yes | Mod version. SemVer 2.0.0 superset; build metadata permitted. | 1 |
| `displayName` | string | no | Human-readable name. Defaults to `id`. | 1 |
| `description` | string | no | Short description. | 1 |
| `authors` | array of string \| object | no | Authors. Object form: `{name, email, website}`. | 1 |
| `contributors` | array of string \| object | no | Contributors. Same shape as `authors`. | 1 |
| `license` | string \| array of string | no | SPDX identifier(s) or free text. | 1 |
| `icon` | string | no | Path inside the pack to a PNG icon (recommended 128x128 or 256x256). | 1 |
| `contact` | object | no | `{homepage, sources, issues, email, irc, discord}`. | 1 |
| `homepage` | string | no | Convenience alias for `contact.homepage`. | 1 |
| `environment` | string | no | One of `*`, `client`, `server`, `dedicated_server`. Default `*`. | 1 |
| `entrypoints` | object | no | Maps phase to array of entrypoint specifiers. See 3.3. | 1 |
| `mixins` | array | no | Mixin config references. See 3.4. | 1 |
| `accessWidener` | string | no | Path to an access widener file (Fabric-compatible format). | 1 |
| `depends` | object | no | Hard dependencies. Keys are mod ids; values are version ranges. | 1 |
| `recommends` | object | no | Soft dependencies loaded before this mod if present. | 1 |
| `suggests` | object | no | Informational suggestions; never fail load. | 1 |
| `breaks` | object | no | Versions that this mod cannot run alongside. | 1 |
| `conflicts` | object | no | Versions that may coexist but are not supported. | 1 |
| `provides` | array of string | no | Alias ids this mod satisfies (e.g. a replacement mod). | 1 |
| `languageAdapters` | object | no | Map of language id to adapter class. | 1 |
| `language` | string | no | Runtime language. Default `java`. One of `java`, `cpp`, `lua`, `csharp`, `rust`, `javascript`. | 1 |
| `platforms` | object | no | Per-loader provider blocks. See 3.5. | 1 |
| `aprismApi` | string (range) | no | Required Aprism API version range. | 1 |
| `minecraft` | string (range) | no | Required Minecraft version range. | 1 |
| `java` | object \| string | no | Required Java version. `{min, max}` or range string. | 1 |
| `custom` | object | no | Free-form metadata; tooling-defined. Must not affect load order. | 1 |
| `depends` may also contain reserved keys `aprism`, `minecraft`, `java` as aliases for the top-level ranges above. | | | | |

### 3.2 Identity and Versioning

`id` must match `^[a-z][a-z0-9_-]{1,63}$`. The id is case-sensitive on disk but case-insensitive for dependency matching, mirroring Fabric. `version` must parse under SemVer 2.0.0 with two extensions permitted: a leading `=` (Maven compat) and `+build` suffixes that compare equal to the unqualified version. Two mods with the same `id` but different `version` cannot coexist; the higher version wins and the lower is logged as shadowed.

### 3.3 `entrypoints`

`entrypoints` is an object whose keys are phase names and whose values are arrays of entrypoint specifiers. A specifier is either a fully-qualified class name (`com.example.MyMod`), a `Class::method` pair (`com.example.MyMod::init`), or an object `{adapter, value}` where `adapter` selects a `languageAdapters` entry (default `java`). Recognized phase keys:

| Phase | When invoked | Signature contract (Java) |
|---|---|---|
| `main` | Shared init, both sides | `void init()` (Fabric `ModInitializer`) |
| `client` | Client-only init | `void onInitializeClient()` |
| `server` | Dedicated server init | `void onInitializeServer()` |
| `aprism` | Aprism lifecycle hook | `void onAprismInit(AprismContext)` |
| `preLaunch` | Before game main; rare | `void onPreLaunch()` |
| `jdk` | JVM-classpath shim | class name only |

Non-Java languages use language-specific contracts declared in Section 10.

### 3.4 `mixins`

Each element is either a string path to a Mixin config JSON (e.g. `"mixins.client.json"`) or an object `{config, environment}` where `environment` is `*`, `client`, or `server`. The referenced Mixin config preserves the canonical `*.mixins.json` shape: `package`, `required`, `minVersion`, `compatibilityLevel`, `priority`, `mixinPriority`, `mixins`, `client`, `server`, `refmap`, `injectors`, `plugin`, `overwrites`.

### 3.5 `platforms` (provider blocks)

`platforms` is an object keyed by loader id: `fabric`, `quilt`, `neoforge`, `forge`, `liteloader`. Each value is an object that may override any of: `entrypoints`, `mixins`, `depends`, `recommends`, `suggests`, `breaks`, `conflicts`, `accessWidener`, `languageAdapters`, `language`, `jars`, `transformers`. Overrides are deep-merged: arrays are concatenated (de-duplicated by value), objects are merged key-by-key with the override winning scalars. A provider block may also declare `enabled: false` to instruct Aprism to skip a loader even if its jar is present.

```json
{
  "platforms": {
    "fabric":  { "depends": { "fabricloader": ">=0.15.0" } },
    "neoforge":{ "depends": { "neoforge": ">=20.4.0" }, "accessWidener": null },
    "forge":   { "enabled": false }
  }
}
```

### 3.6 Full JE Multi-Loader Example

```json
{
  "schemaVersion": 1,
  "id": "examplemod",
  "version": "2.4.1+build.42",
  "displayName": "Example Mod",
  "description": "A multi-loader reference mod.",
  "authors": [{ "name": "BlockConnect", "email": "n@example.com" }],
  "license": ["MIT", "Apache-2.0"],
  "icon": "assets/examplemod/icon.png",
  "contact": { "homepage": "https://example.com", "sources": "https://github.com/example/examplemod", "issues": "https://github.com/example/examplemod/issues" },
  "environment": "*",
  "entrypoints": {
    "main":   ["com.example.ExampleMod::init"],
    "client": ["com.example.ExampleModClient::init"]
  },
  "mixins": [
    "example.mixins.json",
    { "config": "example.client.mixins.json", "environment": "client" }
  ],
  "accessWidener": "example.accesswidener",
  "depends": {
    "aprism":      ">=26.0-Alpha1",
    "minecraft":   ">=1.20.4 <1.22",
    "java":        ">=21",
    "fabricloader": ">=0.15.0"
  },
  "recommends":  { "modmenu": ">=7.0.0" },
  "suggests":    { "sodium": "*" },
  "breaks":     { "incompatiblething": "<2.0" },
  "conflicts":  { "rivalthing": "*" },
  "provides":   ["example-legacy-id"],
  "languageAdapters": { "kotlin": "org.example.KotlinAdapter" },
  "platforms": {
    "fabric":   { "depends": { "fabricloader": ">=0.15.0", "fabric-api": ">=0.96.0" } },
    "quilt":    { "depends": { "quilt_loader": ">=0.20.0" }, "provides": ["examplemod"] },
    "neoforge": { "depends": { "neoforge": ">=20.4.0" }, "accessWidener": null },
    "forge":    { "depends": { "forge": ">=47.0.0" }, "entrypoints": { "main": ["com.example.forge.ExampleModForge"] } },
    "liteloader": { "enabled": false }
  },
  "custom": { "examplemod:feature_flags": ["new_rendering"] }
}
```

## 4. aprism.manifest.json Schema (BE `.abe` packs)

For Bedrock, the manifest is a strict superset of the native `manifest.json`. All Bedrock fields (`format_version`, `header`, `modules`, `dependencies`, `capabilities`, `metadata`, `subpacks`) are preserved verbatim so that Bedrock itself and stock tooling can still parse the pack. Aprism-specific data lives under a single `aprism` object.

### 4.1 Aprism Extension Fields (under `aprism`)

| Field | Type | Required | Description | Since |
|---|---|---|---|---|
| `aprism.schemaVersion` | integer | yes | Aprism BE manifest schema version. Currently `1`. | 1 |
| `aprism.modId` | string | yes | Same id rules as JE. Must be unique within the load graph. | 1 |
| `aprism.version` | string (SemVer) | yes | Mod version. Distinct from `header.version` (Bedrock triple). | 1 |
| `aprism.language` | string | yes | One of `cpp`, `lua`, `csharp`, `rust`, `javascript`. | 1 |
| `aprism.nativeEntrypoints` | object | conditional | Required when `language` != `javascript`. Per-platform binary refs. | 1 |
| `aprism.scriptEntrypoints` | object | conditional | Required when `language` = `javascript`. Maps to Script API `entry`. | 1 |
| `aprism.aprismApiVersion` | string (range) | yes | Required Aprism BE API version range. | 1 |
| `aprism.gameVersionRange` | string (range) | yes | Required Minecraft Bedrock version range. | 1 |
| `aprism.depends` | object | no | Aprism BE mod dependencies (modId -> range). | 1 |
| `aprism.compatibleJE` | object | no | For JE-converted mods: `{jeModId, jeVersionRange, conversionProfile}`. | 1 |
| `aprism.mixins` | array | no | Optional LeviLamina-style hook descriptors for C++ runtime. | 1 |
| `aprism.custom` | object | no | Free-form. | 1 |

### 4.2 Native C++ BE Mod Example

```json
{
  "format_version": 2,
  "header": {
    "name": "Example Native BE Mod",
    "description": "Native C++ Bedrock mod",
    "uuid": "5f1d2b3a-9c8e-4f2a-b6d1-7e3c0a1b2d3e",
    "version": [1, 0, 0],
    "min_engine_version": [1, 21, 0]
  },
  "modules": [
    { "type": "data", "uuid": "6e2c3b4a-1d2e-4a3b-8c5f-9a0b1c2d3e4f", "version": [1, 0, 0] }
  ],
  "dependencies": [],
  "aprism": {
    "schemaVersion": 1,
    "modId": "example_native_be",
    "version": "1.0.0",
    "language": "cpp",
    "aprismApiVersion": ">=26.0-Alpha1",
    "gameVersionRange": ">=1.21.0 <1.22.0",
    "nativeEntrypoints": {
      "windows-x64":   { "binary": "native/windows-x64/example.dll",     "entry": "aprism_mod_load" },
      "android-arm64": { "binary": "native/android-arm64/libexample.so", "entry": "aprism_mod_load" },
      "ios-arm64":     { "binary": "native/ios-arm64/example.dylib",     "entry": "aprism_mod_load" }
    },
    "depends": { "levilamina": ">=0.10.0" },
    "mixins": [ "hooks.example.json" ]
  }
}
```

### 4.3 Script API BE Mod Example

```json
{
  "format_version": 2,
  "header": {
    "name": "Example Script BE Mod",
    "description": "JavaScript Script API mod",
    "uuid": "7a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d",
    "version": [1, 2, 0],
    "min_engine_version": [1, 21, 10]
  },
  "modules": [
    { "type": "script", "language": "javascript", "uuid": "8b4c5d6e-7f8a-4b9c-0d1e-2f3a4b5c6d7e", "version": [1, 2, 0], "entry": "scripts/main.js" }
  ],
  "dependencies": [
    { "module_name": "@minecraft/server", "version": "1.10.0" }
  ],
  "aprism": {
    "schemaVersion": 1,
    "modId": "example_script_be",
    "version": "1.2.0",
    "language": "javascript",
    "aprismApiVersion": ">=26.0-Alpha1",
    "gameVersionRange": ">=1.21.10",
    "scriptEntrypoints": { "main": "scripts/main.js", "client": "scripts/client.js" }
  }
}
```

## 5. Manifest Discovery and Resolution Order

When Aprism loads a pack, it follows this order to obtain a manifest:

1. **JE `.aje` packs.** Look for `aprism.manifest.json` at the pack root. If present, parse and validate against the JE schema.
2. **JE fallback.** If `aprism.manifest.json` is absent, probe in this order:
   1. `fabric.mod.json` (Fabric packs).
   2. `quilt.mod.json` (Quilt packs; falls back to an embedded `fabric.mod.json` when absent).
   3. `META-INF/neoforge.mods.toml` (NeoForge packs).
   4. `META-INF/mods.toml` with `loaderVersion` heuristic (Forge packs).
   5. `litemod.json` (LiteLoader packs).
   The first match is projected into the Aprism schema (see Section 8). If a provider block is implied by the discovered file (e.g. `fabric.mod.json` implies `platforms.fabric`), the projection fills that block.
3. **BE `.abe` packs.** Look for `manifest.json` (Bedrock canonical) at the pack root. If an `aprism` object is present, parse it. If not, the pack is treated as a plain Bedrock pack and Aprism exposes only Script API entrypoints (no native loading).
4. **Embedded `.aje` inside `.abe`.** A `.abe` pack may contain a `je-source/` directory with an embedded `.aje` for the conversion provenance field `aprism.compatibleJE`. This is informational only.

If no manifest is found in any form, the pack is rejected with `CHKAPRISM-MANIFEST-001`.

## 6. Version Range Syntax

Aprism uses a SemVer range syntax with explicit Maven-range compatibility. The grammar (case-sensitive):

```
range      := clause ( "," clause )*
clause     := op? version
op         := ">=" | "<=" | ">" | "<" | "=" | "~" | "^"
version    := semver ( "-" pre )? ( "+" build )?
```

Commas denote logical AND. The pipe character `|` denotes logical OR with lower precedence than comma: `>=1.0 <2.0 | >=3.0` matches `[1.0, 2.0)` OR `[3.0, +inf)`. The bare wildcard `*` matches any version. The empty string is invalid.

| Operator | Meaning |
|---|---|
| `=` | Equal (also implicit when no operator). |
| `>` `<` `>=` `<=` | Comparison. |
| `^` | Compatible-with: same major version. `^1.2.3` = `>=1.2.3 <2.0.0`. `^0.2.3` = `>=0.2.3 <0.3.0`. |
| `~` | Patch-level: `~1.2.3` = `>=1.2.3 <1.3.0`. `~1.2` = `>=1.2.0 <1.3.0`. |

**Maven compatibility.** For Forge-style ranges Aprism also accepts Maven brackets: `[1.0,2.0)`, `(,3.0]`, `[1.5.0]`. These are translated to the SemVer form internally.

**Snapshots and pre-releases.** Snapshot versions use the `26w01a` / `1.21-snapshot` form. Aprism normalizes them to a SemVer pre-release tag (`1.21.0-snapshot.26w01a`). Ranges may include snapshot selectors: `>=1.21-snapshot` matches all snapshots and the release.

## 7. Dependency Resolution Algorithm

Aprism resolves dependencies before classloading. The algorithm:

1. **Collect.** Build a graph where each node is a `(modId, version)` and each edge is a dependency declared in `depends` or `recommends`.
2. **Satisfy.** For each `depends` edge, locate a candidate mod by id (including `provides` aliases). If none, fail with `CHKAPRISM-DEP-001` (missing hard dependency). For `recommends`, missing candidates are logged but tolerated.
3. **Range check.** For each located candidate, verify its `version` satisfies the declared range. Failure: `CHKAPRISM-DEP-002` (version mismatch).
4. **Breaks/conflicts.** For each loaded mod, evaluate `breaks` against the graph. Any match: `CHKAPRISM-DEP-003` (broken). `conflicts` matches log a warning and continue.
5. **Provides deduplication.** If two mods `provides` the same alias, the higher `version` wins; the loser is unloaded with `CHKAPRISM-DEP-004` (duplicate provides).
6. **Topological sort.** Order mods so that every `depends` and `recommends` (when present) predecessor precedes its successor. Ties broken by id lexicographic order for determinism.
7. **Cycle detection.** A cycle in `depends` is fatal: `CHKAPRISM-DEP-005` (circular dependency). A cycle in `recommends` is logged and broken arbitrarily.
8. **Aprism/Minecraft/Java.** The reserved deps `aprism`, `minecraft`, `java` are evaluated first against the runtime; failure halts load before any mod is initialized.

## 8. Migration and Compatibility

### 8.1 `fabric.mod.json` -> `aprism.manifest.json`

| fabric.mod.json | aprism.manifest.json |
|---|---|
| `schemaVersion` | `schemaVersion` (Aprism: always `1`) |
| `id` | `id` |
| `version` | `version` |
| `name` | `displayName` |
| `description` | `description` |
| `authors` / `contact` | `authors` / `contact` |
| `license` | `license` |
| `icon` | `icon` |
| `environment` | `environment` |
| `entrypoints.main/client/server` | `entrypoints.main/client/server` |
| `mixins` | `mixins` |
| `accessWidener` | `accessWidener` |
| `depends/recommends/suggests/breaks/conflicts` | same names |
| `provides` | `provides` |
| `jars` | `platforms.fabric.jars` |
| `languageAdapters` | `languageAdapters` |
| `custom` | `custom` |

### 8.2 `neoforge.mods.toml` / `mods.toml` -> `aprism.manifest.json`

| TOML | Aprism |
|---|---|
| `modLoader` | `platforms.neoforge.language` (`javafmlow` -> `java`) |
| `loaderVersion` | `platforms.neoforge.depends.neoforge` or `forge` |
| `license` | `license` |
| `[[mods]] modId` | `id` (case folded to lowercase) |
| `[[mods]] version` | `version` |
| `[[mods]] displayName` | `displayName` |
| `[[mods]] description` | `description` |
| `[[mods]] logoFile` | `icon` |
| `[[mods]] authors` | `authors` |
| `[[dependencies.<id>]]` | `platforms.neoforge.depends` (type `required` -> `depends`, `optional` -> `recommends`, `incompatible` -> `breaks`, `discouraged` -> `conflicts`) |
| `side BOTH/CLIENT/SERVER` | `environment` (`BOTH` -> `*`, `CLIENT` -> `client`, `SERVER` -> `dedicated_server`) |

### 8.3 `litemod.json` -> `aprism.manifest.json`

| litemod.json | Aprism |
|---|---|
| `name` | `id` |
| `mcversion` | `minecraft` (exact) |
| `revision` / `version` | `version` (`version` preferred; `revision` as build metadata) |
| `author` | `authors` |
| `description` | `description` |
| `tweakClass` | `platforms.liteloader.entrypoints.main` |
| `classTransformerClasses` | `platforms.liteloader.transformers` |
| `dependsOn` | `platforms.liteloader.depends` |
| `requiredAPIs` | `platforms.liteloader.depends` (reserved keys) |

### 8.4 Bedrock `manifest.json` -> Aprism BE manifest

| Bedrock | Aprism BE |
|---|---|
| `format_version`, `header`, `modules`, `dependencies`, `capabilities`, `metadata`, `subpacks` | preserved verbatim |
| `header.uuid` + `header.version` | preserved; Aprism identity is `aprism.modId` + `aprism.version` |
| `modules[type=script].entry` | `aprism.scriptEntrypoints.main` |
| `modules[type=script].language` | `aprism.language` (normalized: `js` -> `javascript`) |
| `dependencies[module_name=...]` | preserved; additionally mirrored in `aprism.depends` for Aprism BE mods |

## 9. Validation Rules

Aprism emits errors from a fixed code table. Tooling MUST treat unknown codes as warnings and continue.

| Code | Severity | Condition |
|---|---|---|
| `CHKAPRISM-MANIFEST-001` | error | No manifest found and no fallback matched. |
| `CHKAPRISM-MANIFEST-002` | error | `schemaVersion` missing or unsupported. |
| `CHKAPRISM-MANIFEST-003` | error | `id` does not match `^[a-z][a-z0-9_-]{1,63}$`. |
| `CHKAPRISM-MANIFEST-004` | error | `version` is not a valid SemVer string. |
| `CHKAPRISM-MANIFEST-005` | error | Required field missing (`id`, `version`, `schemaVersion`). |
| `CHKAPRISM-MANIFEST-006` | warning | Unknown field present (forward-compat). |
| `CHKAPRISM-MANIFEST-007` | error | `environment` not one of `*`, `client`, `server`, `dedicated_server`. |
| `CHKAPRISM-MANIFEST-008` | error | Entrypoint specifier malformed. |
| `CHKAPRISM-MANIFEST-009` | error | Mixin config file not found or invalid. |
| `CHKAPRISM-MANIFEST-010` | error | `accessWidener` path not found. |
| `CHKAPRISM-MANIFEST-011` | error | BE manifest missing `aprism` object and pack is not a stock Bedrock pack. |
| `CHKAPRISM-MANIFEST-012` | error | `aprism.language` not in allowed set. |
| `CHKAPRISM-MANIFEST-013` | error | `aprism.nativeEntrypoints` missing for non-script language. |
| `CHKAPRISM-DEP-001` | error | Missing hard dependency. |
| `CHKAPRISM-DEP-002` | error | Dependency version mismatch. |
| `CHKAPRISM-DEP-003` | error | `breaks` constraint violated. |
| `CHKAPRISM-DEP-004` | warning | Duplicate `provides` alias; lower version unloaded. |
| `CHKAPRISM-DEP-005` | error | Circular dependency detected. |
| `CHKAPRISM-DEP-006` | warning | `conflicts` constraint matched; continuing. |
| `CHKAPRISM-DEP-007` | error | Reserved dep (`aprism`/`minecraft`/`java`) failed. |
| `CHKAPRISM-RANGE-001` | error | Version range syntax invalid. |

## 10. Multi-Language Mod Manifests

The `language` field selects the runtime and the entrypoint contract. Defaults: JE = `java`; BE = `javascript` (Script API) unless `aprism.language` declares otherwise.

| `language` | Runtime | Entrypoint contract |
|---|---|---|
| `java` | JVM classloader | Class name or `Class::method` (Section 3.3). |
| `cpp` | Native dlopen/LoadLibrary | C ABI factory `extern "C" IAprismMod* aprism_mod_create()`; optional platform entry `aprism_mod_load` (Section 4.2 example and Document 8, Section 6.2). |
| `lua` | Embedded Lua 5.x / LuaJIT (JE) or Bedrock Lua (BE) | String path to a `.lua` file with `function aprism_init(ctx)`. |
| `csharp` | .NET CLR host (JE) / native .NET on BE | Path to assembly; static method `AprismInit`. |
| `rust` | Native like `cpp` | Same as `cpp`: C ABI factory `aprism_mod_create` (Rust `extern "C"`). |
| `javascript` | GraalJS (JE) / Bedrock Script API (BE) | Path to a `.js` module with default-exported `init`. |

For non-Java languages on JE, the entrypoint specifier is an object: `{"adapter": "<language>", "value": "<path-or-symbol>"}`. The `languageAdapters` map must contain an entry for the adapter id, optionally pointing at a built-in Aprism adapter (`aprism:lua`, `aprism:csharp`, `aprism:graaljs`, `aprism:native`).

```json
{
  "language": "lua",
  "entrypoints": {
    "main": [ { "adapter": "aprism:lua", "value": "scripts/main.lua" } ]
  },
  "languageAdapters": { "aprism:lua": "io.aprism.loader.lang.LuaAdapter" }
}
```

## 11. Examples

### 11.1 Pure JE Fabric Mod

```json
{
  "schemaVersion": 1,
  "id": "simplerules",
  "version": "1.0.0",
  "displayName": "Simple Rules",
  "description": "A minimal Fabric mod.",
  "authors": ["BlockConnect"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": { "main": ["com.example.simplerules.SimpleRules"] },
  "mixins": ["simplerules.mixins.json"],
  "depends": { "fabricloader": ">=0.15.0", "minecraft": ">=1.20.4", "java": ">=21" },
  "platforms": { "fabric": {} }
}
```

### 11.2 Multi-Loader JE Mod

See Section 3.6.

### 11.3 BE Native C++ Mod

See Section 4.2.

### 11.4 JE-Converted-to-BE Mod

A mod originally authored for JE Fabric, converted to BE. The `.abe` manifest declares `aprism.compatibleJE` to record the source provenance and conversion profile.

```json
{
  "format_version": 2,
  "header": {
    "name": "SimpleRules (BE Conversion)",
    "description": "JE SimpleRules converted to Bedrock",
    "uuid": "9c5d6e7f-8a9b-4c0d-1e2f-3a4b5c6d7e8f",
    "version": [1, 0, 0],
    "min_engine_version": [1, 21, 0]
  },
  "modules": [
    { "type": "script", "language": "javascript", "uuid": "0d6e7f8a-9b0c-4d1e-2f3a-4b5c6d7e8f9a", "version": [1, 0, 0], "entry": "scripts/main.js" }
  ],
  "dependencies": [ { "module_name": "@minecraft/server", "version": "1.10.0" } ],
  "aprism": {
    "schemaVersion": 1,
    "modId": "simplerules_be",
    "version": "1.0.0-conversion.1",
    "language": "javascript",
    "aprismApiVersion": ">=26.0-Alpha1",
    "gameVersionRange": ">=1.21.0",
    "scriptEntrypoints": { "main": "scripts/main.js" },
    "compatibleJE": { "jeModId": "simplerules", "jeVersionRange": ">=1.0.0", "conversionProfile": "fabric-to-scriptapi-v1" }
  }
}
```

## 12. References

- Fabric `fabric.mod.json` schema v1, Fabric Loader documentation.
- NeoForge `neoforge.mods.toml` specification, NeoForge project.
- Legacy Forge `mcmod.info` (Forge 1.12 and earlier).
- LiteLoader `litemod.json` schema, LiteLoader project.
- Bedrock `manifest.json` specification, Mojang / Microsoft Bedrock Add-On documentation.
- SpongePowered Mixin `*.mixins.json` configuration reference.
- SemVer 2.0.0 specification, semver.org.
- Maven version range syntax, Apache Maven documentation.
- LeviLamina manifest and multi-language modding precedent, LeviLamina project.
- Aprism `FACT.md` v26.0-Alpha.1, Architecture Decisions 9.4 and 9.9.

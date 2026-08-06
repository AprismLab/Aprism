# FACT.md - Aprism Loader Project Tracking

> Maintained by BlockConnect@StarsailsClover
> Convention: read & update this file before and after every task session.
> Versioning: v26.0 baseline. Dev = Alpha 1-9 with Phase 0-9 sub-control. Official = PreRelease / Release.

## 1. Project Identity

- **Name:** Aprism / Aprism Loader
- **Tagline:** A cross-platform, cross-edition, cross-version Minecraft mod loader and injector compatible with JE/BE.
- **Author:** BlockConnect@StarsailsClover
- **Repo:** GitHub-hosted (enterprise-grade git control)
- **License:** See LICENSE

## 2. Core Objectives

1. Unified loader supporting JE Fabric, Forge, NeoForge, LiteLoader modpacks.
2. Rapid JE-to-BE mod conversion (JE mod -> BE Aprism mod).
3. Fabric-like mod loader ecosystem for BE.
4. JE Aprism Native: modern, native, JVM-customized foundation.
5. Forced unified interfaces and usage methods; version sequence only increases, never decreases.
6. Maximized cross-version / cross-platform compatibility.

## 3. Platform Coverage Plan

| Platform | Injection Strategy | Status |
|---|---|---|
| Windows (BE) | ExLoader + Int. Aprism DLL injection | Planned |
| Android non-root (BE) | Container + preload hijack SO injection | Planned |
| Android root (BE) | Direct SO injection (injector TBD) | Planned |
| iOS/iPadOS (BE) | Troll Store route (under research) | Research |
| macOS (BE) | Unknown | Research |
| Linux (BE) | Unknown | Research |
| JE (all desktop) | Native JVM (Aprism Native) | Planned |

## 4. Deliverables (8 Documents)

Each document ships in English (canonical) + Chinese (copy).

1. Aprism Loader Overall Architecture Design
2. Aprism JE / BE Mod Manifest
3. Minecraft JE/BE Launcher Aprism Adaptation, Download/Install/Management Module Development Guide
4. Product Feasibility Report
5. Product Technical Report and Research Methodology
6. Product Principle Specification
7. Aprism Mods Pack (.aje/.abe) Classification, Structure and Per-Platform Placement
8. Aprism Mod Developer Guide: Develop and Export .aje/.abe Modpacks

## 5. Versioning Scheme

Format: `v<Year>.<aprism>-<stability>.<subVer>-<MCEdit>-<MCVer>`

- `<Year>.<aprism>`: e.g. `v26.0` = Year 2026, Aprism major version 0
- `<stability>`: `Alpha` (development sub-version), `PreRelease`, `Release`. Beta not planned.
- `<subVer>`: sub-version number (Alpha 1, 2, 3... = development iterations)
- `<MCEdit>`: `JE` or `BE` (Minecraft edition)
- `<MCVer>`: target Minecraft version, e.g. `1.21.4` or `26.2`

Example artifact: `Aprism-v26.0-Alpha.1-JE-1.21.4`

Phase (0-9) is an internal granular development stage tracker, NOT shown in public version strings. Tracked only in FACT.md session logs.

- Internal dev tag: `v26.0-Alpha.1-Phase0` (Phase internal only)
- Public tag: `v26.0-Alpha.1`
- Official: `v26.0-PreRelease.1` -> `v26.0-Release`
- Interface contract: monotonic increment only (never remove/rename); deprecation allowed with notice.

## 6. Conventions

- Default document language: English. Chinese copy required.
- Conversation language: Chinese.
- No emoji in any artifact.
- Sign all artifacts: BlockConnect@StarsailsClover.
- Build tool: Gradle (Workspace\Gradle).
- Enterprise git: conventional commits, signed, no force-push to main.

## 7. Session Log

### Session 2026-08-06 (v26.0-Alpha.1-Phase0)
- [DONE] Initialized FACT.md.
- [DONE] Launched 4 parallel deep-research streams (JE loaders, BE modding, platform injection, JVM/MultiLoader/legal).
- [DONE] Synthesized research into 13 architecture decisions (FACT.md section 9).
- [DONE] Authored 8 documents in English (canonical) + 8 Chinese copies = 16 total in docs/en/ and docs/zh/.
  - Doc 1: Architecture Design (15 sections, 4 mermaid, 9 tables, 10-row risk register)
  - Doc 2: Mod Manifest (12 sections, full schema, 21 CHKAPRISM rules, migration tables)
  - Doc 3: Launcher Guide (13 sections, 4 mermaid, 13 tables, cosign flow, GITHUB_TOKEN chain)
  - Doc 4: Feasibility Report (11 sections, 14-row risk register, phased delivery, go/no-go)
  - Doc 5: Technical Report (9 sections, 7 research questions, tech selection tables, validation plan)
  - Doc 6: Principle Specification (9 sections, 6 mermaid, JE/BE runtime principles, hook abstraction)
  - Doc 7: Pack Structure (12 sections, .aje/.abe trees, per-platform placement tables, validation)
  - Doc 8: Developer Guide (16 sections, Gradle configs, IAprismMod, aprism-packaging plugin, C++ deep dive)
- [DONE] Commit initial docs set to GitHub with conventional commits.
- [DONE] Versioning correction: format is now `v<Year>.<aprism>-<stability>.<subVer>-<MCEdit>-<MCVer>` with Phase internal-only. Updated FACT.md, gradle.properties, libs.versions.toml, README.md.
- [DONE] Project skeleton: multi-module Gradle build (settings.gradle, root build.gradle, gradle.properties, libs.versions.toml) with 4 subprojects (aprism-api, aprism-manifest, aprism-loader-core, aprism-packaging).
- [DONE] Java source skeleton: IAprismMod/AprismContext/AprismEventBus/AprismPhase (api), ManifestParser/AprismManifest/DependencyResolver/VersionRange (manifest), AprismAgent/AprismClassLoader/AprismClassTransformer/AprismRuntime (loader-core), AprismPackagingPlugin/PackageAjeTask/PackageAbeTask (packaging).
- [DONE] Commit skeleton + versioning correction to GitHub (3 conventional commits: fix(versioning), feat(build), docs).
- [DONE] Audited all 16 docs for versioning correctness; found and fixed old-format remnants in all doc headers + wrong versioning explanation in architecture doc (01).
- [DONE] Researched and designed Aprism Extensions (*.aep) architecture: Aprism does NOT natively support other loaders; loader support is provided by .aep extensions.
- [DONE] Designed per-loader mod folder separation: /mods (Aprism native .aje), /fabric-mods, /neoforge-mods, /forge-mods, /quilt-mods, /liteloader-mods.
- [DONE] Defined priority version targets: JE 26.2/26.1.2/1.21.10/1.21.4/1.16.5, BE 26.2/26.1.2 (BE only from 26.x).
- [DONE] Updated all 16 docs (EN+ZH) + build.gradle: fixed versioning remnants, added Aprism Extensions (.aep) architecture, per-loader mod folder scheme (/mods, /fabric-mods, etc.), BE 26.x-only support scope, Aprism native superset principle.
- [DONE] Commit doc architecture update to GitHub (60721ed).
- [IN PROGRESS] Implementation phase begins. Priority: JE 26.2/26.1.2/1.21.10/1.21.4/1.16.5, BE 26.2/26.1.2.

## 7a. Implementation Plan (v26.0-Alpha.1-Phase0)

Implementation order (dependency-driven):

### Phase 0a: aprism-api (foundation)
- IAprismMod lifecycle interface + AprismContext
- AprismPhase enum (PREINIT/INIT/SETUP/COMPLETE/CLIENT/SERVER)
- AprismEventBus + AprismEvent + AprismEventListener
- ModContainer + ModMetadata
- Environment (JE/BE, client/server, MC version)
- Registry system (Registry<T>, BlockRegistry, ItemRegistry)
- IAprismExtension interface + ExtensionContext (for .aep extensions)
- VersionRange (SemVer range parsing/matching)

### Phase 0b: aprism-manifest (manifest parsing)
- AprismManifest data model (aprism.manifest.json schema)
- AprismExtensionManifest data model (aprism.extension.json schema)
- ManifestParser (Gson-based JSON parsing)
- ManifestValidator (schema validation, 21 CHKAPRISM rules)
- DependencyResolver (topological sort, conflict detection)
- Fallback readers: FabricManifestReader, NeoForgeManifestReader

### Phase 0c: aprism-loader-core (javaagent runtime)
- AprismAgent (premain/agentmain entry)
- AprismRuntime (bootstrap: extension scan -> mod scan -> load)
- ExtensionLoader (scan aprism-extensions/, validate ranges, register)
- AprismClassLoader (Knot-style shared space + opt-in isolation)
- AprismClassTransformer (ClassFileTransformer, Mixin downstream)
- ModDiscoverer (scan mods/ for .aje, <loader>-mods/ for .jar/.litemod)
- EntryPointInvoker (invoke IAprismMod lifecycle)

### Phase 0d: aprism-packaging (Gradle plugin)
- AprismPackagingPlugin + AprismPackagingExtension
- PackageAjeTask (.aje ZIP assembly)
- PackageAbeTask (.abe ZIP assembly)
- PackageAepTask (.aep ZIP assembly)

## 8. Open Questions / Risks

- macOS/Linux have NO Bedrock client binary; injection is N/A (BDS server only).
- iOS TrollStore route is per-version and maintenance-heavy (research tier only).
- Anti-cheat on BE is server-side + Xbox Live policy; ban risk is real and must be disclosed.
- BE version adapter + signature DB is the single most important engineering investment.
- 1.21.11 -> 26.1 boundary breaks binary compatibility (Mojang shipped unobfuscated at 26.1).
- Extension version-range matching must be deterministic and fast (scanned at every boot).
- Per-loader folder scheme requires launcher cooperation; launchers must NOT flatten /<loader>-mods into /mods.

## 8a. Priority Version Targets (INTERNAL - not for public disclosure)

JE support line follows Fabric coverage. BE support starts from 26.x ONLY.

| Edition | Target versions | Profile | JDK | Remap |
|---|---|---|---|---|
| JE | 26.2 | 26.1+ | Java 25 | no-remap (unobfuscated) |
| JE | 26.1.2 | 26.1+ | Java 25 | no-remap |
| JE | 1.21.10 | pre-26.1 | Java 21 | Intermediary remap |
| JE | 1.21.4 | pre-26.1 | Java 21 | Intermediary remap |
| JE | 1.16.5 | legacy | Java 8/11 | Intermediary remap (legacy) |
| BE | 26.2 | 26.x | native | n/a |
| BE | 26.1.2 | 26.x | native | n/a |

BE versions below 26.x are NOT supported. No backport investment.

## 9. Architecture Decisions (Synthesized from Research)

### 9.1 JE Aprism Native Foundation
- **Decision**: `premain` javaagent + `ClassFileTransformer` over a bundled tuned OpenJDK (Temurin-derived) LTS build. NOT a custom JVM, NOT GraalVM for the default loader.
- GraalVM Native Image reserved for opt-in pre-baked modpack bundles (closed-world). Track Project Leyden for launcher warm-start.
- Loader core is version-agnostic (mirrors Fabric Loader). Version-specific logic lives in Aprism API adapters.

### 9.2 Classloader Strategy
- Default = Fabric Knot-style shared class space (largest mod catalog: Fabric + Quilt + LiteLoader).
- Opt-in isolated ModClassLoader shim for Forge-style mods that declare isolation.
- ModContainer reference identity is an invariant across all lookup APIs (Quilt 0.29.2 lesson).

### 9.3 Transformation Layer
- SpongePowered Mixin = canonical bytecode injection (all relevant loaders consume it).
- Aprism classloader inserts MixinTransformer downstream of its own remapper; refmaps generated against Aprism's chosen intermediary (consume Fabric Intermediary for pre-26.1; no-remap for 26.1+).

### 9.4 Manifest Schema
- Superset `aprism.mod.json` (schemaVersion, id, version, entrypoints, mixins, depends) + per-loader provider blocks (fabric/neoforge/forge/quilt/liteloader).
- Auto-discover fabric.mod.json / neoforge.mods.toml / mods.toml / litemod.json as fallback.
- BE manifest = Bedrock manifest.json superset with aprism extension fields (aprismVersion, native entrypoints, language type).

### 9.5 Event System
- Phase-strict event bus (PREINIT/INIT/SETUP/COMPLETE/CLIENT/SERVER).
- Provide Fabric functional registration adapter + Forge addListener adapter, both backed by the same bus.

### 9.6 Build Tooling
- Architectury Loom (loom-no-remap for 26.1+, loom for <=1.21.11) as multi-loader foundation.
- Custom `aprism-packaging` Gradle plugin consumes shadowJar/remapJar output -> .aje/.abe artifacts.
- gradle/libs.versions.toml central catalog. Gradle 9.x + Java 25 for 26.1+ profile; Gradle 8.x + Java 21 for legacy.

### 9.7 Cross-Version Compatibility
- Compatibility-group jars with SemVer ranges (mirror minecraft-inventory-sort strategy).
- Split profiles: pre-26.1 (remapped, Intermediary) vs 26.1+ (no-remap, unobfuscated).
- JDK targets per profile: Java 17 (1.20-1.20.4), Java 21 (1.20.5-1.21.11), Java 25 (26.x).
- Interface contract: monotonic increment only; deprecation allowed with notice; never remove/rename.

### 9.8 BE Injection Per-Platform
- Windows (P0): proxy DLL hijack (version.dll) + manual map fallback; MinHook + SafetyHook; libhat signature scanning.
- Android root (P1): Zygisk module + ShadowHook.
- Android non-root (P1): container (NMLauncher-style) + preload hijack + ShadowHook.
- iOS TrollStore (P2 research): insert_dylib + LC_LOAD_DYLIB re-sign + Dobby/ElleKit.
- macOS/Linux (N/A client): BDS server only via LeviLamina-style loader.
- Consoles: impossible (locked down).

### 9.9 BE Loader Architecture
- 3-layer (LeviLamina pattern): reverse-engineered headers (auto-generated via header_generator from BedrockAnalyzer) / core (mod registrar, hook manager, version DB) / public API (events, commands, registry).
- src/ + src-client/ + src-server/ split.
- Version adapter + signature DB (per-build JSON/SQLite) is mandatory infrastructure. Community-maintained signature repo (like AmethystAPI/Community-Headers).
- Multi-language mods (C++/Lua/C#/Rust) via manifest `type` field (LeviLamina precedent).

### 9.10 JE-to-BE Conversion
- Conversion module parses .aje manifest + bytecode/resources, translates to .abe (BP/RP/Script API where possible + native hooks where Script API insufficient).
- Reference Amethyst's C++ mod approach for native capabilities.

### 9.11 Distribution
- Standalone desktop download ONLY. NOT Microsoft Store (Policy 10.2 blocks dynamic code), NOT Apple App Store (Rule 2.4.5(iv)), NOT Realms.
- Download Minecraft from Mojang-authorized sources at runtime; never redistribute modified jars.
- GitHub Releases as primary CDN + cosign keyless signing + CycloneDX SBOM.
- GITHUB_TOKEN-aware client (prefer gh auth token, then env, then anonymous+cache). Handle cross-host redirect (strip Authorization on release-assets.githubusercontent.com).
- Modrinth mirror for mod discoverability.

### 9.12 Git / Release Process
- Conventional Commits. Trunk-based with release branches. GPG/SSH signed commits + signed tags.
- GitHub Releases with auto-notes + attached artifacts + checksums.txt + checksums.txt.bundle.
- CI: GitHub Actions matrix (Windows/Linux JVM, Android NDK, iOS). CodeQL + Dependabot.

### 9.13 Pack Formats
- .aje = ZIP containing aprism.manifest.json + <modid>.jar (Aprism native, uses Aprism API) + resources/ + mixins/ + lib/. NO per-loader subdirs; .aje is Aprism-native only.
- .abe = ZIP containing aprism.manifest.json + behavior_pack/ + resource_pack/ + native/ (per-platform native mod binaries) + scripts/.
- .aep = ZIP containing aprism.extension.json + extension jar/native + optional resources. See 9.14.

### 9.14 Aprism Extensions (*.aep) - Loader Support Model
- **Principle**: Aprism Loader is NATIVE. It does NOT natively understand Fabric/Forge/NeoForge/LiteLoader/Quilt mod formats. Loader support is provided by Aprism Extensions (*.aep), which enhance Aprism itself (NOT mods).
- **Extension types**:
  - `loader-support`: provides a mod loader runtime for a specific loader (Fabric, Forge, NeoForge, LiteLoader, Quilt).
  - `api-extension`: extends Aprism API with additional capabilities beyond the core.
  - `platform-adapter`: adapts Aprism to a specific platform or MC version boundary.
  - `converter`: provides format conversion pipelines (e.g., JE-to-BE).
- **Naming convention**: `<Purpose>-A<AprismVerRange>-<LoaderKey><LoaderVerRange>-<MCEdit>-<MCVer>.aep`
  - `A` = Aprism Loader version range (SemVer range, e.g. `[26.0,27.0)`)
  - Loader key letters (unique, no conflicts): `Fa`=Fabric, `Fo`=Forge, `N`=NeoForge, `L`=LiteLoader, `Q`=Quilt.
  - Example: `Fabric-Support-A[26.0,27.0)-Fa[0.16,0.17)-JE-1.21.4.aep`
  - Example: `NeoForge-Support-A[26.0,27.0)-N[21.4,21.5)-JE-1.21.4.aep`
  - Example: `LiteLoader-Support-A[26.0,27.0)-L[1.7,1.8)-JE-1.16.5.aep`
- **Placement**: JE `<instance>/aprism-extensions/`; BE `com.mojang/aprism_extensions/`.
- **Load order**: Extensions load BEFORE mods. Core scans extensions dir, validates version ranges against running Aprism + MC version, registers capabilities. Only after all extensions register does Aprism scan mod directories.
- **Extension manifest** (`aprism.extension.json`): extensionId, type, aprismRange, loaderRange (for loader-support), mcEdit, mcVersion, entrypoint, provides (capability declarations), depends (other extensions).
- **Conflicts**: Two loader-support extensions for the SAME loader + MC version range is a conflict; Aprism rejects the lower-priority one and logs. Priority = higher loader version range wins.
- **Public statement**: Public docs state "Aprism supports other loaders via Aprism Extensions (*.aep)" without disclosing the internal roadmap or priority versions.

### 9.15 Per-Loader Mod Folder Separation (JE)
- **Principle**: Each mod loader gets its OWN directory. Aprism native mods are separate from loader-specific mods.
- **Folder scheme**:
  - `mods/` -> Aprism native `.aje` packs ONLY.
  - `fabric-mods/` -> Fabric `.jar` mods (requires Fabric-Support.aep).
  - `neoforge-mods/` -> NeoForge `.jar` mods (requires NeoForge-Support.aep).
  - `forge-mods/` -> Forge `.jar` mods (requires Forge-Support.aep).
  - `quilt-mods/` -> Quilt `.jar` mods (requires Quilt-Support.aep).
  - `liteloader-mods/` -> LiteLoader `.litemod` mods (requires LiteLoader-Support.aep).
- If a loader's Support extension is NOT installed, the corresponding folder is simply not scanned (no error, no warning unless mods are present in it).
- Aprism native `.aje` packs in `mods/` use Aprism API exclusively; they do NOT need any loader extension.
- Mixing `.aje` and `.jar` in the SAME folder is no longer the design. `.aje` -> `mods/`; `.jar` -> `/<loader>-mods/`.
- Launchers must respect this separation; flattening `/<loader>-mods/` into `mods/` is non-conformant.

### 9.16 BE Mod Placement and Version Support Scope
- **BE version support**: BE ONLY from 26.x. No BE support for pre-26.x versions (1.21.x BE, etc.). This is a hard scope boundary.
- **BE mod placement** (all `.abe`, since BE has no competing loaders):
  - Native mod binaries: `com.mojang/aprism_mods/<modid>/native/<platform>/`
  - Script API sources: `com.mojang/behavior_packs/<modid>/scripts/` (or `development_behavior_packs/` for dev)
  - BP/RP content: standard Bedrock `behavior_packs/` and `resource_packs/`
  - BE extensions: `com.mojang/aprism_extensions/`
- **BE per-version**: The `com.mojang` path varies by platform (Windows UWP, Android scoped storage, iOS container, BDS). The `aprism_mods/` subdirectory is consistent across all platforms. Version adapter maps MC BE version to signature DB entry.
- **BE loading**: Aprism native loader injects at process start, scans `aprism_mods/`, validates each `.abe` against the running BE version via signature DB, loads native binaries + registers script BPs per-world.
- **BE vs JE scope**: BE does NOT have multiple mod loaders. All BE mods are Aprism native (`.abe`). The extension concept applies to BE for api-extension/platform-adapter/converter types, but NOT for loader-support (there are no competing BE loaders).

### 9.17 Aprism Native Superset Principle
- **JE Native = superset**: Aprism JE Native API is a strict superset of all other JE loaders' APIs. Every capability available in Fabric/Forge/NeoForge/LiteLoader has an Aprism native equivalent (or superior). Mods written for Aprism native get MAXIMUM capabilities.
- **BE Native approaches JE**: Aprism BE Native API mirrors JE Native API where the platform allows. Documented BE-specific limitations: no JVM (native C++ runtime instead), no Java bytecode transformation (native hooks instead), restricted Script API surface. BE extensions can fill gaps via api-extension type.
- **Interface contract**: Aprism native API is monotonic (only increases, never removes/renames). Deprecation allowed with notice. This guarantee applies to BOTH JE and BE native APIs.
- **Loader-specific mods**: Mods using loader-specific APIs (via extensions) get ONLY that loader's capabilities, NOT the Aprism superset. To access the full superset, a mod must be written for Aprism native (`.aje` / `.abe`).

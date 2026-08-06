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
- [TODO] Commit skeleton + versioning correction to GitHub.

## 8. Open Questions / Risks

- macOS/Linux have NO Bedrock client binary; injection is N/A (BDS server only).
- iOS TrollStore route is per-version and maintenance-heavy (research tier only).
- Anti-cheat on BE is server-side + Xbox Live policy; ban risk is real and must be disclosed.
- BE version adapter + signature DB is the single most important engineering investment.
- 1.21.11 -> 26.1 boundary breaks binary compatibility (Mojang shipped unobfuscated at 26.1).

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
- .aje = ZIP containing aprism.manifest.json + jar(s) + platform subdirs (fabric/forge/neoforge/liteloader) + resources/ + mixins/.
- .abe = ZIP containing aprism.manifest.json + behavior_pack/ + resource_pack/ + native/ (per-platform native mod binaries) + scripts/.

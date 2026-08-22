# Aprism

> A cross-platform, cross-edition, cross-version Minecraft mod loader and injector compatible with JE/BE.
> Author: BlockConnect@StarsailsClover | Version: v26.6 (Phase0 internal)

Aprism Loader is a unified mod loading framework that supports JE Fabric, Forge, NeoForge, Quilt and LiteLoader modpacks through Aprism Extensions (.aep, provided by the AprismRefract sub-project), brings a Fabric-like mod loader ecosystem to Bedrock Edition (BE) via native injection, and provides rapid JE-to-BE mod conversion tooling. JE Aprism Native is a modern, JVM-based foundation that forces unified interfaces with a monotonic version contract (only increases, never decreases). The Aprism core itself is native-only: loader support lives outside the core behind the LoaderEntrypointHandler SPI.

## Platform Coverage

| Platform | JE | BE Client | BE Server (BDS) | Strategy |
|---|---|---|---|---|
| Windows | Native (javaagent) | P0 - Proxy DLL + MinHook + libhat | LeviLamina adapter | Primary target |
| Android (root) | - | P1 - Zygisk + ShadowHook | - | Secondary |
| Android (non-root) | - | P1 - Container + preload hijack | - | Secondary |
| iOS/iPadOS | - | P2 - TrollStore + insert_dylib (research) | - | Research tier |
| macOS | Native (javaagent) | N/A (no BE client) | LeviLamina adapter | JE + BDS only |
| Linux | Native (javaagent) | N/A (no BE client) | LeviLamina adapter | JE + BDS only |
| Consoles | - | Impossible (locked down) | - | Not supported |

## Documentation

The full Aprism Loader documentation set is available in English (canonical) and Chinese (copy). See the `docs/` directory.

### English (canonical) - `docs/en/`

| # | Document | File |
|---|---|---|
| 1 | Aprism Loader Overall Architecture Design | [01-architecture-design.md](docs/en/01-architecture-design.md) |
| 2 | Aprism JE / BE Mod Manifest | [02-mod-manifest.md](docs/en/02-mod-manifest.md) |
| 3 | Launcher Adaptation, Download/Install/Management Guide | [03-launcher-guide.md](docs/en/03-launcher-guide.md) |
| 4 | Product Feasibility Report | [04-feasibility-report.md](docs/en/04-feasibility-report.md) |
| 5 | Product Technical Report and Research Methodology | [05-technical-report.md](docs/en/05-technical-report.md) |
| 6 | Product Principle Specification | [06-principle-specification.md](docs/en/06-principle-specification.md) |
| 7 | Mods Pack (.aje/.abe) Structure and Placement | [07-mods-pack-structure.md](docs/en/07-mods-pack-structure.md) |
| 8 | Mod Developer Guide: Export .aje/.abe | [08-developer-guide.md](docs/en/08-developer-guide.md) |
| 9 | Known Issues (v26.2 GA) | [09-known-issues.md](docs/en/09-known-issues.md) |

### Chinese (copy) - `docs/zh/`

| # | Document | File |
|---|---|---|
| 1 | Aprism Loader 鎬讳綋鏋舵瀯璁捐 | [01-鏋舵瀯璁捐.md](docs/zh/01-鏋舵瀯璁捐.md) |
| 2 | Aprism JE / BE 妯＄粍娓呭崟瑙勮寖 | [02-妯＄粍娓呭崟.md](docs/zh/02-妯＄粍娓呭崟.md) |
| 3 | 鍚姩鍣ㄩ€傞厤銆佷笅杞藉畨瑁呬笌绠＄悊妯″潡寮€鍙戞寚鍗?| [03-鍚姩鍣ㄦ寚鍗?md](docs/zh/03-鍚姩鍣ㄦ寚鍗?md) |
| 4 | 浜у搧鍙鎬ф姤鍛?| [04-鍙鎬ф姤鍛?md](docs/zh/04-鍙鎬ф姤鍛?md) |
| 5 | 浜у搧鎶€鏈姤鍛婁笌鐮旂┒鏂规硶 | [05-鎶€鏈姤鍛?md](docs/zh/05-鎶€鏈姤鍛?md) |
| 6 | 浜у搧鍘熺悊璇存槑涔?| [06-鍘熺悊璇存槑涔?md](docs/zh/06-鍘熺悊璇存槑涔?md) |
| 7 | 妯＄粍鍖?(.aje/.abe) 鍒嗙被銆佺粨鏋勪笌鏀剧疆浣嶇疆 | [07-妯＄粍鍖呯粨鏋?md](docs/zh/07-妯＄粍鍖呯粨鏋?md) |
| 8 | Mod 寮€鍙戣€呮寚鍗楋細寮€鍙戝苟瀵煎嚭 .aje/.abe | [08-寮€鍙戣€呮寚鍗?md](docs/zh/08-寮€鍙戣€呮寚鍗?md) |
| 9 | 宸茬煡闂锛坴26.2 GA锛?| [09-宸茬煡闂.md](docs/zh/09-宸茬煡闂.md) |

## Project Tracking

Project decisions, architecture, and session history are tracked in [FACT.md](FACT.md).

## Versioning

Format: `v<Year>.<minor>[-Alpha.<n>][-<MCEdit>-<MCVer>]` (Phase is internal-only, not shown publicly)

- Major line: one per calendar year. `v26` = the 2026 line, containing ten minors `v26.0` ... `v26.9`.
- Development (public): within each minor, `v26.0-Alpha.1` ... `v26.0`, shipped as GitHub Pre-Releases; normal cadence one Alpha every two weeks.
- Minor official: bare version number, e.g. `v26.2`, shipped as a GitHub Release. Alpha.9 is the release candidate; "Alpha 10" is never used.
- Annual edition: `v26.2026` (final improvement pass over `v26.9`), each December, GitHub Release.
- Internal dev tag: `v26.0-Alpha.1-Phase0` ... (Phase tracked only in FACT.md)
- Example artifact: `Aprism-v26.0-JE-26.2.jar`
- Beta is not planned.
- Interface contract: monotonic increment only; deprecation allowed with notice.

## Quick Start

Download `Aprism-<version>-JE-<MCVer>.jar` from [GitHub Releases](https://github.com/NDBlockConnect/Aprism/releases) and attach it as a javaagent:

```bash
java -javaagent:Aprism-v26.2-JE-26.2.jar=aprismVersion=v26.2;mcEdit=JE;mcVersion=26.2;gameRoot=<path-to-game-dir> ...
```

Place `.aje` mods in `<game-dir>/mods/` (see [08-developer-guide.md](docs/en/08-developer-guide.md) for the mod format). Loader-support extensions (`.aep`, e.g. Fabric-Support / NeoForge-Support from the [AprismRefract](https://github.com/NDBlockConnect/AprismRefract) releases) go in `<game-dir>/aprism-extensions/`. Every release is SHA-256 checksummed and cosign keyless-signed; verify with `cosign verify-blob`.

## Distribution

Aprism Loader is distributed as a standalone desktop download only. It is not available on Microsoft Store or Apple App Store (both prohibit dynamic code injection in store-distributed apps). Minecraft is downloaded from Mojang-authorized sources at runtime; Aprism never redistributes modified Minecraft jars.

## License

See [LICENSE](LICENSE).

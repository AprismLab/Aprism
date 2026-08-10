# Aprism

> A cross-platform, cross-edition, cross-version Minecraft mod loader and injector compatible with JE/BE.
> Author: BlockConnect@StarsailsClover | Version: v26.4-Alpha.1 (Phase0 internal)

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
| 1 | Aprism Loader 总体架构设计 | [01-架构设计.md](docs/zh/01-架构设计.md) |
| 2 | Aprism JE / BE 模组清单规范 | [02-模组清单.md](docs/zh/02-模组清单.md) |
| 3 | 启动器适配、下载安装与管理模块开发指南 | [03-启动器指南.md](docs/zh/03-启动器指南.md) |
| 4 | 产品可行性报告 | [04-可行性报告.md](docs/zh/04-可行性报告.md) |
| 5 | 产品技术报告与研究方法 | [05-技术报告.md](docs/zh/05-技术报告.md) |
| 6 | 产品原理说明书 | [06-原理说明书.md](docs/zh/06-原理说明书.md) |
| 7 | 模组包 (.aje/.abe) 分类、结构与放置位置 | [07-模组包结构.md](docs/zh/07-模组包结构.md) |
| 8 | Mod 开发者指南：开发并导出 .aje/.abe | [08-开发者指南.md](docs/zh/08-开发者指南.md) |
| 9 | 已知问题（v26.2 GA） | [09-已知问题.md](docs/zh/09-已知问题.md) |

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

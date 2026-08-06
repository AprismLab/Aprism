# Aprism

> A cross-platform, cross-edition, cross-version Minecraft mod loader and injector compatible with JE/BE.
> Author: BlockConnect@StarsailsClover | Version: v26.0-Alpha1-Phase0

Aprism Loader is a unified mod loading framework that supports JE Fabric, Forge, NeoForge, Quilt and LiteLoader modpacks, brings a Fabric-like mod loader ecosystem to Bedrock Edition (BE) via native injection, and provides rapid JE-to-BE mod conversion tooling. JE Aprism Native is a modern, JVM-based foundation that forces unified interfaces with a monotonic version contract (only increases, never decreases).

## Platform Coverage

| Platform | JE | BE Client | BE Server (BDS) | Strategy |
|---|---|---|---|---|
| Windows | Native (javaagent) | P0 - Proxy DLL + MinHook + libhat | Native (direct link) | Primary target |
| Android (root) | - | P1 - Zygisk + ShadowHook | - | Secondary |
| Android (non-root) | - | P1 - Container + preload hijack | - | Secondary |
| iOS/iPadOS | - | P2 - TrollStore + insert_dylib (research) | - | Research tier |
| macOS | Native (javaagent) | N/A (no BE client) | Native (direct link) | JE + BDS only |
| Linux | Native (javaagent) | N/A (no BE client) | Native (direct link) | JE + BDS only |
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

## Project Tracking

Project decisions, architecture, and session history are tracked in [FACT.md](FACT.md).

## Versioning

- Baseline: v26.0
- Development: `v26.0-Alpha{1-9}-Phase{0-9}`
- Official: `v26.0-PreRelease{N}` -> `v26.0-Release`
- Interface contract: monotonic increment only; deprecation allowed with notice.

## Distribution

Aprism Loader is distributed as a standalone desktop download only. It is not available on Microsoft Store or Apple App Store (both prohibit dynamic code injection in store-distributed apps). Minecraft is downloaded from Mojang-authorized sources at runtime; Aprism never redistributes modified Minecraft jars.

## License

See [LICENSE](LICENSE).

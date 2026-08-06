# Aprism Loader Product Feasibility Report

> Document 4 of 8 | Aprism Loader Documentation Set
> Version: v26.0-Alpha.1 | Status: Development
> Author: BlockConnect@StarsailsClover
> Canonical language: English (Chinese copy maintained in parallel)

---

## 1. Executive Summary

Aprism Loader is a multi-edition, multi-platform Minecraft modding framework that
unifies Java Edition (JE) mod loaders and introduces native Bedrock Edition (BE)
client/server modding under a single product. This report assesses whether the
product is technically buildable, legally defensible, market-viable, and
sustainable to maintain.

The overall verdict is **conditional go**, structured by edition:

- **JE unified loader: highly feasible.** All underlying technology (javaagent,
  Mixin, superset manifests, multi-loader builds) is proven by Fabric, Quilt,
  NeoForge, and MultiLoader-Template. No novel research is required. The risk is
  engineering scope, not feasibility.
- **BE Windows client injection: feasible.** Amethyst, OnixClient, and
  BetterRenderDragon demonstrate that proxy DLL hijack plus MinHook/libhat is a
  production-capable approach. The UWP sandbox is a constraint, not a blocker.
- **BE Android injection: feasible with caveats.** Root path via Zygisk +
  ShadowHook is production-hardened. Non-root container path (NMLauncher
  pattern) works but is legally grey due to APK repackaging. Both are fragile
  across BE updates.
- **BE iOS injection: marginal.** TrollStore enables sideload on a narrow range
  of iOS versions. No community BE iOS mod loader exists at scale. Recommend
  research only, not shipping.
- **BE macOS/Linux client: not feasible.** No Bedrock client binary exists on
  these platforms. Out of scope.
- **BE consoles: not feasible.** Fully locked down. Out of scope.
- **BDS server modding: feasible.** LeviLamina (1553 stars) and LiteLoaderBDS
  demonstrate direct-linking server modding openly and at scale.
- **JE-to-BE conversion: partially feasible.** The Script API cannot express
  Fabric-equivalent features (custom blocks/items/dimensions beyond JSON). Native
  hooks are required for feature parity. Fully automated conversion is not
  achievable; human-assisted conversion is.

The dominant engineering risk is **BE version maintenance**: every Bedrock update
shifts offsets and signatures. This single risk determines whether BE support is
sustainable. Mitigation requires a community-maintained signature database and
automated header generation (the LeviLamina/BedrockAnalyzer pattern).

The recommended delivery sequence is **JE first, BE Windows second, BE Android
third, iOS research-only**. Each phase has explicit go/no-go criteria. The
project should not commit to BE Android or iOS until BE Windows has demonstrated
a sustainable update cadence across at least two Mojang BE releases.

---

## 2. Scope and Assumptions

### 2.1 In scope

- JE unified loader supporting Fabric, NeoForge, Forge, Quilt, and LiteLoader
  via a superset manifest and Mixin.
- BE client injection on Windows (P0), Android (P1), and iOS (P2 research).
- BDS server modding via direct linking.
- A JE-to-BE conversion toolchain (human-assisted, not fully automated).
- Standalone desktop distribution via GitHub Releases with cosign signing.

### 2.2 Out of scope

- BE client modding on macOS, Linux, and consoles (no viable binary or locked
  down).
- Distribution through Microsoft Store, Apple App Store, or Google Play (policy
  prohibited; see section 5.2).
- Modifying Realms servers or official featured servers.
- Redistributing any Mojang-owned binary, asset, or trademark.

### 2.3 Assumptions

- The target user base is technically literate: existing JE mod users who can
  install a custom launcher, and BE power users willing to install standalone
  software outside app stores.
- Mojang continues to permit client-side JE mods under the existing EULA and does
  not retroactively prohibit javaagent-based loaders.
- Microsoft does not extend Xbox Live enforcement to ban JE mod users on
  singleplayer or third-party multiplayer.
- The project has access to at least one contributor with C++/ARM reverse
  engineering skill for BE work, and one with JVM/Mixin expertise for JE work.
- GitHub Releases remains an acceptable distribution channel for modding
  tooling.
- The Bedrock edition release cadence continues at roughly 4-6 major versions
  per year.

Assumptions that, if invalidated, change the verdict are listed in section 10.

---

## 3. Technical Feasibility

### 3.1 JE Unified Loader

**Feasibility: HIGH.** No novel technology is required.

The JE architecture uses a javaagent attached to a tuned OpenJDK runtime. Loader
compatibility (Fabric, NeoForge, Forge, Quilt, LiteLoader) is achieved through a
superset manifest that expresses each loader's metadata model, combined with
Mixin for runtime bytecode transformation. This is the same approach Fabric and
Quilt already use in production. MultiLoader-Template demonstrates that a single
mod source tree can target multiple loaders at build time, validating the
superset-manifest premise. Architectury API further demonstrates that
cross-loader abstraction layers are viable and adopted.

Key engineering work is integration and tooling, not research:
- Superset manifest schema and per-loader projection.
- Mixin reconciliation when multiple loaders co-load.
- Build tooling that emits per-loader artifacts from one source.
- Launcher integration for javaagent attachment.

Residual risk is low and bounded: behavior is well understood because the
component technologies are already deployed to millions of users.

### 3.2 BE Client Injection Per Platform

| Platform | Feasibility | Evidence | Key risk |
|---|---|---|---|
| Windows | FEASIBLE | Amethyst (531 stars), OnixClient, BetterRenderDragon use proxy DLL hijack + MinHook + libhat | UWP sandbox; signature drift per BE update |
| Android (root) | FEASIBLE WITH CAVEATS | Zygisk + ShadowHook (production-hardened by ByteDance/TikTok) | Requires root; fragile across BE updates |
| Android (non-root) | FEASIBLE WITH CAVEATS | NMLauncher demonstrates container approach | Legally grey (APK repackaging); fragile across BE updates |
| iOS | MARGINAL | TrollStore works on iOS 14.0-16.6.1/17.0 only | Per-version re-injection; no community loader at scale; high maintenance burden |
| macOS | NOT FEASIBLE | No Bedrock client binary exists | N/A |
| Linux | NOT FEASIBLE | No Bedrock client binary exists | N/A |
| Consoles | NOT FEASIBLE | Fully locked down; no sideloading | N/A |

The Windows path is the strongest: the community standard (proxy DLL hijack of
`ratification`/`libhat` style hooks, MinHook trampolines, in-process mod host)
is documented and reproduced across at least three independent projects. The UWP
sandbox restricts file system and registry access but does not prevent
DLL hijack when the loader is co-located with the game process. Loader code
must run outside the UWP app container, which is consistent with a standalone
desktop distribution model.

Android root is technically the most production-hardened path because Zygisk and
ShadowHook are maintained by large teams (ByteDance) for production use at
scale. The cost is a root-only user base, which is small.

Android non-root via containerization (NMLauncher pattern) widens the user base
but requires repackaging the Minecraft APK, which is a clear legal grey area
under the EULA and Google Play policy. This path is technically feasible but
should only be pursued after explicit legal review.

iOS is marginal: TrollStore enables unsigned sideloading on a narrow version
range, requires per-version re-injection, and has no community BE mod loader at
scale to validate the approach. Recommend research prototype only.

### 3.3 BE Server (BDS) Modding

**Feasibility: HIGH.** LeviLamina (1553 stars) and LiteLoaderBDS demonstrate
that direct-linking against the BDS binary is a viable, openly-operated model.
BDS is distributed separately by Mojang as a dedicated server binary, and
server-side mods carry lower legal risk than client injection (no Xbox Live
ToS exposure, no client modification).

No injection is required: the loader links directly against the BDS executable
and exposes a mod API. The dominant risk is the same as BE client work
(version drift), but LeviLamina's approach of auto-generating headers via
BedrockAnalyzer demonstrates a sustainable mitigation pattern.

### 3.4 JE-to-BE Conversion

**Feasibility: PARTIAL.** Fully automated conversion is not achievable.

The Bedrock Script API is deliberately constrained. It cannot express custom
blocks, items, or dimensions beyond JSON-defined content, and it cannot perform
the runtime bytecode transformation that Mixin enables on JE. Features that
depend on Fabric-like deep hooks (custom rendering, subsystem replacement,
non-vanilla behavior overrides) require native hooks into the Bedrock binary,
which means the conversion output is itself a BE native mod, not a portable
script.

Realistic conversion scope:
- JSON content (blocks, items, biomes via behavior packs): automatable.
- Simple gameplay logic expressible in the Script API: automatable with manual
  review.
- Custom rendering, custom GUIs, deep subsystem hooks: not automatable; require
  hand-authored native code.

The conversion tooling should be framed as **human-assisted**, not automatic. It
reduces mechanical porting effort but does not eliminate the need for a BE
engineer on the receiving end. Market demand for this feature is unproven (see
section 4.3).

### 3.5 BE Version Maintenance

**Risk: HIGH. This is the dominant engineering challenge.**

Every Bedrock update shifts function offsets, vtable layouts, and symbol
signatures. Without active maintenance, every BE update breaks the loader. This
is not a one-time risk: it is a recurring per-release cost for the lifetime of
the product.

Evidence that this is hard:
- Amethyst's maintainer went partially closed-source citing reverse-engineering
  burden.
- LeviLamina invests in BedrockAnalyzer to auto-generate headers per release,
  which is the only demonstrated sustainable pattern.
- BE updates ship roughly every 4-6 weeks during major release cycles.

Required mitigations:
1. **Signature database.** A versioned, community-maintained database of
   function offsets and signatures per BE release, with provenance tracking.
2. **Automated header generation.** A BedrockAnalyzer-style tool that ingests a
   new BDS/client binary and emits updated headers, reducing manual RE to
   exception cases only.
3. **Community RE pool.** A single maintainer is insufficient; the project must
   recruit and onboard multiple RE contributors to avoid bus factor failure.
4. **Update-readiness CI.** A regression harness that runs against each new BE
   release within 24 hours of availability and reports breakage by component.

If these mitigations are not in place before BE Windows ships, the BE line will
enter a maintenance death spiral within 2-3 BE releases.

---

## 4. Market Feasibility

### 4.1 JE Modding Market

Large and mature. Fabric, NeoForge, and Forge collectively serve millions of
players. Modrinth hosts 30,000+ mods and CurseForge hosts 100,000+. Distribution
infrastructure is established and the user base understands how to install mods.

Cross-loader unification demand is real and observable:
- MultiLoader-Template is widely adopted by mod authors who do not want to
  maintain separate source trees per loader.
- Architectury API exists specifically to abstract loader differences and is
  used by a large share of cross-posted mods.
- Mod authors consistently cite loader-fragmentation as a top pain point in
  community surveys.

Aprism's JE value proposition (one manifest, one build, all loaders) addresses a
demonstrated author-side pain. User-side demand is weaker because players
already have working loaders; Aprism's user-side pull is convenience and unified
management, not capability.

### 4.2 BE Modding Market

Underserved. The official Script API is deliberately limited and cannot express
the features that define JE-style modding. Amethyst and LeviLamina have niche
audiences relative to the BE player base.

Mobile BE (Android and iOS) has the largest Bedrock player base but the smallest
modding scene, because the technical barrier to entry (root, sideload, container
install) is far higher than installing a JE mod. The opportunity is real but
adoption is uncertain: the users who most want BE mods are the least equipped to
install them.

### 4.3 Cross-Edition Demand

Uncertain. JE players rarely want to play BE (different mechanics, different
content parity). BE players who want mods may lack the technical skill for
native injection. The JE-to-BE conversion feature is technically interesting
but its market demand is unproven. It should be treated as a developer
convenience feature for the small population of authors who want to port JE
mods to BE, not as a primary user-facing capability.

### 4.4 Competitive Landscape

| Competitor | Edition | Platform | License | Status | Aprism advantage |
|---|---|---|---|---|---|
| Fabric | JE | Cross-platform JVM | Apache-2.0 | Active, mature | Aprism unifies Fabric with other loaders; not a replacement |
| NeoForge | JE | Cross-platform JVM | LGPL | Active, mature | Aprism unifies; superset manifest |
| Forge | JE | Cross-platform JVM | LGPL | Active, mature | Aprism unifies; superset manifest |
| Quilt | JE | Cross-platform JVM | Apache-2.0 | Active | Aprism unifies; superset manifest |
| Prism Launcher | JE (launcher) | Desktop | GPL-3.0 | Active, mature | Aprism is loader + launcher; Prism is launcher only |
| Amethyst | BE client | Windows | Partially closed | Active, niche | Aprism targets multi-platform BE; open signature DB |
| OnixClient | BE client | Windows | Proprietary | Active, niche | Aprism is open framework; OnixClient is a mod pack |
| LeviLamina | BE server | Windows/Linux BDS | LGPL | Active, niche | Aprism integrates client + server under one product |
| LiteLoaderBDS | BE server | Windows BDS | LGPL | Maintenance | Aprism covers both client and server |
| NMLauncher | BE client | Android (non-root) | Proprietary | Active, niche | Aprism formalizes approach with legal review |

Aprism's differentiation is scope: a single product covering JE unification,
BE client on multiple platforms, and BE server, with an open signature database.
No existing competitor covers this surface area. The risk is that breadth
dilutes depth: each individual segment already has a focused competitor, and
Aprism must match each on quality to be relevant.

---

## 5. Legal and Regulatory Feasibility

### 5.1 Minecraft EULA Analysis

The Minecraft EULA distinguishes between mods created from scratch (permitted)
and modifications that contain substantial Mojang code (prohibited).

- **JE mods: clearly allowed.** Fabric, Forge, and Prism Launcher all operate
  legally under this reading. Aprism must not redistribute modified Minecraft
  jars; it must operate against user-supplied game files. This is the same model
  Prism Launcher uses.
- **BE client modification: legally grey.** EULA section 4(c) prohibits client
  modification that confers unfair advantage. A mod loader itself does not
  confer advantage, but mods that run on it might. The loader must not enable
  cheats by default and must prominently disclose that anti-cheat and official
  servers may treat any client modification as a violation.
- **BDS server mods: tolerated.** LeviLamina and LiteLoaderBDS operate openly.
  BDS is a separate downloadable binary, and server-side mods do not touch Xbox
  Live. This is the lowest-risk BE segment.

### 5.2 Platform Store Policies

- **Microsoft Store Policy 10.2** forbids apps that perform dynamic code
  injection into other apps. Aprism cannot be distributed through the Microsoft
  Store. This is consistent with Prism Launcher's distribution model.
- **Apple App Store 2.4.5(iv)** forbids apps that execute or inject code into
  other processes. Aprism cannot be distributed through the App Store. iOS
  distribution would be limited to TrollStore sideloading, which itself depends
  on a narrow iOS version range.
- **Google Play** policy similarly restricts apps that modify other apps. The
  non-root Android path (APK repackaging) is not Play-distributable.

Conclusion: standalone desktop download is the only legally safe distribution
channel. This is not a limitation unique to Aprism; it is the standard model for
modding tooling.

### 5.3 Xbox Live and Account Ban Risk

Real for BE client mods. Xbox Live terms of service treat client modification as a
violation that can result in account suspension. This applies to BE clients
connecting to Realms, featured servers, and any Xbox Live-integrated
multiplayer. Risk is lower for singleplayer and LAN, and zero for BDS server
mods that do not touch the client.

Aprism must:
- Disclose ban risk in the installer, in first-run UI, and in documentation.
- Default to offline or LAN-only modes for BE client installs.
- Warn the user explicitly before any BE client connects to a Realms or
  featured server.

### 5.4 Distribution Legality

Standalone desktop download is legally safe (Prism Launcher precedent). GitHub
Releases as a CDN is acceptable. Mirroring through Modrinth is acceptable.
Distribution must:
- Not use Mojang branding, logos, or trademarks.
- Not bundle Mojang binaries or assets.
- Require the user to supply their own legitimately-acquired game files.
- Sign releases (cosign) to provide a tamper-evident supply chain.

### 5.5 Realms and Official Servers

Realms cannot be modded: the user does not control the server binary. Modded BE
clients connecting to Realms risk account bans (section 5.3). Official featured
servers (Hive, CubeCraft, etc.) run their own anti-cheat and ban modified
clients. Aprism must treat Realms and featured servers as off-limits for client
mods and surface this in the UI.

### 5.6 Intellectual Property

Mods belong to their creators provided they do not contain substantial Mojang
code. Aprism's role is to provide a loader and tooling; it does not claim
ownership of mods. Aprism itself must not redistribute Mojang code, assets, or
trademarks. The signature database and headers generated from BE binaries are
derivative works of those binaries and must be handled carefully: headers that
contain only function signatures (not decompiled source) are the
LeviLamina-style approach and are the safest practical model.

---

## 6. Risk Assessment

Risk register. Likelihood and impact scored 1 (low) to 5 (high). Score =
likelihood x impact.

| # | Risk | Category | Likelihood | Impact | Score | Mitigation | Owner |
|---|---|---|---|---|---|---|---|
| R1 | BE update breaks signatures, loader stops working | Technical | 5 | 5 | 25 | Signature DB, automated header generator, update-readiness CI, community RE pool | BE lead |
| R2 | Single-maintainer bus factor on BE RE | Resource | 4 | 5 | 20 | Recruit >=3 RE contributors; document RE process; pair on signature updates | BE lead |
| R3 | Xbox Live account bans for BE client users | Legal | 4 | 4 | 16 | Prominent disclosure; offline/LAN defaults; warn before Realms/featured server connect | Legal/PM |
| R4 | Microsoft Store / Apple policy enforcement against standalone distribution | Legal | 2 | 4 | 8 | Standalone download only; no store presence; monitor policy changes | Legal |
| R5 | BE Android non-root APK repackaging challenged legally | Legal | 3 | 4 | 12 | Legal review before ship; consider root-only initially; user-supplied APK | Legal |
| R6 | iOS TrollStore version window closes (new iOS breaks jailbreak-free sideload) | Technical | 4 | 3 | 12 | Treat iOS as research-only; no ship commitment | BE lead |
| R7 | JE Mixin conflicts across co-loaded loaders | Technical | 3 | 3 | 9 | Mixin reconciliation layer; per-loader isolation; conflict test suite | JE lead |
| R8 | JE-to-BE conversion produces non-functional output | Technical | 3 | 3 | 9 | Frame as human-assisted; ship conversion as assist tool, not auto-converter | Conversion lead |
| R9 | Market adoption below break-even for BE investment | Market | 3 | 4 | 12 | Phase BE investment behind JE; go/no-go gate after BE Windows | PM |
| R10 | Mojang EULA change prohibiting javaagent loaders | Legal | 1 | 5 | 5 | Monitor EULA; maintain pluggable architecture; community advocacy | Legal |
| R11 | Signature DB poisoning or supply-chain attack | Security | 2 | 5 | 10 | cosign releases; signature DB integrity checks; contributor review; reproducible builds | Security |
| R12 | User installs Aprism on wrong BE version, crashes game | Technical | 4 | 2 | 8 | Version detection at launch; refuse to load on unsupported BE; clear error UI | BE lead |
| R13 | Trademark/branding complaint from Mojang | Legal | 2 | 3 | 6 | No Mojang branding; neutral product name; review all marketing assets | Legal |
| R14 | CI/release infrastructure cost overruns | Resource | 2 | 2 | 4 | GitHub-hosted CI; sponsor budget; mirror only what is needed | Infra |

The two highest-scoring risks (R1, R2) are both BE version maintenance. This
confirms section 3.5: BE sustainability is the single most important risk to
manage. If R1 and R2 cannot be mitigated, the BE line should not ship.

---

## 7. Resource Requirements

### 7.1 Skills

| Skill area | Required for | Depth |
|---|---|---|
| JVM, Java, bytecode | JE loader, Mixin, manifest | Expert |
| Gradle, build tooling | JE multi-loader builds, release automation | Expert |
| C++, Win32, UWP | BE Windows client injection | Expert |
| ARM, Android NDK | BE Android injection | Expert |
| Reverse engineering (IDA/Ghidra) | BE signature/offset maintenance | Expert |
| iOS, Mach-O, TrollStore | BE iOS research | Intermediate (research only) |
| Security, code signing | cosign, signature DB integrity, supply chain | Expert |
| Technical writing | Documentation set (8 documents) | Intermediate |
| Legal review (EULA, store policy) | Distribution, disclosure | External counsel |

### 7.2 Team size estimate

Minimum viable team for the phased plan:
- JE: 2 contributors (1 lead, 1 build/tooling).
- BE Windows: 2 contributors (1 lead/RE, 1 mod API).
- BE Android: 1 contributor (shared with BE Windows RE).
- BDS server: 1 contributor (can be shared with BE Windows).
- Signature DB / header generator: 1 contributor (shared RE).
- Infra/CI/release: 1 contributor (part-time).
- Docs: 1 contributor (part-time).

Realistic minimum: 4-5 active contributors, with at least 2 holding RE skill.
A single-maintainer model is not viable for the BE line; the Amethyst precedent
shows that a single maintainer cannot sustain RE load across BE updates.

### 7.3 Infrastructure

- GitHub repository and Releases as primary CDN.
- GitHub Actions CI for build, test, and release signing.
- cosign-based release signing with a documented public key.
- Signature database repository with versioned per-BE-release data and
  provenance metadata.
- Issue tracker and documentation site (GitBook or equivalent).
- Optional Modrinth mirror for JE artifacts.

No paid infrastructure is required for the Alpha phases. Cost becomes material
only at scale (mirror bandwidth, signing key HSM if enterprise-grade signing is
later required).

---

## 8. Phased Delivery Recommendation

### Phase 0 (Alpha 1-3): JE foundation

Scope: JE unified loader, superset manifest, multi-loader build tooling, basic
launcher integration. Prove the foundation on JE only.

Go/no-go criteria:
- JE loader runs Fabric, NeoForge, Forge, Quilt mods from a single install.
- A representative sample of 20 popular mods across loaders runs without
  regressions versus native loader installs.
- Build tooling produces per-loader artifacts from a single source tree.
- No EULA or distribution blocker identified by legal review.

### Phase 1 (Alpha 4-6): BE Windows client

Scope: BE Windows client injection, mod API, signature DB for current BE
release. Prove the BE concept on the best-supported platform.

Go/no-go criteria:
- Loader runs against the current stable BE Windows release.
- At least 5 reference mods load and function.
- Signature DB update for one subsequent BE release completed within 7 days of
  that release shipping.
- Update-readiness CI reports breakage within 24 hours of a new BE release.
- Ban-risk disclosure reviewed and shipped in installer and first-run UI.

If the 7-day signature update criterion is not met, do not proceed to Phase 2;
revisit RE process and tooling first.

### Phase 2 (Alpha 7-9): BE Android + BDS server

Scope: BE Android root path first; non-root container path after legal review.
BDS server modding via direct linking.

Go/no-go criteria:
- Android root path runs against current stable BE Android release.
- Non-root path gated on legal review sign-off.
- BDS server loader runs reference server mods.
- Signature DB covers Windows, Android, and BDS for the current BE release.

### Phase 3 (PreRelease): Conversion + iOS research + polish

Scope: JE-to-BE conversion tooling (human-assisted), iOS research prototype,
documentation completion, UX polish.

Go/no-go criteria:
- Conversion tooling reduces manual port effort by a measured factor versus
  hand-porting, on a reference set of 5 JE mods.
- iOS prototype runs on at least one supported iOS version with no commitment
  to ship.
- All 8 documentation set documents complete and reviewed.

### Phase 4 (Release): v26.0 stable

Scope: v26.0 stable release, community onboarding, contributor guide for
signature DB maintenance, long-term support policy.

Go/no-go criteria:
- Phase 0-3 criteria all sustained across at least two BE releases.
- Contributor guide published and at least one external contributor has
  submitted a signature DB update.
- Security review of release signing and signature DB integrity complete.

---

## 9. Go/No-Go Recommendation

**Verdict: GO, conditional.**

Proceed with Aprism Loader under the following conditions:

1. **JE first.** Phase 0 must complete and meet its go/no-go criteria before any
   BE engineering investment beyond research. JE is low-risk and demonstrates
   the product's value even if BE never ships.
2. **BE Windows is the BE go/no-go gate.** Do not commit to BE Android or iOS
   until BE Windows has demonstrated sustainable update cadence across at least
   two Mojang BE releases, measured by the 7-day signature update criterion.
3. **Signature DB and header generator must be in place before BE Windows
   ships.** These are not post-launch improvements; they are launch blockers.
   Without them R1 and R2 will force the BE line into maintenance failure.
4. **Standalone distribution only.** No Microsoft Store, Apple App Store, or
   Google Play presence. This is non-negotiable under store policies.
5. **Ban-risk disclosure is a launch blocker.** Installer, first-run UI, and
   documentation must disclose Xbox Live ban risk for BE client users before
   any BE client build is distributed.
6. **Non-root Android path requires explicit legal review** before ship, because
   APK repackaging is a legal grey area.
7. **iOS is research-only through Release.** No ship commitment.

If conditions 1-5 are met, the project is feasible and should proceed. If
condition 3 cannot be met (no signature DB / header generator), the BE line
should be descoped to BDS server only, where the maintenance burden is lower.

---

## 10. Assumptions Log

Assumptions that, if invalidated, change the verdict:

| # | Assumption | If invalidated | Impact on verdict |
|---|---|---|---|
| A1 | Mojang EULA continues to permit client-side JE mods and javaagent loaders | EULA change prohibits javaagent | JE line blocked; product descoped to BE only |
| A2 | Microsoft does not extend Xbox Live enforcement to JE mod users | JE users face Xbox Live bans | JE adoption collapses; product viability at risk |
| A3 | GitHub Releases remains acceptable for modding tooling distribution | GitHub policy change or takedown | Distribution blocked; need alternate CDN |
| A4 | Bedrock release cadence remains roughly 4-6 major versions per year | Cadence doubles | R1 score rises; signature DB maintenance cost doubles; BE sustainability at risk |
| A5 | At least one contributor with C++/ARM RE skill is available long-term | Contributor leaves, no replacement | R2 triggers; BE line cannot be maintained; descope to JE + BDS only |
| A6 | TrollStore remains functional on at least one current iOS version | Apple patches all TrollStore vectors | iOS research path closed; iOS descoped entirely |
| A7 | LeviLamina-style header auto-generation is achievable for Aprism's BE scope | Headers require manual RE per release | R1 unmitigated; BE Windows not sustainable past 2-3 releases |
| A8 | Modrinth continues to permit loader/mirrored artifact hosting | Policy change | JE distribution limited to GitHub; reduced discoverability |
| A9 | Mojang does not assert trademark claims against the product name or assets | Trademark complaint | Forced rename and rebrand; non-blocking but costly |
| A10 | Standalone desktop distribution remains legally safe in target jurisdictions | New regulation or ruling | Distribution model must change; possible scope reduction |

Each assumption should be reviewed at every phase gate. A1, A4, A5, and A7 are
the highest-leverage: invalidating any one of them is sufficient to force a
major rescope.

---

## 11. References

Primary sources reviewed for this report:

- Minecraft End User License Agreement (Mojang).
- Microsoft Store Policies, section 10.2 (dynamic code injection).
- Apple App Store Review Guidelines, section 2.4.5(iv).
- Xbox Live Terms of Service.
- Fabric Loader source and documentation (javaagent + Mixin model).
- Quilt Loader source and documentation.
- NeoForge and Forge loader documentation.
- MultiLoader-Template (multi-loader build pattern reference).
- Architectury API (cross-loader abstraction precedent).
- Prism Launcher (standalone-distribution legal precedent).
- Amethyst (BE Windows client injection reference implementation).
- OnixClient (BE Windows client mod pack reference).
- BetterRenderDragon (BE render-path injection reference).
- NMLauncher (BE Android non-root container reference).
- Zygisk and ShadowHook (Android root injection, production-hardened by
  ByteDance).
- LeviLamina and BedrockAnalyzer (BDS server direct-linking and automated
  header generation).
- LiteLoaderBDS (BDS server modding precedent).
- TrollStore (iOS sideload capability and version coverage).
- Modrinth and CurseForge mod catalogs (JE market size indicators).

Star counts and catalog sizes cited in this report reflect values observed at
the time of research and should be re-verified at each phase gate. No claim is
made about the current state of any referenced project beyond its demonstrated
technical precedent.

---

End of Document 4 of 8.

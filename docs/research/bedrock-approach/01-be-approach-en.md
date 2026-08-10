# Aprism BE: Approach Research Report (v26.2-Alpha.4, goal #5)

> Author: BlockConnect@StarsailsClover
> Status: research deliverable; informs the BE native platform injector line.
> Language: English (canonical). Chinese copy: `02-be-approach-zh.md`.
> Sources are cited inline; claims about third-party projects are as of
> 2026-08-10 and re-checked against their repositories before writing.

## 1. Problem Statement

Minecraft Bedrock Edition (BE) is a closed-source C++ binary with no
official native mod SDK. The official Script API (`@minecraft/server`) is a
sandboxed JavaScript runtime that cannot express custom blocks, items, or
dimensions beyond JSON-defined content. Native modding is therefore the only
path to JE-mod feature parity on BE.

Two facts dominate the design space:

1. **Version fragility.** Bedrock updates shift function offsets and struct
   layouts unpredictably. Every production BE loader (Amethyst, LeviLamina)
   addresses this with per-version reverse-engineered headers plus a
   signature/offset database. There is no stable ABI to link against.
2. **Per-platform code signing and process protection.** Each BE platform
   (Windows, Android, iOS, BDS server) imposes different injection
   constraints: DLL load rules on Windows, SELinux + Zygote process model on
   Android, re-signing requirements on iOS, and no anti-cheat but no
   persistence on BDS.

Aprism's committed scope (FACT.md 9.16): BE support from version 26.x
onwards only; fail-closed version resolution; the version adapter +
signature database is the single most important engineering investment.

## 2. What Already Exists in Aprism (v26.1 line)

The v26.1 line delivered the complete **Java-side** BE foundation
(aprism-loader-core `com.aprism.loader.bedrock`):

| Component | Responsibility |
|---|---|
| `BedrockVersionDatabase` | Fail-closed version + signature database (JSON schema v1) |
| `BedrockVersionAdapter` | Normalizes raw BE versions, enforces the 26.x scope boundary, resolves against the DB |
| `BedrockSignatureDbLoader` | Loads and validates the on-disk signature DB |
| `BedrockInjectionPlan` | Pure-Java injection plan computation, version-gated before mod collection |
| `BedrockInjectionCoordinator` | Wires adapter -> platform detection -> planning into `AprismRuntime` |
| `BedrockNativeStager` | Stages per-platform native libraries from `.abe` archives |
| `NativeInjector` (SPI) | The seam that a native platform injector implements |

The `.abe` discovery, per-platform native library resolution
(`windows-x64`/`android`/`ios`...), and BP/RP/scripts content detection are
also in place (v26.0 line). What remains is the **native half**: the actual
process attachment, symbol resolution, and hook application per platform.

## 3. Candidate Routes

### 3.1 Windows client: proxy-DLL hijack + inline hooking

The game loads `version.dll` from its own directory before the system one.
A proxy DLL with the same export table loads first, giving Aprism code
execution inside the game process at a well-defined point (DllMain of a
legitimately loaded module). Manual-map injection is the fallback for
hardened launchers that pre-load the real `version.dll`.

- **Hooking:** MinHook or SafetyHook for inline hooks; libhat for signature
  scanning to locate functions from the signature DB.
- **Precedent:** Amethyst (FrederoxDev) uses exactly this stack (libhat +
  MinHook, xmake build) for MCBE 1.21-1.26.x client modding. As of this
  writing Amethyst's core runtime remains open source, with 1.26.x support
  shipped as a "bring your own types" library.
- **Risk:** Windows Defender heuristics on manual mapping; mitigated by
  signed builds and preferring the proxy-DLL path.
- **Verdict:** proven, lowest research risk. **Primary route (P0).**

### 3.2 Android (root): Zygisk module + ShadowHook

A Magisk/Zygisk module injects into the game process spawned by Zygote via
`LD_PRELOAD`-style specialization, then applies inline hooks with
ShadowHook (PLT + inline, ART-aware).

- **Precedent:** the standard stack for Android game hooking; LeviLamina's
  community tooling and multiple BE mod managers already ship Zygisk-based
  loaders.
- **Risk:** requires root (Magisk/KernelSU); SELinux denials on hardened
  ROMs; Zygisk API churn between Magisk forks.
- **Verdict:** proven where root is acceptable. **P1.**

### 3.3 Android (non-root): container + preload hijack

Run the game inside an app container (NMLauncher-style) that controls the
process environment, injecting a preload library the container owns.

- **Risk:** the container must be rebuilt per Android/BE combination;
  Google Play integrity checks may flag containerized installs; higher
  maintenance surface than the root path.
- **Verdict:** viable but the most fragile Android path. **P1 (after root
  route stabilizes).**

### 3.4 iOS/iPadOS: TrollStore side-load + re-sign

`insert_dylib` adds an `LC_LOAD_DYLIB` load command to the BE binary; the
app is re-signed and installed via TrollStore (no developer account). Hooks
via Dobby with ElleKit as the fallback substrate on newer iOS versions.

- **Risk:** per-version and maintenance-heavy; TrollStore availability is
  iOS-version-gated; App Store policies forbid dynamic code injection, so
  this route is explicitly out-of-store (FACT.md 9.11).
- **Verdict:** research tier only. **P2.**

### 3.5 macOS/Linux: BDS server via a LeviLamina-style loader

There is no Bedrock *client* binary for macOS/Linux; only the dedicated
server (BDS) runs there. LeviLamina (LiteLDev) is the de-facto BDS mod
loader and tracks current Bedrock version numbering; it is actively
maintained and installs via its `lip` package manager.

- **Option A (recommended):** treat LeviLamina as the BE-server substrate
  and ship Aprism BE mods for BDS as LeviLamina-compatible packages, with an
  Aprism-side adapter that translates the `.abe` native layer into
  LeviLamina's plugin format. This avoids duplicating BDS injection work.
- **Option B:** build a first-party BDS injector. High effort, duplicates
  an ecosystem that already solves the problem, and fragments the signature
  database effort.
- **Verdict:** **Option A.** Integrate, don't fork.

### 3.6 Consoles

Locked down; code execution for modding is not feasible. **Out of scope.**

## 4. Recommended Architecture

Adopt the LeviLamina three-layer pattern (FACT.md 9.9) for the native
loader core, shared across Windows/Android/iOS:

1. **Reverse-engineered headers layer** — auto-generated by a
   `header_generator` toolchain fed by `BedrockAnalyzer` outputs; exposes
   class layouts, vtable indices, and function signatures as headers.
2. **Core layer** — mod registrar, hook manager, and the **version adapter
   + signature DB client**. The DB lives in the same JSON schema v1 already
   shipped in Aprism's Java side (`BedrockSignatureDbLoader`), so one
   community-maintained signature repository serves both the Java planner
   and the native resolver.
3. **Public API layer** — events, commands, registry access; multi-language
   mods (C++/Lua/C#/Rust) via the manifest `type` field.

Source layout: `src/` (shared), `src-client/` (client entrypoints),
`src-server/` (BDS entrypoints).

### Fail-closed contract (carried from the Java side)

- A native injector must refuse to attach when the running BE version is
  pre-26.x, outside the signature DB, or has unresolved required signatures.
- Every refusal produces a human-readable report through the Java
  coordinator's existing refusal reasons (`UNPARSEABLE` / `OUT_OF_SCOPE` /
  `NOT_IN_DATABASE`).

### NativeInjector implementations (one per route)

| Platform | Implementation | Loads via | Hooks via |
|---|---|---|---|
| Windows | `aprism-be-windows` | proxy `version.dll` (manual map fallback) | MinHook/SafetyHook + libhat |
| Android root | `aprism-be-android` | Zygisk module | ShadowHook |
| Android non-root | `aprism-be-android-container` | container preload | ShadowHook |
| iOS | `aprism-be-ios` (research) | TrollStore re-signed dylib | Dobby / ElleKit |
| BDS server | adapter over LeviLamina | LeviLamina plugin | LeviLamina API |

## 5. Phased Plan

| Phase | Milestone | Exit criteria |
|---|---|---|
| P0 | Windows proxy-DLL injector MVP | real BE 26.x boots with Aprism attached; one native hook fires; fail-closed on unknown version |
| P1 | Signature DB community pipeline | signature repository public; adapter consumes it end-to-end on Windows |
| P2 | Android root injector | Zygisk module attaches on a real device |
| P3 | BDS adapter | LeviLamina-compatible `.abe` package loads a hello-world native mod |
| P4 | Android non-root + iOS research | container route evaluated; iOS spike report |

## 6. Risks and Mitigations

| Risk | L | I | Mitigation |
|---|---|---|---|
| Bedrock update shifts offsets | High | High | signature DB + fail-closed adapter + rapid community signature updates |
| Windows Defender flags injection | Med | Med | signed builds; prefer proxy-DLL over manual map |
| Zygisk API churn across Magisk forks | Med | Med | pin Zygisk API version; abstract behind a thin shim |
| TrollStore availability gates iOS | Med | Low | keep iOS research-tier; publish version-support matrix |
| Ban risk on Live-enabled worlds | Low | High | explicit disclosure (Doc 01 §13.2); recommend offline worlds |
| Duplicating BDS work | Med | Med | LeviLamina adapter (Option A) instead of first-party injector |

## 7. Decision Record

- **DEC-BE-1:** Windows proxy-DLL + MinHook/SafetyHook + libhat is the
  primary native route (P0). Manual map is fallback only.
- **DEC-BE-2:** The signature database stays JSON schema v1, shared between
  the Java planner and every native injector; community-maintained
  repository is the source of truth.
- **DEC-BE-3:** BDS server support integrates with LeviLamina rather than
  forking a first-party injector.
- **DEC-BE-4:** Android root precedes Android non-root; iOS remains
  research-tier until TrollStore stability improves.
- **DEC-BE-5:** All native injectors honor the fail-closed contract; no
  speculative hooking against unresolved signatures.

## 8. Open Questions

- Should the native core be C++ (LeviLamina parity) or Rust (memory
  safety)? C++ matches the ecosystem and tooling today; Rust raises FFI and
  header-generation friction. Deferred to the P0 spike.
- Signature DB governance: single repo under Aprism org vs. a neutral
  community org (AmethystAPI/Community-Headers precedent). Deferred to P1.
- Console-adjacent platforms (handheld Windows devices) inherit the Windows
  route; no separate work planned.

## 9. References

- FACT.md §9.8 (BE injection per-platform), §9.9 (BE loader architecture),
  §9.16 (BE 26.x-only scope).
- Docs 01 (Architecture Design) §7 (BE injection and loader subsystem),
  §13.2 (Bedrock ban risk disclosure).
- Amethyst — github.com/FrederoxDev/Amethyst (MCBE 1.21-1.26.x client
  loader; libhat + MinHook; core runtime open source, checked 2026-08-10).
- LeviLamina — github.com/LiteLDev/LeviLamina (BDS mod loader tracking
  current Bedrock version numbering; installed via `lip`; checked
  2026-08-10).
- libhat, MinHook, SafetyHook, ShadowHook, Dobby, ElleKit, Zygisk: hooking
  and signature-scanning libraries as cited in Doc 01 §15.

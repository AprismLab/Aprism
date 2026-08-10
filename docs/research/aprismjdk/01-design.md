# AprismJDK Design Document (v26.4-Alpha.1, goal: AprismJDK subproject)

> Research + design deliverable. This document defines what AprismJDK is,
> why it exists, its architecture, and the roadmap to a buildable OpenJDK
> variant. Implementation of the JDK build itself is a later milestone;
> this alpha ships the design, the subproject skeleton, and the governance
> contract.
>
> Author: BlockConnect@StarsailsClover. Companion Chinese copy:
> `docs/zh/research/aprismjdk/`. Repository: `AprismLab/AprismJDK`
> (GPL-2.0).

---

## 1. Positioning

**AprismJDK** (working brand: **AJR — Aprism Java Runtime**) is an
OpenJDK-variant distribution that exists to serve the Aprism loader
ecosystem first and the general Java community second. It is **not** a
fork that diverges from OpenJDK's language/VM semantics; it is a *build +
patch layer* on top of upstream OpenJDK, in the same family as Amazon
Corretto, Azul Zulu, Microsoft Build of OpenJDK, and Red Hat OpenJDK.

The differentiator is that AprismJDK is designed **bottom-up around the
Aprism agent model**: the JDK ships an **AprismateAgent** (a
JavaAgent-like low-level agent) and a set of **opened, stable JVM
interfaces** that Aprism's deep API targets. Where mainstream
distributions optimize for server throughput and enterprise LTS, AprismJDK
optimizes for:

1. **Low-level reach** — first-class support for class redefinition,
   method hooking, and bytecode transformation at runtime (the same
   capabilities Aprism's `ClassRedefiner`/`MethodHookRegistry` expose, but
   hardened at the VM level).
2. **Performance + hardware fusion** — exposing CPU-feature detection,
   cache-line/NUMA awareness, and vectorization hints as stable APIs.
3. **Open interfaces** — exposing more JVM internals through a *stable,
   versioned* surface rather than through reflection hacks.
4. **Cross-language transition** — first-class Cpp2Java / Rust2Java
   bridges built on the Foreign Function & Memory (FFM) API.
5. **Extreme compatibility** — a compatibility matrix that spans Java
   versions, so mods built against one AprismJDK line keep running across
   updates.

### 1.1 Why a custom JDK at all?

Aprism already achieves loader-level goals (Mixin weaving, multi-loader
parity, typed registries, event buses) **on stock OpenJDK** via the
`java.lang.instrument` + ASM + Mixin stack. A custom JDK is justified only
where the stock JVM is a *hard ceiling*:

- **Capability ceilings.** Some low-level operations (e.g. redefining
  classes with added/removed fields, hooking JIT-inlined hot methods
  reliably, reading certain VM-internal state) are impossible or fragile
  on stock HotSpot through `Instrumentation` alone. A patched VM can
  support them safely.
- **Performance ceilings.** Aprism wants deterministic, low-overhead
  hooks. Stock HotSpot's safepoints, deoptimization, and inlining can
  fight agent-injected code. A VM aware of Aprism's hook points can keep
  them cheap.
- **Interface ceilings.** Aprism's deep API today reaches into the JVM via
  `ManagementFactory`, `Instrumentation`, and FFM. A custom JDK can
  promote the most-used of these into *named, stable* interfaces.

The design therefore treats AprismJDK as an **enabler**, not a
requirement: every Aprism capability must still work on stock OpenJDK
(graceful degradation), and AprismJDK merely *unlocks* the deeper tier.

---

## 2. Relationship to the Aprism project

```
                 +-------------------------------------+
                 |            Aprism (loader)          |
                 |  Mixin / event bus / registries /   |
                 |  parity surfaces (works on stock JDK)|
                 +-------------------------------------+
                                  |  deep API calls
                                  v
                 +-------------------------------------+
                 |         AprismJDK (AJR runtime)     |
                 |  AprismateAgent + opened VM seams + |
                 |  perf/hardware APIs + FFM bridges   |
                 +-------------------------------------+
                                  |
                                  v
                 +-------------------------------------+
                 |        upstream OpenJDK 25 (LTS)     |
                 +-------------------------------------+
```

- **Aprism** stays JDK-agnostic. Its deep API (v26.4 line) detects whether
  it runs on AprismJDK and upgrades its behaviour when it does.
- **AprismJDK** is versioned independently but *tracks* an upstream LTS
  line. v26.4 targets **OpenJDK 25** (current LTS, GA 2025-09, supported
  to ~2032). When upstream moves to the next LTS, AprismJDK rebases.
- The two coordinate through a **capability descriptor** (see §6): Aprism
  asks the runtime "which AprismJDK capabilities do you expose?", and the
  runtime answers with a versioned capability set.

---

## 3. AprismateAgent

The **AprismateAgent** is the flagship component: a JavaAgent-like agent
that is *bundled with* the JDK image and reachable without any external
jar.

### 3.1 Entry points

Like a standard JavaAgent it supports both modes, but with JVM-side
support:

| Mode | Trigger | Use |
|------|---------|-----|
| `premain` | `-javaagent:aprismate.jar=...` (or auto-attached on AprismJDK) | Load-time weaving, boot-time hook registration |
| `agentmain` | Attach API at runtime | Hot-attach of hooks to a running game |

On AprismJDK the agent can additionally be **auto-loaded** via a JVM flag
(`-XX:+AprismateAgent`) that wires the agent before `main`, removing the
need for the launcher to pass `-javaagent`. This is the "deep" part: the
VM knows about the agent, so it can install hooks at points stock agents
cannot reach.

### 3.2 Capabilities

The agent exposes a stable programmatic surface (`com.aprismate.api`):

- **ClassRedefiner+** — redefine classes including structural changes
  (add/remove fields/methods) that stock `Instrumentation.redefineClasses`
  refuses. Backed by a patched HotSpot that performs safe class
  redefinition with instance migration.
- **MethodHookRegistry+** — register entry/exit hooks on any method,
  including JIT-compiled ones, with the VM guaranteeing the hook survives
  inlining (the VM treats hook points as deoptimization anchors).
- **BytecodeTransformer** — an ASM-backed pipeline hook that sees classes
  at load time, before verification, enabling Mixin-style weaving without
  a separate Mixin runtime.
- **VmIntrospection** — read thread stacks, class statistics, heap
  summaries, and JIT/GC state through named methods instead of JMX
  reflection.

### 3.3 Fail-safe contract

Every agent capability is **fail-closed into the game, never the VM**: a
bad hook is logged and skipped; a failing transformation falls back to the
untransformed class. The agent must never crash the JVM. This mirrors the
fail-safe discipline Aprism already applies to its loader.

---

## 4. Opened interfaces & capabilities

AprismJDK promotes frequently-used JVM internals into a **stable,
versioned API module** (`jdk.aprismate`). Concretely:

- `jdk.aprismate.Agent` — the AprismateAgent programmatic entry.
- `jdk.aprismate.VmInfo` — VM build identity, AprismJDK version,
  capability set.
- `jdk.aprismate.runtime.ThreadInsight` — thread-stack and scheduling
  introspection.
- `jdk.aprismate.runtime.HeapInsight` — heap region / GC introspection.
- `jdk.aprismate.runtime.JitInsight` — compilation-queue and method-
  compilation introspection.

These are **additive** to the JDK (no removal or renaming of stock APIs),
so a program compiled against stock OpenJDK runs unchanged on AprismJDK,
and vice versa for the non-AprismJDK subset.

---

## 5. Performance optimization & hardware fusion

AprismJDK exposes hardware awareness as first-class, stable APIs rather
than leaving mods to parse `/proc/cpuinfo` or call JNI:

- **CpuFeatures** — detected instruction-set features (SSE4.2, AVX2,
  AVX-512, NEON, SVE) with a capability-token API.
- **CacheTopology** — cache-line size and cache-hierarchy hints, enabling
  mods to pad hot structures against false sharing.
- **NumaTopology** — NUMA node enumeration and affinity hints (where the
  OS exposes them).
- **VectorHints** — a thin, safe surface suggesting vectorization-friendly
  loop shapes (advisory only; never a correctness dependency).

The design principle: **advisory, never mandatory.** A mod that reads
`CpuFeatures` must degrade gracefully when the API is absent (stock JDK)
or when a feature is missing on the host.

---

## 6. Cross-language transition (Cpp2Java / Rust2Java)

AprismJDK standardizes foreign interop on the **Foreign Function & Memory
(FFM) API** (final in JDK 22+), and adds generator + runtime layers:

- **Cpp2Java** — a binding generator that consumes C/C++ headers and emits
  Java-side downcall stubs + upcall wrappers, plus a runtime that owns the
  native-library lifecycle (load, symbol resolution, arena-scoped memory).
- **Rust2Java** — the same pipeline targeting Rust `extern "C"` exports,
  with `cbindgen`-style header generation on the Rust side and FFM
  downcalls on the Java side.

Both bridges share a common **ABI mapping** document (primitive widths,
struct layout, string/pointer ownership, error propagation) and a common
**lifecycle convention** (who allocates, who frees, arena scope). This is
the foundation Aprism needs to eventually host native BE/Java interop and
to let AprismRefract's native bridges (Zygisk, proxy-DLL) share one
runtime.

---

## 7. Extreme compatibility (cross-Java-version)

AprismJDK commits to a **compatibility matrix**:

1. **Forward compatibility of the capability descriptor.** Mods query
   capabilities, never assume them. A mod written for AprismJDK 25 must
   run on AprismJDK 26+ with reduced-but-working capability.
2. **Stock-JDK fallback.** Every AprismJDK-specific API has a documented
   stock-JDK equivalent or a documented no-op. Aprism itself must run on
   stock OpenJDK 21/25 without AprismJDK.
3. **LTS rebasing.** AprismJDK tracks upstream LTS lines (25, then the
   next LTS) and backports the AprismJDK patch set onto each, so the
   capability surface is stable across LTS jumps.

---

## 8. Subproject skeleton & governance

The `AprismLab/AprismJDK` repository ships (this alpha):

```
AprismJDK/
  README.md            - project pitch (existing, to be expanded)
  LICENSE              - GPL-2.0 (existing)
  docs/
    01-architecture.md - this design, canonical EN
    02-aprismate-agent.md
    03-opened-interfaces.md
    04-perf-hardware.md
    05-cross-language.md
    06-compatibility-matrix.md
  (JDK source tree lands when the OpenJDK fork build begins)
```

Governance follows the **Aprism main-project management & version-control
convention** (documented separately in `Aprism/docs/en/10-project-
management-and-version-control.md`, delivered in v26.4-Alpha.2):
signed commits/tags, per-alpha Pre-Releases, GA as a bare number,
bilingual docs, FACT.md session log.

---

## 9. Status & explicit non-goals (this alpha)

- [SCOPE] Design + docs + skeleton only. No OpenJDK source fork, no JDK
  build, no agent implementation yet. Those are later milestones.
- [NON-GOAL] Replacing stock OpenJDK for users who do not need Aprism's
  deep tier.
- [NON-GOAL] Changing Java language semantics or the JVM's standard
  class-file format.

## 10. References

- Amazon Corretto 25 (LTS) and Corretto 26 (FR) — distribution model and
  LTS/FR cadence (GA 2025-09-16 for 25; Corretto FAQ, 2026).
- Azul Zulu, Microsoft Build of OpenJDK, Red Hat OpenJDK — build+patch
  distribution pattern.
- JDK 22 Foreign Function & Memory API (final) — interop foundation.
- `java.lang.instrument.Instrumentation`, JVMTI — the stock low-level
  seams AprismJDK hardens.

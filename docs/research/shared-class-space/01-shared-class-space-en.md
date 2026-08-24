# Shared Class Space under Javaagent Topology — Feasibility Research

> Research deliverable for Aprism FACT.md 9.2 (Knot-style shared class
> space). Authored by the opencode agent (model: ox-alpha) during the
> AprismRefract v26.8 compatibility line, from live-game evidence.
> Canonical language: English.

## 1. Problem statement (live-game evidence)

Mixin-heavy optimization mods inject references to their OWN classes into
vanilla classes. Live case: Lithium mc26.2-0.25.3 (Fabric) weaves
`EntityPushableMixin`, causing the transformed `net.minecraft` class to
reference `net.caffeinemc.mods.lithium.common.entity.pushable.FeetBlockCachingEntity`.
Under the current topology the JVM dies in main during vanilla bootstrap:

```
Exception in thread "main" java.lang.NoClassDefFoundError:
  net/caffeinemc/mods/lithium/.../FeetBlockCachingEntity
  at jdk.internal.loader.BuiltinClassLoader.findClassOnClassPathOrNull
```

Root cause: vanilla classes are defined by the system (app) loader; mod
classes live on the child `AprismClassLoader`. A parent-defined class can
never resolve a child-only class.

Compatibility tiers observed across the v26.8 live line:

| Tier | Behaviour | Examples |
|---|---|---|
| 1 | constructs and plays | JEI, FerriteCore, examplemod |
| 2 | constructs; entrypoint awaits client-start events | mods needing FMLClientSetupEvent (CLIENT-phase dispatch gap, separate item) |
| 3 | requires shared class space | Lithium and any mixin that injects mod-class references into vanilla classes |

## 2. Candidate solutions

### (A) Child-first for game namespaces in AprismClassLoader — REJECTED (fundamental flaw)

The JVM entry class `net.minecraft.client.main.Main` must be defined by
the system loader (launcher contract; a javaagent cannot relocate it).
Its transitive closure (SharedConstants, Bootstrap, BuiltInRegistries,
...) is therefore system-defined. Child-first definition would create
SECOND copies of any game class a mod references but Main has not
touched - splitting registry identity (BuiltInRegistries defined twice is
catastrophic). The duplicate-population hazard is inherent to the
javaagent topology, not an implementation detail.

### (B) Instrumentation.appendToSystemClassLoaderSearch - RECOMMENDED (javaagent-compatible shared space)

Append mod jars to the SYSTEM loader via
`Instrumentation.appendToSystemClassLoaderSearch(JarFile)`. Mods and
vanilla then share ONE defining loader:

- vanilla -> mod references resolve (fixes Tier 3)
- mod -> vanilla references resolve
- the global `ClassFileTransformer` (AprismClassTransformer, registered
  JVM-wide via `inst.addTransformer`) keeps weaving - it already fires for
  system-loader definitions (that is how vanilla mixins work today)
- zero classloader-topology change; AprismClassLoader stays for legacy

Costs and mitigations:

| Cost | Mitigation |
|---|---|
| Irreversible (jars cannot be un-appended) | loses `URLClassLoader.close()` file-lock release; acceptable until hot-reload exists (none shipped; game exit releases locks) |
| BytecodeRemapper hooks `AprismClassLoader.findClass`; mod classes would now define via the system loader | For the PRIMARY modern target (NO_REMAP, MC 26.1+) no remap exists - works as-is. REMAPPED (pre-26.1) profile keeps the current AprismClassLoader path (flag-gated) |
| `AprismMixinService` routes class lookups through AprismClassLoader | extend lookup to also consult the system loader (small, contained change) |
| No per-mod isolation | aligned with 9.2's stated intent (shared space IS the design) |

### (C) Wrapper-launch mode - STRATEGIC ALTERNATIVE

mdl launches `net.aprism.bootstrap.Main`, which builds AprismClassLoader
as THE loader for everything including the game entry class (true Knot
parity, no duplicate population). Changes the product identity from
"javaagent" to "wrapper launcher"; touches the mdl `--aprism` contract.
Defer until (B) proves insufficient.

### (D) AJR built-in loader - LONG-TERM HOME

AprismJDK's runtime can integrate the loader natively. Tracks the AJR
line; out of scope here.

## 3. Recommendation

Implement (B) behind `aprism.sharedClassSpace=system` (default off;
current topology remains the fallback), NO_REMAP profiles only for the
first cut. Sequence:

1. `appendToSystemClassLoaderSearch` for mod jars in
   `AprismRuntime.loadMods` (flag-gated)
2. AprismMixinService lookup fallback to system loader
3. LoadReport / mod-list unchanged (discovery already separate)
4. Live E2E: Lithium construct + world join (Tier 3 acceptance)
5. REMAPPED-profile support decision after modern-line proof

## 4. Evidence base

- Live E2E sessions 2026-08-24/25 (refract-e268 instance, MC 26.2,
  JRE 25): Tier 1/2/3 behaviour table above; full stack trace archived
  in AprismRefract FACT.md session log.
- JEI 30.25.0.177 constructs under Aprism (v26.8 + GameBootstrapGate);
  world entry driven via Despotes raw protocol; 14-minute in-world run.

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

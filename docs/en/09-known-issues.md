# Aprism Loader v26.6 — Known Issues

> Companion to the v26.6 release. Maintained by
> BlockConnect@StarsailsClover. Items are ordered by theme, not severity;
> they ship knowingly with the v26.6 GA. Items closed during v26.3-v26.6
> are marked [CLOSED] and kept for historical traceability.

## JE Loader Core

1. **[CLOSED in v26.5-Alpha.3] No game-event dispatch.** The v26.3-Alpha.1
   game-event dispatcher was passive; v26.5-Alpha.3 added
   `GameEventHookInstaller` that bridges MC tick/render/world methods to
   `GameEventDispatcher` through `MethodHookRegistry`. Hook targets are
   supplied by a platform adapter layer (the core never hardcodes MC class
   names); without a platform adapter the dispatcher remains fail-closed.
2. **[CLOSED in v26.3-Alpha.2] Registry is generic-only.** `AprismRegistry` and the `registry/`
   subpackage expose a generic registry API. Typed Block/Item/Entity
   registries bound to real Minecraft game registries do not exist yet.
3. **[CLOSED in v26.3-Alpha.3] No networking API.** Neither JE ecosystem's packet API has an Aprism
   equivalent yet. Mods needing custom packets must use Mixin.
4. **[CLOSED in v26.5-Alpha.1] Entrypoint discovery is manifest-driven only.**
   Annotation-scan fallback added: when the manifest declares no `main`
   entrypoints, the loader scans embedded jars for `@AprismMod` classes.

## Loader Support (goal #4, closed in v26.2-Alpha.5)

5. **Foreign dispatch is SPI-only.** Since v26.2-Alpha.5 the core ships no
   built-in foreign-loader bridges. A foreign-loader mod folder is scanned
   only when its `.aep` loader-support extension is installed, and its mods
   are dispatched only when the extension's `LoaderEntrypointHandler` is
   registered. A foreign mod with no registered handler is discovered but
   never invoked.
6. **[CLOSED in v26.5-Alpha.2] Extension dependency ranges are presence-checked only.**
   `depends` between extensions now performs full SemVer range matching
   against the dependency extension's `version` field; presence-only
   checking remains as the backwards-compatible fallback for manifests
   without versions.

## Game-Side Driving (v26.5 installers)

18. **v26.5 installers require a platform adapter to drive the real game.**
    The v26.5 line shipped `GameEventHookInstaller`, `CommandBindingInstaller`,
    `KeyBindingBindingInstaller`, `TickSchedulerDriver`,
    `ResourceReloadTrigger`, and `NetworkTransportInstaller`. Each follows
    the same contract: the core supplies the registry/facade wiring and a
    bridge interface (`CommandDispatcherBridge`, `InputSystemBridge`,
    `NetworkTransport`, hook-target descriptors); an AprismRefract branch or
    platform adapter supplies the MC-version-specific binding. Without the
    adapter, every surface stays registration-only and fail-closed. The
    adapters themselves ship with the respective Refract lines.

## Deep API Line (added in v26.4)

16. **Deep capabilities are contract + registry only on stock JVMs.** The
    v26.4 deep API (bytecode hooks, JVM introspection, native interop,
    AprismateAgent detection, hardware insight, cross-language runtime)
    ships as proven-capability contracts. The hardware-backed and
    FFM-backed implementations live in the AprismJDK line; on stock JVMs
    every deep operation degrades gracefully (proven values only,
    fail-closed refusals, never throws into the game).
17. **AprismJDK itself is still in its own early line.** The AprismJDK
    subproject (OpenJDK variant + AprismateAgent) tracks its own version
    line and is not yet a downloadable JDK; Aprism runs fully on stock
    OpenJDK in the meantime.

## Bedrock Edition

7. **Java-side only so far.** The complete Java-side BE foundation shipped
   in v26.1 (version DB, injection plan, coordinator, native staging,
   version adapter). The native platform injectors (Windows proxy-DLL,
   Android Zygisk/container, iOS) are not implemented; see the BE approach
   research report (`docs/research/bedrock-approach/`). BE work remains
   suspended until the September reverse-engineering milestone.
8. **No real-game BE smoke.** This machine has no Minecraft Bedrock
   installation, so BE behaviour is verified by unit/integration tests
   only.
9. **Ban risk.** Native BE modification on Xbox Live-enabled worlds carries
   a real ban risk. Use offline worlds. Disclosed in Doc 01 §13.2.

## Real-Game Verification

10. **Harness coverage is JE 26.2 only.** The MDL-driven real-game smoke
    runs against Minecraft 26.2 with JDK 25. The 26.1.2 and legacy
    (1.16.5 / 1.21.x) lines are covered by the version-line registry
    profiles and unit tests, without live-game runs.
11. **MDL has no `-javaagent` config key.** The harness launches the game
    directly with the agent; MDL is used for instance/log/game control. A
    deeper MDL integration is planned.

## Distribution

12. **Standalone download only.** Aprism is not on Microsoft Store or Apple
    App Store (both prohibit dynamic code injection in store apps).
13. **[CLOSED in v26.6-Alpha.3] Modrinth mirror not yet published.** The
    release workflow now mirrors every signed artifact to Modrinth (gated on
    MODRINTH_TOKEN); GitHub Releases remains the primary,
    verification-authoritative channel.

## v26.7 Content Binding

19. **Block binding requires a provider seam.** Items bind fully (11/11 in
    the Alpha.8 soak); blocks fail because modern MC's
    BlockBehaviour.Properties factory needs a HolderLookup provider that the
    binder does not yet supply. Refinement scheduled for the next line.
20. **Pre-26.1 profiles are documented-limitation (DEC-PRE261).** Binding
    requires NO_REMAP; cross-mapping is a v26.8 candidate workstream.
    BlockBehaviour.Properties factory needs a HolderLookup provider that the
    binder does not yet supply. Refinement scheduled for the next line.
20. **Pre-26.1 profiles are documented-limitation (DEC-PRE261).** Binding
    requires NO_REMAP; cross-mapping is a v26.8 candidate workstream.

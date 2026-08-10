# Aprism Loader v26.4 — Known Issues

> Companion to the v26.3 official release. Maintained by
> BlockConnect@StarsailsClover. Items are ordered by theme, not severity;
> they ship knowingly with the v26.4 GA. Items closed during v26.3 are
> marked [CLOSED] and kept for historical traceability.

## JE Loader Core

1. **[CLOSED in v26.3-Alpha.1] No game-event dispatch.** The `CLIENT` and `SERVER` lifecycle phases are
   declared and dispatchable, but there is no real-game event hook yet: the
   phases fire only when the launcher explicitly supplies the distribution
   side (`side=client|server` agent argument). Hooking real game events
   (tick, render, network) is a later milestone.
2. **[CLOSED in v26.3-Alpha.2] Registry is generic-only.** `AprismRegistry` and the `registry/`
   subpackage expose a generic registry API. Typed Block/Item/Entity
   registries bound to real Minecraft game registries do not exist yet.
3. **[CLOSED in v26.3-Alpha.3] No networking API.** Neither JE ecosystem's packet API has an Aprism
   equivalent yet. Mods needing custom packets must use Mixin.
4. **Entrypoint discovery is manifest-driven only.** There is no
   annotation-scan fallback for Forge-style `@Mod` discovery on the Aprism
   native path; Forge/NeoForge mods run through their loader-support
   extensions, which scan the `@Mod` annotation themselves.

## Loader Support (goal #4, closed in v26.2-Alpha.5)

5. **Foreign dispatch is SPI-only.** Since v26.2-Alpha.5 the core ships no
   built-in foreign-loader bridges. A foreign-loader mod folder is scanned
   only when its `.aep` loader-support extension is installed, and its mods
   are dispatched only when the extension's `LoaderEntrypointHandler` is
   registered. A foreign mod with no registered handler is discovered but
   never invoked.
6. **Extension dependency ranges are presence-checked only.** `depends`
   between extensions validates that the referenced id/capability exists;
   SemVer range matching of dependency versions is deferred.

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

## Loader-Ecosystem Parity (added in v26.3)

14. **Parity surfaces are registration contracts.** v26.3 closed the
    Forge/NeoForge EventPriority + InterModComms parity and the Fabric
    command/key-binding/tick-scheduler/resource-reload parity at the
    loader API level. The actual game-side dispatch (command-dispatcher
    binding, input-system mapping, tick-loop driving, resource-reload
    invocation) is supplied by the injector/native layer when it hooks the
    real game; until then these surfaces are registration-only.
15. **No annotation-scan entrypoint discovery** remains unchanged (see
    item 4).

## Bedrock Edition

7. **Java-side only so far.** The complete Java-side BE foundation shipped
   in v26.1 (version DB, injection plan, coordinator, native staging,
   version adapter). The native platform injectors (Windows proxy-DLL,
   Android Zygisk/container, iOS) are not implemented; see the BE approach
   research report (`docs/research/bedrock-approach/`).
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
13. **Modrinth mirror not yet published.** GitHub Releases is the only
    artifact channel at v26.2 GA.

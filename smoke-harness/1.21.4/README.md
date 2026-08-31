<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

# Aprism 1.21.4 Smoke Harness (v26.8-Alpha.9)

Live-game proof that Aprism v26.8-Alpha.9 binds Aprism-native content (an item
and a block) into a genuine obfuscated Mojang Minecraft 1.21.4 client. The
launch profile is REMAPPED under DEC-PRE261 Option A: the loader translates
Aprism's deobfuscated-world API calls through Fabric intermediary (tiny v2)
plus Mojang official mappings at runtime, then binds mod content into the
live item/block registries.

## Prerequisites

- JDK 25 at `C:\Users\Sails\Java\jdk-25.0.3+9` (override with the script's
  `-JavaHome` parameter).
- Built agent jar: `aprism-loader-core\build\libs\Aprism-v26.8-Alpha.9-JE-26.2.jar`.
- A pre-assembled runtime environment at `build\smoke\remap\` (build output,
  NOT committed). It must contain:
  - `client-1.21.4.jar` — genuine obfuscated Mojang 1.21.4 client.
  - `1.21.4.json` — vanilla version manifest for 1.21.4.
  - `1.21.4.tiny` — Fabric Intermediary mappings, tiny v2 (8841 classes).
  - `1.21.4-client.txt` — Mojang official mappings (8857 classes). Line
    format is official -> obfuscated, REVERSED vs the standard ProGuard
    direction.
  - `intermediary-1.21.4-v2.jar` — Fabric intermediary mapping artifact;
    mapping data only, contributes no classes to the runtime classpath.
  - `assets\` — indexes `19.json` (+ `32.json`) and `objects\` blobs.
  - `natives\` — LWJGL / OpenAL / glfw Windows DLLs.
  - Library jars in maven-layout dirs: `com\mojang\...`, `org\lwjgl\...`,
    `io\netty\...`, `org\ow2\asm\asm\9.6\asm-9.6.jar`, etc.
  - `gamedir-1214\` — game dir containing `mods\realsmoke.aje`.

## Environment bootstrap (from scratch)

1. Version JSON: fetch
   `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`, locate
   the `1.21.4` entry, download its URL, save as `1.21.4.json`.
2. Client jar: download `downloads.client.url` from that JSON and save as
   `client-1.21.4.jar`.
3. Official mappings: download `downloads.client_mappings.url` from the
   version JSON and save as `1.21.4-client.txt`.
4. Intermediary: download
   `https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.4/intermediary-1.21.4-v2.jar`,
   keep it as `intermediary-1.21.4-v2.jar`, and place its tiny v2 mapping
   data at `1.21.4.tiny`.
5. Libraries: for every entry in the JSON `libraries` list, download
   `downloads.artifact.url` and store it at `downloads.artifact.path`
   (maven layout, e.g. `com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar`).
6. Assets: download the asset index referenced by `assetIndex` (id `19`)
   into `assets\indexes\19.json`, then download each object into
   `assets\objects\<first two hex chars of hash>\<hash>`. Asset download is
   the slowest bootstrap step.
7. Natives: extract the DLLs from every `natives-windows` classifier jar
   into `natives\`.

## Build the smoke mod

`WriteRealAje.java` (tracked next to this README) generates `realsmoke.aje`
via ASM bytecode generation: it registers `aprism:realsmoke_item` and
`aprism:realsmoke_block` through the Aprism API, packaged as an outer zip
(`aprism.manifest.json` + nested `realsmoke.jar` with
`com/mod/RealSmokeMod.class`). Compile it against the ASM 9.6 jar found in
the env and run it with the output path. From `build\smoke\remap`:

```powershell
& C:\Users\Sails\Java\jdk-25.0.3+9\bin\javac.exe -cp org\ow2\asm\asm\9.6\asm-9.6.jar -d . ..\..\..\smoke-harness\1.21.4\WriteRealAje.java
& C:\Users\Sails\Java\jdk-25.0.3+9\bin\java.exe -cp ".;org\ow2\asm\asm\9.6\asm-9.6.jar" WriteRealAje gamedir-1214\mods\realsmoke.aje
```

## Run

From the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File smoke-harness\1.21.4\run-1214.ps1 -PollSeconds 200
```

The tracked script is normally copied into `build\smoke\remap\` and run from
there: it rebuilds the classpath from every `*.jar` under its own directory
(plus `client-1.21.4.jar`, excluding `gamedir*` trees), writes a Java
`@argfile` (`java-args.txt`) so no token can be split by shell re-quoting,
launches `net.minecraft.client.main.Main` with `-javaagent` pointing at the
Alpha.9 agent jar and args `aprismVersion`/`mcEdit=JE`/`mcVersion=1.21.4`/
`gameRoot`/`mappings`/`officialMappings`, polls `run-a9-err.log` for binding
evidence, then force-stops the process.

## Expected evidence

`run-a9-err.log` must contain exactly these lines:

```text
[gate] vanilla bootstrap detected - dispatching deferred mod lifecycle
[RealSmokeMod] onInitialize reached (remap path active); item+block registered
Registry readback: 'aprism:realsmoke_item' present in the live item registry
Registry readback: 'aprism:realsmoke_block' present in the live block registry
Content binding: 2/2 unit(s) bound to the live registries
```

## Known limitations

From the FACT.md v26.8-Alpha.9 sessions:

- Name-only mapping translation cannot separate same-name overloads without
  param hints; descriptor-aware matching is a named follow-up.
- `stacksTo(int)` is skipped because the probe is no-arg, so maxStack is
  historically fixed at 64 in the live smoke pending an arg-aware stacksTo
  probe.
- The harness assumes the pre-assembled `build\smoke\remap` environment;
  there is no automated bootstrap script (this document describes the manual
  assembly).

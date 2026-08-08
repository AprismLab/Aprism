# Aprism Real-Game Smoke Harness

Automated, reproducible verification that the Aprism javaagent loads a genuine
Minecraft instance and runs an Aprism-native mod through the full lifecycle.
This is the deliverable of **v26.0-Alpha.8** (see FACT.md section 8b).

## What it proves

- The Aprism fat agent jar is attached to a **real** Minecraft 26.2 process.
- A real `.aje` mod is discovered, extracted, and its `IAprismMod` entrypoint
  runs through `PREINIT -> INIT -> SETUP -> COMPLETE` inside the live game.
- The Aprism Load Report banner is emitted with zero failures.

This closes the loop that Alpha 1-7 unit/integration tests could only simulate:
it exercises the actual `javaagent` + real classpath + real Minecraft classes.

## Files

| File | Purpose |
|---|---|
| `run_smoke.sh` | Launches the game, waits for `ExampleMod onComplete`, asserts lifecycle, kills the game. |
| `setup_smoke_env.sh` | Downloads MC 26.2 client jar + libraries + natives + assets into `build/smoke`, scaffolds the game dir. Idempotent. |
| `README.md` | This document. |

## Usage (Windows, git-bash / MSYS)

```bash
# 1. Prepare the game environment once (downloads MC 26.2, ~1-2 GB):
bash tools/smoke/setup_smoke_env.sh

# 2. Build the fat agent jar for the current version:
./gradlew :aprism-loader-core:shadowJar

# 3. Ensure a sample .aje mod exists in build/smoke/gamedir/mods/.
#    (examplemod-1.0.0.aje from the examplemod packaging task works.)

# 4. Run the smoke test. Requires a JDK 25 (MC 26.2 baseline).
APRISM_JAVA_HOME="C:/path/to/jdk-25" bash tools/smoke/run_smoke.sh
```

`run_smoke.sh` exits 0 on `SMOKE PASS` and non-zero on any failure, printing the
last 40 log lines. The version is read from `gradle.properties` so the harness
always tests the version currently being built.

## Notes and constraints

- MDL (MCDebugLauncher) exposes instance/log/game control but has **no
  `-javaagent` config key**, so the harness launches the game directly with the
  agent and does not go through MDL. MDL remains useful for log inspection and
  game control; a deeper MDL integration is planned for v26.1-Alpha.1.
- The harness kills the game after asserting; it does not leave a window open.
- Offline: the `--accessToken 0` boot logs an auth 401, which is expected and
  ignored. The lifecycle assertions run before auth matters.
- The classpath is reused from a previously captured `build/smoke/launch_args.txt`
  (the proven 55-entry set). `setup_smoke_env.sh` records libraries under
  `build/smoke/libs`; if you rebuild the environment from scratch, re-capture the
  classpath once from a manual launch and it will be reused thereafter.

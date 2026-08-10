# Aprism Project Management & Version-Control Conventions (Doc 10)

> Companion to every Aprism-family repository (`AprismLab/Aprism`,
> `AprismLab/AprismRefract`, `AprismLab/AprismJDK`,
> `AprismLab/AprismPrismate`, `AprismLab/AprismTest`). Maintained by
> BlockConnect@StarsailsClover. Delivered with v26.4-Alpha.2. This
> document codifies the conventions the project actually enforces; it is
> a contract, not a suggestion.

---

## 1. Versioning scheme

Version identifiers follow `v<MAJOR>.<MINOR>[-Alpha.<N>]`:

- **MAJOR** tracks the calendar year of the 20xx line (`v26` = 2026).
- **MINOR** increments per feature line (`v26.1`, `v26.2`, ...).
- Each minor line ships up to **nine Alphas** (`v26.X-Alpha.1` ...
  `v26.X-Alpha.9`), each an individually released and verified
  Pre-Release.
- The **bare number** (`v26.X`, no suffix) is the official General
  Availability release of that minor line. It freezes the Alpha work and
  is published as a non-prerelease GitHub Release.
- Example line: `v26.3-Alpha.1 ... v26.3-Alpha.9`, then `v26.3` GA.

Rules:

1. Never reuse an Alpha number within a minor line.
2. Never skip the bare-number GA: every minor line that ships Alphas must
   end in a GA.
3. Build coordinates (`gradle.properties aprismVersion`,
   `gradle/libs.versions.toml aprism`, `README.md Version:`) must all
   carry the same version string, bumped in the same commit that
   introduces the change set.

## 2. Branch strategy

The Aprism family is **trunk-based, main-only** for the main repository:

- All work lands on `main`; there are no long-lived feature branches and
  no merge commits from feature branches.
- The exception is **AprismRefract**, which uses one long-lived branch
  per external loader (`fabric`, `forge`, `neoforge`, `quilt`,
  `liteloader`) plus `main` for shared docs. Each branch is an
  independent loader-support `.aep` provider; branches never merge into
  each other.
- Hotfixes (if ever needed) are normal commits on `main` followed by a
  patch tag, not backport branches.

## 3. Commits and tags

Every commit and every release tag is **signed**:

- Commit signing: `git commit -S` with the project SSH ED25519 signing
  key (verified via `git log --show-signature`).
- Tag signing: `git tag -s` (signed annotated tags); the tag message
  carries a one-line summary of the release content.
- Commit message format: `<type>(<scope>): v<version> - <summary>`,
  e.g. `feat(imc): v26.3-Alpha.7 - InterModComms for Forge/NeoForge
  parity (...)`. Types in use: `feat`, `fix`, `refactor`, `test`,
  `docs`, `release`, `build`.
- A version bump and its feature changes ship in **one** commit; the tag
  points at that commit.

## 4. Release pipeline

Releases are produced exclusively by the GitHub Actions workflow
`.github/workflows/release.yml`, triggered by pushing a version tag:

1. Build with JDK 21 (`./gradlew build package`), producing the fat
   agent jar named `Aprism-<version>-JE-26.2.jar`.
2. Generate `checksums.txt` (SHA-256 of every artifact).
3. Sign every artifact with **cosign keyless** (GitHub OIDC identity),
   producing `.sig` + `.bundle` per artifact.
4. Publish the SBOM (`Aprism-sbom.cdx.json`, CycloneDX).
5. Create the GitHub Release: **Pre-Release** for `-Alpha.N` tags,
   normal release for bare-number tags. The release body is read from
   `docs/release-notes/<version>.md` when present (enriched notes),
   falling back to a generated template otherwise.

Verification contract for every release (maintainer and CI consumers):

- `sha256sum -c checksums.txt` must pass (mind CRLF: strip `\r` first on
  Windows-authored files).
- `cosign verify-blob <artifact> --signature <artifact>.sig --bundle
  <artifact>.bundle --certificate-identity-regexp "AprismLab/Aprism"
  --certificate-oidc-issuer https://token.actions.githubusercontent.com`
  must report `Verified OK`.
- The repository moved from `NDBlockConnect/Aprism` to `AprismLab/Aprism`
  during v26.3; certificate identities are the AprismLab ones, and the
  old remote URL keeps working via redirect.

## 5. Testing rules

Testing is a release gate, not a courtesy:

1. **Full suite before every release** — `./gradlew test --rerun-tasks`
   (cache is not trusted) must report 0 failures before the commit is
   signed and pushed.
2. **Test count is tracked** in FACT.md per alpha (e.g. v26.3: 430 ->
   496). A release that shrinks the suite must justify it in the session
   log.
3. **Real-game verification** — the MDL-driven smoke harness
   (`tools/smoke/run_smoke.sh`) launches real Minecraft 26.2 with the
   agent and must report `SMOKE PASS` at least once per minor line
   (done for v26.2; repeated before each GA).
4. Refract loader-support branches run their own suites; the core's
   cross-repo E2E tests consume the branch-built `.aep` artifacts.

## 6. Documentation rules

1. **Bilingual, EN canonical.** Every substantive document exists in
   `docs/en/` (canonical) and `docs/zh/` (mirror, same numbering). A
   change to one requires a matching change to the other in the same
   release.
2. **Docs ship with the code that changes them.** A feature alpha that
   adds an API surface must extend Doc 01 (architecture) in the same
   commit.
3. **Doc 09 (Known Issues)** is refreshed at every GA: closed items are
   marked `[CLOSED in vX.Y-Alpha.N]` and kept for traceability; new
   caveats are added before the GA commit.
4. **Enriched release notes** live in `docs/release-notes/<version>.md`
   for every GA (and optionally for notable alphas).
5. No emoji in any artifact, document, or release note.

## 7. FACT.md session log

`FACT.md` (repository root, intentionally **not** committed — it is the
local working journal) records, per session:

- what was done (`[DONE]`), decisions taken (`[DECISION]`), notes
  (`[NOTE]`), bugs found and fixed (`[BUG FIXED]`);
- the test-suite delta;
- the roadmap sections that govern the next sessions.

It is the authoritative source for "why" behind any commit; git history
is the authoritative source for "what".

## 8. Subproject governance

| Repository | Role | Version coupling |
|---|---|---|
| `AprismLab/Aprism` | Main loader/injector; owns the version line `v26.X` | — |
| `AprismLab/AprismRefract` | External loader-support `.aep` providers, one branch per loader | Builds against the current Aprism jar (`align` version); released independently |
| `AprismLab/AprismJDK` | OpenJDK variant (AJR) + AprismateAgent design/build | Own version line; design tracked from Aprism v26.4 |
| `AprismLab/AprismPrismate` | Rendering/visual companion | Own version line |
| `AprismLab/AprismTest` | Cross-repo test fixtures | Follows main repo |

Rules:

1. The main repository never depends on a subproject at build time;
   integration flows through artifacts (`.aep` files, jars) and
   cross-repo E2E tests.
2. Each subproject keeps its own README + LICENSE and follows the same
   signing/release conventions where it has releases.
3. Suspending work on a subproject (e.g. Aprism BE until the September
   reverse-engineering milestone) is recorded as a `[DECISION]` in
   FACT.md with a resume condition — suspended work is frozen, never
   deleted.

## 9. Security & disclosure

1. **Fail-safe by contract.** Loader and agent code must never crash the
   host game or the JVM; failures are logged and isolated (see Doc 06
   principle specification).
2. **Native-modification disclosure.** BE native modification carries
   ban risk on live services; Doc 01 §13.2 and Doc 09 carry the
   disclosure.
3. No secrets in the repository; CI uses GitHub OIDC for signing, no
   long-lived keys.

## 10. Change control for this document

This document changes only with a deliberate commit of type `docs`
scoped `conventions`, and both language copies must change together.
Retroactive rewrites of recorded decisions are not allowed; corrections
are appended as new `[NOTE]` entries.

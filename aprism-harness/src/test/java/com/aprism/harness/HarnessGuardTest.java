package com.aprism.harness;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Harness guard tests (v26.9-Alpha.8). The pairing and artifact tests encode
 * the two Despotes-reported defect classes as executable regressions.
 *
 * @author BlockConnect@StarsailsClover
 */
class HarnessGuardTest {

    @TempDir
    Path tempDir;

    private static final String AGENT_ENTRY = "com/aprism/loader/AprismAgent.class";

    /**
     * Incompressible bytes so the fake jars exceed the guard's sanity size
     * threshold (a jar of repeated zero bytes compresses to almost nothing).
     */
    private static byte[] incompressible(int size) {
        byte[] bytes = new byte[size];
        new java.util.Random(42).nextBytes(bytes);
        return bytes;
    }

    /** Builds a fake agent jar with the given entries and version. */
    private Path fakeAgent(String name, String version, List<String> extraEntries)
            throws IOException {
        Path jar = tempDir.resolve(name);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        if (version != null) {
            manifest.getMainAttributes().putValue("Implementation-Version", version);
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar),
                manifest)) {
            for (String entry : extraEntries) {
                out.putNextEntry(new JarEntry(entry));
                if (!entry.endsWith("/")) {
                    out.write(incompressible(4096));
                }
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry(AGENT_ENTRY));
            out.write(incompressible(4096));
            out.closeEntry();
        }
        return jar;
    }

    @Test
    void fabricHostRequiresRelocatedAsmLayout() {
        // The exact Despotes-reported condition: the native-layout jar paired
        // with a Fabric host must be refused.
        List<String> nativeLayout = List.of("org/objectweb/asm/ClassReader.class",
                "org/spongepowered/asm/mixin/Mixin.class");
        List<String> problems = HostVariantSelector.verifyPairing(
                HostVariantSelector.Host.FABRIC,
                "Aprism-v26.8-JE-26.2.jar", nativeLayout);
        assertFalse(problems.isEmpty(), "native jar on Fabric host must be refused");
        assertTrue(problems.get(0).contains("FABRIC_HOST"), problems.toString());

        // The fabric-host artifact satisfies the pairing.
        List<String> fabricLayout = List.of("aprism/libs/asm/ClassReader.class");
        assertTrue(HostVariantSelector.verifyPairing(
                HostVariantSelector.Host.FABRIC,
                "Aprism-v26.9-Alpha.8-JE-26.2-fabric-host.jar", fabricLayout).isEmpty());

        // Its own guard still fires if the relocation silently regressed.
        List<String> regressed = List.of("aprism/libs/asm/ClassReader.class",
                "org/objectweb/asm/ClassReader.class");
        List<String> regression = HostVariantSelector.verifyPairing(
                HostVariantSelector.Host.FABRIC,
                "Aprism-v26.9-Alpha.8-JE-26.2-fabric-host.jar", regressed);
        assertTrue(regression.stream().anyMatch(p -> p.contains("verifyClasspath")),
                regression.toString());
    }

    @Test
    void nativeHostRequiresAsmAndMixin() {
        assertTrue(HostVariantSelector.verifyPairing(
                HostVariantSelector.Host.NATIVE, "Aprism-v26.9-JE-26.2.jar",
                List.of("org/objectweb/asm/ClassReader.class",
                        "org/spongepowered/asm/mixin/Mixin.class")).isEmpty());
        List<String> missing = HostVariantSelector.verifyPairing(
                HostVariantSelector.Host.NATIVE, "Aprism-v26.9-JE-26.2.jar",
                List.of("com/aprism/loader/AprismAgent.class"));
        assertEquals(2, missing.size(), missing.toString());
        assertEquals(HostVariantSelector.Host.FORGE_LIKE,
                HostVariantSelector.parseHost("neoforge"));
        assertThrows(IllegalArgumentException.class,
                () -> HostVariantSelector.parseHost("bukkit"));
    }

    @Test
    void staleAndTruncatedArtifactsAreRefused() throws IOException {
        Path good = fakeAgent("Aprism-v26.9-Alpha.8-JE-26.2.jar",
                "v26.9-Alpha.8", List.of("org/objectweb/asm/ClassReader.class"));
        AgentArtifactGuard.Artifact verified =
                AgentArtifactGuard.verify(good, "v26.9-Alpha.8");
        assertEquals("v26.9-Alpha.8", verified.version());
        assertEquals(64, verified.sha256().length());

        // Stale version: the run under test expects a different build.
        IOException stale = assertThrows(IOException.class,
                () -> AgentArtifactGuard.verify(good, "v26.9-Alpha.9"));
        assertTrue(stale.getMessage().contains("stale artifact"), stale.getMessage());

        // Truncated jar: the "zip END header not found" incident.
        Path truncated = tempDir.resolve("Aprism-truncated.jar");
        Files.write(truncated, "PK\u0003\u0004not-a-real-jar".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class,
                () -> AgentArtifactGuard.verify(truncated, null));

        // A .tmp leftover must never be treated as an artifact.
        Path tmp = tempDir.resolve("Aprism-v26.9-Alpha.8-JE-26.2.jar.tmp");
        Files.copy(good, tmp);
        IOException refusedTmp = assertThrows(IOException.class,
                () -> AgentArtifactGuard.verify(tmp, null));
        assertTrue(refusedTmp.getMessage().contains(".tmp"), refusedTmp.getMessage());

        assertEquals(1, AgentArtifactGuard.findLeftovers(tempDir).size());
        Files.delete(tmp);
        assertTrue(AgentArtifactGuard.findLeftovers(tempDir).isEmpty());
    }

    @Test
    void ajeFloorAndStructureAreVerified() throws IOException {
        Path aje = tempDir.resolve("despotes-1.0.0.aje");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(aje),
                manifest)) {
            out.putNextEntry(new JarEntry("aprism.manifest.json"));
            out.write(("""
                    {"schemaVersion":1,"id":"despotes","version":"1.0.0",
                     "entrypoints":{"main":["com.example.DespotesMod"]},
                     "depends":{"aprism":">=26.0-Alpha.1"}}
                    """).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("despotes.jar"));
            out.write(incompressible(4096));
            out.closeEntry();
        }

        AjeManifestContract.ManifestFacts facts = AjeManifestContract.read(aje);
        assertEquals("despotes", facts.modId());
        assertEquals(">=26.0-Alpha.1", facts.aprismFloor());
        assertEquals("com.example.DespotesMod", facts.entrypoint());
        assertTrue(AjeManifestContract.verifyStructure(aje, facts).isEmpty());

        // The Despotes-reported defect: floor below the compiled baseline.
        List<String> problems = AjeManifestContract.verifyFloor(facts, "v26.8");
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("below the compiled baseline"),
                problems.get(0));

        // The corrected manifest is accepted.
        AjeManifestContract.ManifestFacts fixed = new AjeManifestContract.ManifestFacts(
                "despotes", "1.0.0", ">=26.8", "com.example.DespotesMod",
                facts.entries());
        assertTrue(AjeManifestContract.verifyFloor(fixed, "v26.8").isEmpty());
        assertTrue(AjeManifestContract.verifyFloor(fixed, "v26.8-alpha.3").isEmpty());
        assertFalse(AjeManifestContract.verifyFloor(
                new AjeManifestContract.ManifestFacts("m", "1", null, "e",
                        List.of("m.jar")), "v26.8").isEmpty());
    }

    @Test
    void structureRejectsEmbeddedLoader() throws IOException {
        Path aje = tempDir.resolve("bad-1.0.0.aje");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(aje),
                manifest)) {
            out.putNextEntry(new JarEntry("aprism.manifest.json"));
            out.write("""
                    {"id":"bad","version":"1.0.0","entrypoints":{"main":["X"]}}
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry(AGENT_ENTRY));
            out.write(incompressible(4096));
            out.closeEntry();
        }
        AjeManifestContract.ManifestFacts facts = AjeManifestContract.read(aje);
        List<String> problems = AjeManifestContract.verifyStructure(aje, facts);
        // The archive embeds no mod jar AND embeds the loader: both are
        // contract violations and both must be reported.
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("does not embed")),
                problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("must not ship the loader")),
                problems.toString());
    }

    @Test
    void reportJsonIsFailClosed() {
        HarnessReport report = new HarnessReport();
        assertFalse(report.passed(), "an empty report is not a pass");
        report.record("a", true, "ok");
        assertTrue(report.passed());
        report.record("b", false, "broken \"quoted\"");
        assertFalse(report.passed());
        String json = report.toJson();
        assertTrue(json.contains("\"passed\":false"));
        assertTrue(json.contains("\\\"quoted\\\""));
        assertTrue(report.toText().contains("[FAIL] b"));
    }
}

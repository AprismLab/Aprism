package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.testmods.RecordingMod;

/**
 * Tests for the agent entry point: argument parsing, the production bootstrap
 * triggered from {@code premain}, and fault tolerance (a broken boot must not
 * throw out of the agent and must leave a crash report behind).
 *
 * <p>The {@link Instrumentation} handle is faked with a dynamic proxy because
 * the production path only requires {@code addTransformer} to accept the
 * transformer registration.
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismAgentTest {

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        RecordingMod.resetGlobal();
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
        System.clearProperty("aprism.agent.active");
    }

    private static Instrumentation fakeInstrumentation() {
        InvocationHandler handler = (proxy, method, args) -> {
            Class<?> rt = method.getReturnType();
            if (rt == boolean.class) {
                return false;
            }
            if (rt == int.class) {
                return 0;
            }
            if (rt == long.class) {
                return 0L;
            }
            return null;
        };
        return (Instrumentation) Proxy.newProxyInstance(
                AprismAgentTest.class.getClassLoader(),
                new Class<?>[] { Instrumentation.class },
                handler);
    }

    @Test
    void parseArgsNullReturnsEmptyMap() {
        assertThat(AprismAgent.parseArgs(null)).isEmpty();
        assertThat(AprismAgent.parseArgs("")).isEmpty();
        assertThat(AprismAgent.parseArgs("   ")).isEmpty();
    }

    @Test
    void parseArgsReadsKeyValuePairs() {
        Map<String, String> kv = AprismAgent.parseArgs(
                "aprismVersion=v26.0-Alpha.1;mcEdit=JE;mcVersion=26.2;gameRoot=/tmp/game");
        assertThat(kv)
                .containsEntry("aprismVersion", "v26.0-Alpha.1")
                .containsEntry("mcEdit", "JE")
                .containsEntry("mcVersion", "26.2")
                .containsEntry("gameRoot", "/tmp/game");
    }

    @Test
    void parseArgsSkipsMalformedPairs() {
        // Pairs without '=' (or with '=' at position 0) are skipped;
        // empty values are kept as empty strings.
        Map<String, String> kv = AprismAgent.parseArgs("novalue;=value;ok=1");
        assertThat(kv).containsExactly(Map.entry("ok", "1"));
    }

    @Test
    void parseArgsReadsOfficialMappingsPath() {
        assertThat(AprismAgent.parseArgs(
                "officialMappings=C:/mappings/client.txt"))
                .containsEntry("officialMappings", "C:/mappings/client.txt");
    }

    @Test
    void premainWithGameRootLoadsModsAndInvokesLifecycle() throws Exception {
        writeAje(gameRoot.resolve("mods").resolve("agenttest.aje"), "agenttest", "1.0.0",
                "com.aprism.loader.testmods.RecordingMod");

        AprismAgent.premain(
                "aprismVersion=v26.0-Alpha.1;mcEdit=JE;mcVersion=26.2;gameRoot="
                        + gameRoot.toString().replace('\\', '/'),
                fakeInstrumentation());

        assertThat(AprismRuntime.instance().getMod("agenttest"))
                .as("Mod should be discovered via the production bootstrap")
                .isNotNull();
        assertThat(RecordingMod.getGlobalPhases())
                .as("The common lifecycle should be dispatched in order")
                .containsExactly(
                        "PREINIT:agenttest",
                        "INIT:agenttest",
                        "SETUP:agenttest",
                        "COMPLETE:agenttest");
    }

    @Test
    void premainWithoutGameRootDoesNotTriggerProductionLoad() {
        AprismAgent.premain("aprismVersion=v26.0-Alpha.1;mcEdit=JE;mcVersion=26.2",
                fakeInstrumentation());

        assertThat(AprismRuntime.instance().getMods())
                .as("No gameRoot means no production load")
                .isEmpty();
    }

    @Test
    void premainSetsAgentActiveSystemProperty() {
        // OPEN-3 (closed in v26.0): the agent announces itself via a system
        // property so companion loaders (e.g. AprismPrismate) can detect a
        // mutually exclusive Aprism agent in the same JVM.
        AprismAgent.premain("aprismVersion=v26.0-Alpha.1;mcEdit=JE;mcVersion=26.2",
                fakeInstrumentation());

        assertThat(System.getProperty("aprism.agent.active"))
                .as("the agent must set aprism.agent.active=true")
                .isEqualTo("true");
    }

    @Test
    void premainWithUnresolvableDependencyDoesNotThrowAndWritesCrashReport() throws Exception {
        // A mod that depends on a missing mod makes dependency resolution fail
        // inside bootstrapProduction; the agent must swallow the failure.
        writeAjeWithDepends(gameRoot.resolve("mods").resolve("broken.aje"), "broken", "1.0.0",
                "com.aprism.loader.testmods.RecordingMod",
                "\"missing-mod\":\">=1.0.0\"");

        AprismAgent.premain(
                "aprismVersion=v26.0-Alpha.1;mcEdit=JE;mcVersion=26.2;gameRoot="
                        + gameRoot.toString().replace('\\', '/'),
                fakeInstrumentation());

        // No exception propagated out of premain; a crash report was written.
        Path crashDir = gameRoot.resolve("aprism-crashes");
        assertThat(crashDir).exists();
        assertThat(Files.list(crashDir).count())
                .as("A crash report should be written on bootstrap failure")
                .isGreaterThanOrEqualTo(1);

        // v26.2-Alpha.6 hardening: the crash report embeds the failure cause
        // and the recent structured-log tail so a failed boot is actionable.
        try (var crashFiles = Files.list(crashDir)) {
            Path report = crashFiles.findFirst().orElseThrow();
            String content = Files.readString(report);
            assertThat(content).contains("Aprism Loader crash report");
            assertThat(content).contains("Cause");
            assertThat(content)
                    .as("the report embeds the recent structured-log tail")
                    .contains("Recent log");
        }
    }

    private static void writeAje(Path ajeFile, String id, String version,
            String mainEntrypoint) throws IOException {
        writeAjeWithDepends(ajeFile, id, version, mainEntrypoint, null);
    }

    private static void writeAjeWithDepends(Path ajeFile, String id, String version,
            String mainEntrypoint, String dependsBody) throws IOException {
        Files.createDirectories(ajeFile.getParent());
        String json = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
                  "displayName": "%s",
                  "description": "test",
                  "environment": "*",
                  "entrypoints": {"main":["%s"]},
                  "mixins": [],
                  "depends": {%s},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(id, version, id,
                mainEntrypoint == null ? "" : mainEntrypoint,
                dependsBody == null ? "" : dependsBody);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(ajeFile))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}

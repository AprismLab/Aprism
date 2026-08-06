package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * JUnit 5 + AssertJ tests for the SpongePowered Mixin integration in Aprism.
 * Verifies that the Mixin environment bootstraps, the transformer is acquired,
 * mixin configs can be offered, and the transformer delegation pipeline runs
 * without error on synthetic bytecode.
 *
 * <p>The Mixin environment is a JVM-level singleton, so these tests are
 * designed to be order-independent: they re-initialize the runtime in
 * {@code @BeforeEach} and reset the bootstrap state in {@code @AfterEach}.
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismMixinTest {

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        AprismRuntime.instance().initialize(null, "26.0.0", "JE", "1.21.4");
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class EnvironmentBootstrap {
        @Test
        void mixinEnvironmentInitializesOnRuntimeInit() {
            assertThat(AprismMixinBootstrap.isEnvironmentInitialized())
                    .as("Mixin environment should be initialized after AprismRuntime.initialize")
                    .isTrue();
        }

        @Test
        void transformerIsAvailableAfterInit() {
            assertThat(AprismRuntime.instance().isMixinAvailable())
                    .as("IMixinTransformer should be acquired after initialization")
                    .isTrue();
        }

        @Test
        void aprismServiceIsValidWhenClassLoaderBound() {
            // The AprismMixinService should be the active service (isValid = true)
            // because the classloader is bound via the bootstrap.
            assertThat(AprismMixinBootstrap.getClassLoader())
                    .as("Aprism classloader should be bound to the mixin service")
                    .isNotNull();
        }
    }

    @Nested
    class ConfigOffering {
        @Test
        void offerMixinConfigDoesNotThrowForMissingConfig() {
            // Offering a config that doesn't exist on the classpath should be
            // handled gracefully (logged, not thrown).
            AprismRuntime.instance().offerMixinConfig("nonexistent.mixins.json");
            // No exception = pass
        }

        @Test
        void offerNullConfigIsNoop() {
            AprismRuntime.instance().offerMixinConfig(null);
            AprismRuntime.instance().offerMixinConfig("");
            // No exception = pass
        }

        @Test
        void offerDuplicateConfigIsIdempotent() {
            AprismRuntime.instance().offerMixinConfig("dup.mixins.json");
            // Second offer should be silently skipped (deduplicated)
            AprismRuntime.instance().offerMixinConfig("dup.mixins.json");
            // No exception = pass
        }
    }

    @Nested
    class TransformerDelegation {
        @Test
        void transformPassthroughForNonTargetedClass() {
            byte[] synthetic = generateMinimalClass("com/aprism/test/SyntheticTarget");
            byte[] result = AprismMixinBootstrap.transformClassBytes(
                    "com.aprism.test.SyntheticTarget", synthetic);
            assertThat(result)
                    .as("Transformer should return non-null bytes for a non-targeted class")
                    .isNotNull();
            assertThat(result.length)
                    .as("Resulting bytecode should be non-empty")
                    .isGreaterThan(0);
        }

        @Test
        void transformReturnsOriginalWhenMixinUnavailable() {
            // Reset the bootstrap to simulate Mixin being unavailable, then
            // verify the passthrough returns the original bytes.
            byte[] synthetic = generateMinimalClass("com/aprism/test/Unavailable");
            AprismMixinBootstrap.reset();
            byte[] result = AprismMixinBootstrap.transformClassBytes(
                    "com.aprism.test.Unavailable", synthetic);
            assertThat(result)
                    .as("Should return original bytes when Mixin is unavailable")
                    .isSameAs(synthetic);
            // Re-bootstrap for tearDown consistency
            AprismRuntime.instance().initialize(null, "26.0.0", "JE", "1.21.4");
        }

        @Test
        void transformHandlesNullBytesGracefully() {
            // The bootstrap catches Throwable, so even null input should not
            // propagate an exception (it returns the input, which is null).
            byte[] result = AprismMixinBootstrap.transformClassBytes(
                    "com.aprism.test.NullBytes", null);
            // Returns null (the input) because the transformer catches the NPE
            assertThat(result).isNull();
        }
    }

    @Nested
    class ManifestMixinRegistration {
        @Test
        void modWithMixinsRegistersConfigsDuringLoad() throws Exception {
            // Write a .aje with a mixins list; the load should offer each config
            // to the Mixin environment without throwing.
            writeAjeWithMixins(gameRoot.resolve("mods").resolve("mixinmod.aje"),
                    "mixinmod", "1.0.0",
                    List.of("mixinmod.mixins.json", "mixinmod.client.mixins.json"));

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            assertThat(AprismRuntime.instance().getMods())
                    .as("Mod should be loaded")
                    .hasSize(1);
            assertThat(AprismRuntime.instance().getMods().get(0).getId())
                    .isEqualTo("mixinmod");
        }

        @Test
        void modWithoutMixinsLoadsCleanly() throws Exception {
            writeAjeWithMixins(gameRoot.resolve("mods").resolve("plain.aje"),
                    "plain", "1.0.0", List.of());

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            assertThat(AprismRuntime.instance().getMods()).hasSize(1);
            assertThat(AprismRuntime.instance().isMixinAvailable()).isTrue();
        }
    }

    /**
     * Generates a minimal valid class bytecode using ASM.
     *
     * @param internalName the internal class name (slashed, e.g. "com/Test")
     * @return the class bytecode
     */
    private static byte[] generateMinimalClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Writes a synthetic {@code .aje} archive with a manifest declaring the
     * given mixin configs.
     *
     * @param ajeFile the destination .aje file
     * @param id      the mod id
     * @param version the mod version
     * @param mixins  the list of mixin config resource paths
     * @throws IOException if the archive cannot be written
     */
    private static void writeAjeWithMixins(Path ajeFile, String id, String version,
            List<String> mixins) throws IOException {
        Files.createDirectories(ajeFile.getParent());
        String mixinsJson = mixins.isEmpty()
                ? "[]"
                : mixins.stream()
                        .map(m -> "\"" + m + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .map(s -> "[" + s + "]")
                        .orElse("[]");
        String json = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
                  "displayName": "%s",
                  "description": "test",
                  "environment": "*",
                  "entrypoints": {},
                  "mixins": %s,
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(id, version, id, mixinsJson);
        writeZip(ajeFile, "aprism.manifest.json", json);
    }

    /**
     * Writes a zip archive containing a single entry.
     *
     * @param file       the destination file
     * @param entryName  the zip entry name
     * @param content    the entry content
     * @throws IOException if the archive cannot be written
     */
    private static void writeZip(Path file, String entryName, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}

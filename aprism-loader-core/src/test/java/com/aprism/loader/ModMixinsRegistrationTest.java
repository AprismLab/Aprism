package com.aprism.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Reproduces the real-game Mixin scenario in-process: a .aje whose inner jar
 * holds a mixin config + mixin class, extracted and registered, then offers the
 * config and transforms the target. Captures the real exception if config init
 * fails, to pinpoint the "Error initialising mixin config" cause.
 */
class ModMixinsRegistrationTest {

    private static final String CONFIG = "mixinproof.mixins.json";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        AprismRuntime.instance().initialize(null, "26.0.0", "JE", "26.2");
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void debugGameMixinScenario() throws Exception {
        // Build a .aje mirroring the real proof mod: inner jar holds the mixin
        // class + mixin config. The mixin targets the generated class by name.
        Path aje = gameRoot.resolve("mods").resolve("mixinproof.aje");
        Files.createDirectories(aje.getParent());
        writeAjeWithGeneratedTargetMixin(aje);

        AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(AprismRuntime.instance().getMods())
                .as("the mixin-proof mod should be loaded")
                .hasSize(1);
        assertThat(Mixins.getConfigs())
                .as("the mixin config from the extracted .aje must register "
                        + "(was: restrictions NPE / isClassLoaded Class.forName bugs)")
                .hasSize(1);
    }


    private static void writeAjeWithGeneratedTargetMixin(Path aje) throws Exception {
        String cfgJson = """
                {
                  "required": true,
                  "minVersion": "0.8",
                  "package": "com.aprism.loader.mixintest",
                  "compatibilityLevel": "JAVA_21",
                  "mixins": ["GeneratedTargetMixin"],
                  "injectors": {"defaultRequire": 1}
                }
                """;
        String manifest = """
                {
                  "schemaVersion": 1,
                  "id": "mixinproof",
                  "version": "1.0.0",
                  "displayName": "mixinproof",
                  "description": "debug",
                  "environment": "*",
                  "entrypoints": {},
                  "mixins": ["mixinproof.mixins.json"],
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """;
        // inner jar: GeneratedTargetMixin.class + config
        byte[] mixinClass;
        try (java.io.InputStream in = ModMixinsRegistrationTest.class.getResourceAsStream(
                "/com/aprism/loader/mixintest/GeneratedTargetMixin.class")) {
            mixinClass = in.readAllBytes();
        }
        java.io.ByteArrayOutputStream inner = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(inner)) {
            z.putNextEntry(new ZipEntry("com/aprism/loader/mixintest/GeneratedTargetMixin.class"));
            z.write(mixinClass);
            z.closeEntry();
            z.putNextEntry(new ZipEntry(CONFIG));
            z.write(cfgJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            z.closeEntry();
        }
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(aje))) {
            z.putNextEntry(new ZipEntry("aprism.manifest.json"));
            z.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("mixinproof.jar"));
            z.write(inner.toByteArray());
            z.closeEntry();
        }
    }
}

package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.testmods.ForgeRecordingMod;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Tests for {@link ForgeEntrypointBridge}: bytecode discovery of
 * {@code net.minecraftforge.fml.common.Mod}-annotated classes and
 * constructor injection of the mod-scoped {@link IEventBus}.
 *
 * @author BlockConnect@StarsailsClover
 */
class ForgeEntrypointBridgeTest {

    private static final String MOD_CLASS = "com.aprism.loader.testmods.ForgeRecordingMod";

    @TempDir
    Path tempDir;

    @Test
    void findsModClassByAnnotation() throws Exception {
        Path jar = writeForgeModJar("forgemod");
        List<String> found = ForgeEntrypointBridge.findModClasses(jar, "forgemod");
        assertThat(found).containsExactly(MOD_CLASS);
    }

    @Test
    void annotationValueMismatchFindsNothing() throws Exception {
        Path jar = writeForgeModJar("forgemod");
        // The fixture is annotated @Mod("forgemod"); another id must not match
        List<String> found = ForgeEntrypointBridge.findModClasses(jar, "othermod");
        assertThat(found).isEmpty();
    }

    @Test
    void jarWithoutModClassesReturnsEmpty() throws Exception {
        Path jar = tempDir.resolve("empty.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("dummy.txt"));
            zos.write("not a class".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertThat(ForgeEntrypointBridge.findModClasses(jar, "forgemod")).isEmpty();
    }

    @Test
    void constructInjectsEventBus() {
        IEventBus bus = new ForgeEventBus();
        Object instance = ForgeEntrypointBridge.construct(ForgeRecordingMod.class, bus);
        assertThat(instance).isInstanceOf(ForgeRecordingMod.class);
        assertThat(((ForgeRecordingMod) instance).getInjectedBus()).isSameAs(bus);
    }

    @Test
    void constructFallsBackToNoArgConstructor() {
        // A class without an IEventBus constructor must still construct
        Object instance = ForgeEntrypointBridge.construct(NoBusMod.class, new ForgeEventBus());
        assertThat(instance).isInstanceOf(NoBusMod.class);
    }

    /** A Forge-style mod with only a no-arg constructor. */
    static final class NoBusMod {
        NoBusMod() {
        }
    }

    private Path writeForgeModJar(String modId) throws IOException {
        Path jar = tempDir.resolve("forgemod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            String toml = """
                    modLoader="javafml"
                    loaderVersion="[47,)"
                    license="MIT"

                    [[mods]]
                    modId="%s"
                    version="1.0.0"
                    """.formatted(modId);
            zos.write(toml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();

            String classPath = MOD_CLASS.replace('.', '/') + ".class";
            zos.putNextEntry(new ZipEntry(classPath));
            try (InputStream in = ForgeRecordingMod.class.getResourceAsStream(
                    "ForgeRecordingMod.class")) {
                zos.write(in.readAllBytes());
            }
            zos.closeEntry();
        }
        return jar;
    }
}

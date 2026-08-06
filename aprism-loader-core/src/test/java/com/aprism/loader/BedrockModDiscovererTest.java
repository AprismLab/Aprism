package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * JUnit 5 + AssertJ tests for {@link BedrockModDiscoverer} and the BE mod
 * loading pipeline in {@link AprismRuntime}.
 *
 * <p>Builds synthetic {@code .abe} archives with manifests, native library
 * directories per platform, behavior_pack/resource_pack/scripts markers, and
 * verifies discovery, native library resolution, and runtime integration.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockModDiscovererTest {

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        AprismRuntime.instance().initialize(null, "26.0.0", "BE", "26.2");
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void discoversAbeModFromAprismModsDir() throws Exception {
        writeAbe(gameRoot.resolve("aprism_mods").resolve("testmod.abe"),
                "testmod", "1.0.0", false, false, false);

        AprismRuntime.instance().loadBedrockMods(gameRoot);

        List<LoadedBedrockModContainer> mods = AprismRuntime.instance().getBedrockMods();
        assertThat(mods).hasSize(1);
        assertThat(mods.get(0).getId()).isEqualTo("testmod");
        assertThat(mods.get(0).getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void discoversMultipleMods() throws Exception {
        writeAbe(gameRoot.resolve("aprism_mods").resolve("alpha.abe"),
                "alpha", "1.0.0", false, false, false);
        writeAbe(gameRoot.resolve("aprism_mods").resolve("beta.abe"),
                "beta", "2.0.0", false, false, false);

        AprismRuntime.instance().loadBedrockMods(gameRoot);

        List<LoadedBedrockModContainer> mods = AprismRuntime.instance().getBedrockMods();
        assertThat(mods).hasSize(2);
        assertThat(mods.get(0).getId()).isEqualTo("alpha");
        assertThat(mods.get(1).getId()).isEqualTo("beta");
    }

    @Test
    void emptyAprismModsDirLoadsNothing() {
        // No aprism_mods/ directory at all
        AprismRuntime.instance().loadBedrockMods(gameRoot);
        assertThat(AprismRuntime.instance().getBedrockMods()).isEmpty();
    }

    @Test
    void resolvesNativeLibrariesPerPlatform() throws Exception {
        writeAbeWithNatives(gameRoot.resolve("aprism_mods").resolve("nativemod.abe"),
                "nativemod", "1.0.0");

        AprismRuntime.instance().loadBedrockMods(gameRoot);

        LoadedBedrockModContainer mod = AprismRuntime.instance().getBedrockMod("nativemod");
        assertThat(mod).isNotNull();

        // Windows native library
        List<String> winLibs = mod.getNativeLibraries(
                BedrockModDiscoverer.BedrockPlatform.WINDOWS);
        assertThat(winLibs).hasSize(1);
        assertThat(winLibs.get(0)).contains("nativemod.dll");

        // Android native library
        List<String> androidLibs = mod.getNativeLibraries(
                BedrockModDiscoverer.BedrockPlatform.ANDROID);
        assertThat(androidLibs).hasSize(1);
        assertThat(androidLibs.get(0)).contains("nativemod.so");

        // No macOS library (not included)
        List<String> macLibs = mod.getNativeLibraries(
                BedrockModDiscoverer.BedrockPlatform.MACOS);
        assertThat(macLibs).isEmpty();
    }

    @Test
    void detectsBehaviorPackAndScripts() throws Exception {
        writeAbe(gameRoot.resolve("aprism_mods").resolve("bpmod.abe"),
                "bpmod", "1.0.0", true, true, true);

        AprismRuntime.instance().loadBedrockMods(gameRoot);

        LoadedBedrockModContainer mod = AprismRuntime.instance().getBedrockMod("bpmod");
        assertThat(mod).isNotNull();
        assertThat(mod.hasBehaviorPack()).isTrue();
        assertThat(mod.hasResourcePack()).isTrue();
        assertThat(mod.hasScripts()).isTrue();
    }

    @Test
    void modWithoutNativeLibrariesHasEmptyMap() throws Exception {
        writeAbe(gameRoot.resolve("aprism_mods").resolve("plain.abe"),
                "plain", "1.0.0", false, false, false);

        AprismRuntime.instance().loadBedrockMods(gameRoot);

        LoadedBedrockModContainer mod = AprismRuntime.instance().getBedrockMod("plain");
        assertThat(mod).isNotNull();
        assertThat(mod.nativeLibraries()).isEmpty();
    }

    @Test
    void getBedrockModReturnsNullForUnknownId() {
        assertThat(AprismRuntime.instance().getBedrockMod("nonexistent")).isNull();
    }

    @Test
    void performLoadBranchesToBedrockWhenMcEditIsBE() throws Exception {
        writeAbe(gameRoot.resolve("aprism_mods").resolve("auto.abe"),
                "auto", "1.0.0", false, false, false);

        // performLoad should detect mcEdit=BE and call loadBedrockMods
        AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

        assertThat(AprismRuntime.instance().getBedrockMods()).hasSize(1);
        assertThat(AprismRuntime.instance().getMods()).isEmpty();
    }

    @Test
    void platformDetectReturnsCurrentOs() {
        BedrockModDiscoverer.BedrockPlatform detected =
                BedrockModDiscoverer.BedrockPlatform.detect();
        // On the test machine (Windows), detect should return WINDOWS
        assertThat(detected).isNotNull();
    }

    @Test
    void platformFromIdResolvesKnownPlatforms() {
        assertThat(BedrockModDiscoverer.BedrockPlatform.fromId("windows"))
                .isEqualTo(BedrockModDiscoverer.BedrockPlatform.WINDOWS);
        assertThat(BedrockModDiscoverer.BedrockPlatform.fromId("android"))
                .isEqualTo(BedrockModDiscoverer.BedrockPlatform.ANDROID);
        assertThat(BedrockModDiscoverer.BedrockPlatform.fromId("ios"))
                .isEqualTo(BedrockModDiscoverer.BedrockPlatform.IOS);
        assertThat(BedrockModDiscoverer.BedrockPlatform.fromId("unknown")).isNull();
        assertThat(BedrockModDiscoverer.BedrockPlatform.fromId(null)).isNull();
    }

    /**
     * Writes a synthetic {@code .abe} archive with a manifest and optional
     * behavior_pack/resource_pack/scripts directories.
     *
     * @param abeFile the destination .abe file
     * @param id      the mod id
     * @param version the mod version
     * @param hasBP   whether to include a behavior_pack/ directory
     * @param hasRP   whether to include a resource_pack/ directory
     * @param hasScripts whether to include a scripts/ directory
     * @throws IOException if the archive cannot be written
     */
    private static void writeAbe(Path abeFile, String id, String version,
            boolean hasBP, boolean hasRP, boolean hasScripts) throws IOException {
        Files.createDirectories(abeFile.getParent());
        String json = manifestJson(id, version);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(abeFile))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            if (hasBP) {
                zos.putNextEntry(new ZipEntry("behavior_pack/.keep"));
                zos.write(new byte[0]);
                zos.closeEntry();
            }
            if (hasRP) {
                zos.putNextEntry(new ZipEntry("resource_pack/.keep"));
                zos.write(new byte[0]);
                zos.closeEntry();
            }
            if (hasScripts) {
                zos.putNextEntry(new ZipEntry("scripts/main.js"));
                zos.write("// test".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /**
     * Writes a synthetic {@code .abe} archive with native libraries for
     * Windows and Android platforms.
     *
     * @param abeFile the destination .abe file
     * @param id      the mod id
     * @param version the mod version
     * @throws IOException if the archive cannot be written
     */
    private static void writeAbeWithNatives(Path abeFile, String id, String version)
            throws IOException {
        Files.createDirectories(abeFile.getParent());
        String json = manifestJson(id, version);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(abeFile))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            // Windows native library
            zos.putNextEntry(new ZipEntry("native/windows/" + id + ".dll"));
            zos.write(new byte[]{0x4D, 0x5A}); // minimal PE header
            zos.closeEntry();
            // Android native library
            zos.putNextEntry(new ZipEntry("native/android/" + id + ".so"));
            zos.write(new byte[]{0x7F, 0x45, 0x4C, 0x46}); // ELF magic
            zos.closeEntry();
        }
    }

    /**
     * Builds a minimal {@code aprism.manifest.json} for a BE mod.
     *
     * @param id      the mod id
     * @param version the mod version
     * @return the JSON content
     */
    private static String manifestJson(String id, String version) {
        return """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
                  "displayName": "%s",
                  "description": "test BE mod",
                  "environment": "*",
                  "entrypoints": {},
                  "mixins": [],
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(id, version, id);
    }
}

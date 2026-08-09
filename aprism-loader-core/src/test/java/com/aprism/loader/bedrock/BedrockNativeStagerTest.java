package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;
import com.aprism.loader.LoadedBedrockModContainer;
import com.aprism.manifest.AprismManifest;

/**
 * Tests for the fail-closed {@link BedrockNativeStager}, which extracts
 * planned native libraries from {@code .abe} archives onto disk.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockNativeStagerTest {

    @TempDir
    Path gameRoot;

    private final BedrockNativeStager stager = new BedrockNativeStager();

    private static AprismManifest manifest(String id) {
        return new AprismManifest(1, id, "1.0.0", id, "", "*",
                Map.of(), List.of(), Map.of(), Map.of(), null, List.of(), Map.of());
    }

    /** Writes a synthetic .abe containing a native library with real bytes. */
    private Path writeAbeWithNative(String id, BedrockPlatform platform, String fileName,
                                    byte[] magic) throws IOException {
        Path abe = gameRoot.resolve("aprism_mods").resolve(id + ".abe");
        Files.createDirectories(abe.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(abe))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("native/" + platform.id() + "/" + fileName));
            zos.write(magic);
            zos.closeEntry();
        }
        return abe;
    }

    private static BedrockInjectionPlan.InjectionAction action(String id, BedrockPlatform platform) {
        return new BedrockInjectionPlan.InjectionAction(id, platform,
                "native/" + platform.id() + "/" + id + (platform == BedrockPlatform.WINDOWS ? ".dll" : ".so"));
    }

    private static BedrockInjectionPlan.Plan plan(List<BedrockInjectionPlan.InjectionAction> actions) {
        return new BedrockInjectionPlan.Plan(true, null, actions, "26.2.0");
    }

    @Test
    void stagesSingleWindowsLibrary() throws Exception {
        byte[] peMagic = {0x4D, 0x5A, (byte) 0x90, 0x00};
        Path abe = writeAbeWithNative("alpha", BedrockPlatform.WINDOWS, "alpha.dll", peMagic);

        var container = new LoadedBedrockModContainer(manifest("alpha"), abe,
                Map.of(BedrockPlatform.WINDOWS, List.of("native/windows/alpha.dll")),
                false, false, false);
        var modsById = Map.of("alpha", container);

        var result = stager.stage(gameRoot, plan(List.of(action("alpha", BedrockPlatform.WINDOWS))), modsById);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagedLibraries()).hasSize(1);
        Path staged = result.stagedLibraries().get("native/windows/alpha.dll");
        assertThat(staged).isNotNull();
        assertThat(Files.exists(staged)).isTrue();
        assertThat(staged.getFileName().toString()).isEqualTo("alpha.dll");
        // Staged under <gameRoot>/aprism_native_stage/<modId>/
        assertThat(staged.toString()).contains(BedrockNativeStager.STAGE_DIR_NAME);
        assertThat(Files.readAllBytes(staged)).isEqualTo(peMagic);
    }

    @Test
    void stagesMultipleModsPreservingEntryOrder() throws Exception {
        Path abeA = writeAbeWithNative("alpha", BedrockPlatform.WINDOWS, "alpha.dll", new byte[]{0x4D});
        Path abeB = writeAbeWithNative("beta", BedrockPlatform.WINDOWS, "beta.dll", new byte[]{0x5A});

        var modsById = new LinkedHashMap<String, LoadedBedrockModContainer>();
        modsById.put("alpha", new LoadedBedrockModContainer(manifest("alpha"), abeA,
                Map.of(BedrockPlatform.WINDOWS, List.of("native/windows/alpha.dll")), false, false, false));
        modsById.put("beta", new LoadedBedrockModContainer(manifest("beta"), abeB,
                Map.of(BedrockPlatform.WINDOWS, List.of("native/windows/beta.dll")), false, false, false));

        var actions = new ArrayList<BedrockInjectionPlan.InjectionAction>();
        actions.add(action("alpha", BedrockPlatform.WINDOWS));
        actions.add(action("beta", BedrockPlatform.WINDOWS));

        var result = stager.stage(gameRoot, plan(actions), modsById);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stagedLibraries()).hasSize(2);
        assertThat(new ArrayList<>(result.stagedLibraries().keySet()))
                .containsExactly("native/windows/alpha.dll", "native/windows/beta.dll");
    }

    @Test
    void refusesInfeasiblePlan() {
        var refused = new BedrockInjectionPlan.Plan(false,
                BedrockInjectionPlan.RefusalReason.VERSION_UNKNOWN, List.of(), "99.9.9");
        var result = stager.stage(gameRoot, refused, Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.message()).contains("not feasible");
        assertThat(result.stagedLibraries()).isEmpty();
    }

    @Test
    void refusesWhenModContainerMissing() {
        var actions = List.of(action("ghost", BedrockPlatform.WINDOWS));
        var result = stager.stage(gameRoot, plan(actions), Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.message()).contains("no source archive");
    }

    @Test
    void refusesWhenEntryAbsentFromArchive() throws Exception {
        // Archive has no native/<platform>/missing.dll entry.
        Path abe = writeAbeWithNative("alpha", BedrockPlatform.WINDOWS, "other.dll", new byte[]{0x4D});
        var container = new LoadedBedrockModContainer(manifest("alpha"), abe,
                Map.of(), false, false, false);

        var result = stager.stage(gameRoot,
                plan(List.of(action("alpha", BedrockPlatform.WINDOWS))),
                Map.of("alpha", container));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.message()).contains("failed to stage");
        assertThat(result.stagedLibraries()).isEmpty();
    }

    @Test
    void isIdempotentOnRepeatedStaging() throws Exception {
        byte[] peMagic = {0x4D, 0x5A};
        Path abe = writeAbeWithNative("alpha", BedrockPlatform.WINDOWS, "alpha.dll", peMagic);
        var container = new LoadedBedrockModContainer(manifest("alpha"), abe,
                Map.of(BedrockPlatform.WINDOWS, List.of("native/windows/alpha.dll")), false, false, false);
        var modsById = Map.of("alpha", container);
        var plan = plan(List.of(action("alpha", BedrockPlatform.WINDOWS)));

        var first = stager.stage(gameRoot, plan, modsById);
        var second = stager.stage(gameRoot, plan, modsById);

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
        assertThat(first.stagedLibraries()).isEqualTo(second.stagedLibraries());
        Path staged = second.stagedLibraries().get("native/windows/alpha.dll");
        assertThat(Files.readAllBytes(staged)).isEqualTo(peMagic);
    }
}

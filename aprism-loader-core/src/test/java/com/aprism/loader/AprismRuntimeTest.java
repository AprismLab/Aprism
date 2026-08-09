package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import com.aprism.api.AprismPhase;
import com.aprism.api.ExtensionType;
import com.aprism.loader.testexts.DynamicSupportExtension;
import com.aprism.loader.testexts.RecordingExtension;
import com.aprism.loader.testmods.RecordingMod;

/**
 * JUnit 5 + AssertJ tests for {@link AprismRuntime} covering the full two-phase
 * load pipeline, dependency resolution, and mod lifecycle dispatch.
 *
 * <p>Tests build synthetic game instance directories with
 * {@code aprism-extensions/}, {@code mods/}, and loader-specific folders, then
 * drive the runtime through {@code performLoad} and {@code invokeEntrypoints}.
 *
 * <p>Entrypoint classes are loaded from the test classpath (the runtime's
 * classloader delegates to the system classloader via parent-first loading).
 * The {@link RecordingMod} fixture records the phases it receives so tests
 * can assert ordering and dispatch.
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismRuntimeTest {

    private static final String RECORDING_MOD_CLASS = "com.aprism.loader.testmods.RecordingMod";
    private static final String RECORDING_EXT_CLASS = "com.aprism.loader.testexts.RecordingExtension";
    private static final String DYNAMIC_SUPPORT_EXT_CLASS = "com.aprism.loader.testexts.DynamicSupportExtension";

    @TempDir
    Path gameRoot;

    @BeforeEach
    void setUp() {
        RecordingMod.resetGlobal();
        RecordingExtension.resetGlobal();
        DynamicSupportExtension.reset();
        // Re-initialize the singleton runtime for each test
        AprismRuntime.instance().initialize(null, "26.0.0", "JE", "1.21.4");
    }

    @AfterEach
    void tearDown() {
        // Shutdown the runtime to release the classloader's file locks on
        // mod jars before @TempDir tries to delete them (Windows file locking)
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class TwoPhaseLoad {
        @Test
        void emptyGameRootLoadsNothing() throws Exception {
            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            assertThat(AprismRuntime.instance().getMods()).isEmpty();
            assertThat(AprismRuntime.instance().getLoadedExtensions()).isEmpty();
        }

        @Test
        void loadsAprismNativeModFromModsFolder() throws Exception {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            List<LoadedModContainer> mods = AprismRuntime.instance().getMods();
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).getId()).isEqualTo("alpha");
            assertThat(mods.get(0).getLoaderKey()).isEqualTo(ModDiscoverer.APRISM_NATIVE);
        }

        @Test
        void loadsExtensionThenModFromRegisteredFolder() throws Exception {
            // Phase 1: install Fabric-Support extension
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Fabric-Support.aep"),
                    loaderSupportAepJson("Fa"));

            // Phase 2: a Fabric mod in fabric-mods/ (not in mods/)
            writeJar(gameRoot.resolve("fabric-mods").resolve("fabricmod.jar"),
                    "fabric.mod.json", fabricModJson("fabricmod", "1.0.0"));

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            assertThat(AprismRuntime.instance().getLoadedExtensions()).hasSize(1);
            assertThat(AprismRuntime.instance().getLoaderFolders())
                    .containsEntry("Fa", "fabric-mods");

            List<LoadedModContainer> mods = AprismRuntime.instance().getMods();
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).getId()).isEqualTo("fabricmod");
            assertThat(mods.get(0).getLoaderKey()).isEqualTo("Fa");
        }

        @Test
        void unregisteredLoaderFolderNotScanned() throws Exception {
            // No Fabric-Support extension; fabric-mods/ should be ignored
            writeJar(gameRoot.resolve("fabric-mods").resolve("ignored.jar"),
                    "fabric.mod.json", fabricModJson("ignored", "1.0.0"));
            writeAje(gameRoot.resolve("mods").resolve("native.aje"), "native", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            List<LoadedModContainer> mods = AprismRuntime.instance().getMods();
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).getId()).isEqualTo("native");
        }
    }

    @Nested
    class DependencyResolution {
        @Test
        void loadsDependencyBeforeDependent() throws Exception {
            // mod "a" depends on mod "b"; "b" must load first
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("b", "*"));
            writeAje(gameRoot.resolve("mods").resolve("b.aje"), "b", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            List<LoadedModContainer> mods = AprismRuntime.instance().getMods();
            assertThat(mods).hasSize(2);
            // b (dependency) must come before a (dependent)
            assertThat(mods.get(0).getId()).isEqualTo("b");
            assertThat(mods.get(1).getId()).isEqualTo("a");
        }

        @Test
        void missingDependencyAbortsLoad() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("b", "*"));

            assertThatThrownBy(() ->
                    AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions")))
                    .hasMessageContaining("missing dependency")
                    .hasMessageContaining("b");
        }

        @Test
        void circularDependencyAbortsLoad() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("b", "*"));
            writeAje(gameRoot.resolve("mods").resolve("b.aje"), "b", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("a", "*"));

            assertThatThrownBy(() ->
                    AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions")))
                    .hasMessageContaining("cycle");
        }

        @Test
        void versionConflictAbortsLoad() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("b", "2.0.0"));
            writeAje(gameRoot.resolve("mods").resolve("b.aje"), "b", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            assertThatThrownBy(() ->
                    AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions")))
                    .hasMessageContaining("requires")
                    .hasMessageContaining("b")
                    .hasMessageContaining("2.0.0");
        }

        @Test
        void aprismEnvironmentIdResolves() throws Exception {
            // OPEN-1 (closed in v26.0): a mod may depend on the running
            // Aprism Loader itself. The environment map supplies the
            // normalized running version ("26.0.0" here), so >=26.0 resolves.
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("aprism", ">=26.0"));

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            assertThat(AprismRuntime.instance().getMod("a"))
                    .as("a mod depending on the aprism environment id must load")
                    .isNotNull();
        }

        @Test
        void aprismEnvironmentIdVersionMismatchAborts() throws IOException {
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("aprism", ">=99.0"));

            assertThatThrownBy(() ->
                    AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions")))
                    .hasMessageContaining("requires")
                    .hasMessageContaining("aprism");
        }
    }

    @Nested
    class LifecycleDispatch {
        @Test
        void invokeCommonLifecycleCallsAllPhasesInOrder() throws Exception {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            AprismRuntime.instance().invokeCommonLifecycle();

            List<String> phases = RecordingMod.getGlobalPhases();
            assertThat(phases).containsExactly(
                    "PREINIT:alpha", "INIT:alpha", "SETUP:alpha", "COMPLETE:alpha");
        }

        @Test
        void invokeEntrypointsDispatchesPerPhase() throws Exception {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.PREINIT);
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("PREINIT:alpha");
            RecordingMod.resetGlobal();

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.INIT);
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("INIT:alpha");
            RecordingMod.resetGlobal();

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.SETUP);
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("SETUP:alpha");
            RecordingMod.resetGlobal();

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.COMPLETE);
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("COMPLETE:alpha");
        }

        @Test
        void dependencyOrderPreservedAcrossPhases() throws Exception {
            // a depends on b; b must receive each phase before a
            writeAje(gameRoot.resolve("mods").resolve("a.aje"), "a", "1.0.0",
                    RECORDING_MOD_CLASS, java.util.Map.of("b", "*"));
            writeAje(gameRoot.resolve("mods").resolve("b.aje"), "b", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            AprismRuntime.instance().invokeCommonLifecycle();

            List<String> phases = RecordingMod.getGlobalPhases();
            assertThat(phases).containsExactly(
                    "PREINIT:b", "PREINIT:a",
                    "INIT:b", "INIT:a",
                    "SETUP:b", "SETUP:a",
                    "COMPLETE:b", "COMPLETE:a");
        }

        @Test
        void instanceStoredOnContainerAfterInit() throws Exception {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            // No instance yet
            LoadedModContainer before = AprismRuntime.instance().getMod("alpha");
            assertThat(before.getInstance()).isNull();

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.INIT);

            // After INIT, the RecordingMod instance should be cached on the container
            LoadedModContainer after = AprismRuntime.instance().getMod("alpha");
            assertThat(after.getInstance()).isInstanceOf(RecordingMod.class);
        }

        @Test
        void contextBoundToMod() throws Exception {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            AprismRuntime.instance().invokeEntrypoints(AprismPhase.INIT);

            LoadedModContainer mc = AprismRuntime.instance().getMod("alpha");
            RecordingMod mod = (RecordingMod) mc.getInstance();
            assertThat(mod).isNotNull();
            assertThat(mod.getContext()).isNotNull();
            assertThat(mod.getContext().getMod().getId()).isEqualTo("alpha");
            assertThat(mod.getContext().getEventBus()).isNotNull();
            assertThat(mod.getContext().getRegistry()).isNotNull();
            assertThat(mod.getContext().getLogger()).isNotNull();
        }

        @Test
        void noEntrypointModSkipsInvocation() throws Exception {
            // Mod with no entrypoints: load succeeds, but no phase invoked
            writeAje(gameRoot.resolve("mods").resolve("silent.aje"), "silent", "1.0.0",
                    null, null);

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            AprismRuntime.instance().invokeCommonLifecycle();

            assertThat(RecordingMod.getGlobalPhases()).isEmpty();
            assertThat(AprismRuntime.instance().getMod("silent")).isNotNull();
        }
    }

    @Nested
    class ClientServerEntrypoints {
        @Test
        void clientEntrypointInvokedOnClientPhase() throws Exception {
            // main + client entrypoints both point to RecordingMod
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null,
                    "client", List.of(RECORDING_MOD_CLASS));

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.INIT);
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("INIT:alpha");
            RecordingMod.resetGlobal();

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.CLIENT);
            // CLIENT phase invokes onInitialize on the client entrypoint class
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("INIT:alpha");
        }

        @Test
        void serverEntrypointInvokedOnServerPhase() throws Exception {
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null,
                    "server", List.of(RECORDING_MOD_CLASS));

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            AprismRuntime.instance().invokeEntrypoints(AprismPhase.SERVER);
            assertThat(RecordingMod.getGlobalPhases()).containsExactly("INIT:alpha");
        }
    }

    @Nested
    class InitializationIdempotency {
        @Test
        void duplicateInitializeIsIgnoredAfterModsLoaded() throws Exception {
            // Load a mod first so the runtime has populated state.
            writeAje(gameRoot.resolve("mods").resolve("alpha.aje"), "alpha", "1.0.0",
                    RECORDING_MOD_CLASS, null);
            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
            assertThat(AprismRuntime.instance().getMods()).hasSize(1);

            // A duplicate initialize() (e.g. premain + agentmain, or a
            // re-triggered load) must NOT reset the runtime: the loaded mod and
            // the classloader reference survive, so mods are not dropped or
            // double-loaded.
            AprismRuntime.instance().initialize(null, "26.0.0", "JE", "1.21.4");

            assertThat(AprismRuntime.instance().getMods())
                    .as("duplicate initialize() must not clear loaded mods")
                    .hasSize(1);
            assertThat(AprismRuntime.instance().getMods().get(0).getId()).isEqualTo("alpha");
        }
    }

    @Nested
    class ExtensionLifecycle {
        @Test
        void extensionWithEntrypointInvokedOnInitialize() throws Exception {
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Test-Ext.aep"),
                    extensionJson("test-ext", "api-extension", RECORDING_EXT_CLASS, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            assertThat(RecordingExtension.getGlobalLog()).containsExactly("INIT:test-ext");
        }

        @Test
        void extensionWithoutEntrypointRegisteredButNotInvoked() throws Exception {
            writeAep(gameRoot.resolve("aprism-extensions").resolve("No-Entry.aep"),
                    extensionJson("no-entry", "api-extension", null, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            assertThat(RecordingExtension.getGlobalLog()).isEmpty();
            LoadedExtensionContainer container = AprismRuntime.instance().getExtension("no-entry");
            assertThat(container).isNotNull();
            assertThat(container.getInstance()).isNull();
        }

        @Test
        void extensionInstanceStoredOnContainer() throws Exception {
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Test-Ext.aep"),
                    extensionJson("test-ext", "api-extension", RECORDING_EXT_CLASS, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            LoadedExtensionContainer container = AprismRuntime.instance().getExtension("test-ext");
            assertThat(container).isNotNull();
            assertThat(container.getInstance()).isInstanceOf(RecordingExtension.class);
        }

        @Test
        void extensionContextProvidesAccessors() throws Exception {
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Test-Ext.aep"),
                    extensionJson("test-ext", "api-extension", RECORDING_EXT_CLASS, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            LoadedExtensionContainer container = AprismRuntime.instance().getExtension("test-ext");
            RecordingExtension ext = (RecordingExtension) container.getInstance();
            assertThat(ext.getContext()).isNotNull();
            assertThat(ext.getContext().getExtension()).isEqualTo(container);
            assertThat(ext.getContext().getEventBus()).isNotNull();
            assertThat(ext.getContext().getRegistry()).isNotNull();
            assertThat(ext.getContext().getLogger()).isNotNull();
        }

        @Test
        void extensionTypeParsedOnContainer() throws Exception {
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Test-Ext.aep"),
                    extensionJson("test-ext", "platform-adapter", RECORDING_EXT_CLASS, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            LoadedExtensionContainer container = AprismRuntime.instance().getExtension("test-ext");
            assertThat(container.getType()).isEqualTo(ExtensionType.PLATFORM_ADAPTER);
        }

        @Test
        void manifestDrivenLoaderSupportStillWorks() throws Exception {
            // Extension with loaderKey in manifest auto-registers folder,
            // AND the entrypoint is still invoked
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Fabric-Support.aep"),
                    extensionJson("fabric-support", "loader-support", RECORDING_EXT_CLASS, "Fa", null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            // Folder registered from manifest
            assertThat(AprismRuntime.instance().getLoaderFolders())
                    .containsEntry("Fa", "fabric-mods");
            // Entrypoint also invoked
            assertThat(RecordingExtension.getGlobalLog()).containsExactly("INIT:fabric-support");
        }

        @Test
        void dynamicLoaderSupportViaContext() throws Exception {
            // Extension registers folders at runtime via context.registerLoaderSupport
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Dynamic.aep"),
                    extensionJson("dynamic", "loader-support", DYNAMIC_SUPPORT_EXT_CLASS, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            assertThat(DynamicSupportExtension.getInitCount()).isEqualTo(1);
            assertThat(AprismRuntime.instance().getLoaderFolders())
                    .containsEntry("Fa", "fabric-mods")
                    .containsEntry("N", "neoforge-mods");
        }

        @Test
        void extensionLoadsBeforeMods() throws Exception {
            // Extension registers Fabric support, then a Fabric mod is loaded
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Fabric-Support.aep"),
                    extensionJson("fabric-support", "loader-support", RECORDING_EXT_CLASS, "Fa", null));
            writeJar(gameRoot.resolve("fabric-mods").resolve("fabricmod.jar"),
                    "fabric.mod.json", fabricModJson("fabricmod", "1.0.0"));

            AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));

            // Extension initialized
            assertThat(RecordingExtension.getGlobalLog()).containsExactly("INIT:fabric-support");
            // Mod loaded from fabric-mods/
            assertThat(AprismRuntime.instance().getMods()).hasSize(1);
            assertThat(AprismRuntime.instance().getMods().get(0).getId()).isEqualTo("fabricmod");
            assertThat(AprismRuntime.instance().getMods().get(0).getLoaderKey()).isEqualTo("Fa");
        }

        @Test
        void getLoadedExtensionContainersReturnsAll() throws Exception {
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Ext1.aep"),
                    extensionJson("ext1", "api-extension", RECORDING_EXT_CLASS, null, null));
            writeAep(gameRoot.resolve("aprism-extensions").resolve("Ext2.aep"),
                    extensionJson("ext2", "api-extension", RECORDING_EXT_CLASS, null, null));

            AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

            List<LoadedExtensionContainer> containers = AprismRuntime.instance().getLoadedExtensionContainers();
            assertThat(containers).hasSize(2);
            assertThat(containers).extracting(LoadedExtensionContainer::getExtensionId)
                    .containsExactlyInAnyOrder("ext1", "ext2");
        }
    }

    // ----- fixture helpers -----

    /**
     * Writes a synthetic {@code .aje} archive with an {@code aprism.manifest.json}
     * declaring the given id, version, main entrypoint, and dependencies.
     *
     * @param ajeFile     the destination .aje file
     * @param id          the mod id
     * @param version     the mod version
     * @param mainEntrypoint the main entrypoint class (may be {@code null})
     * @param depends     the dependency map (may be {@code null})
     * @throws IOException if the archive cannot be written
     */
    private static void writeAje(Path ajeFile, String id, String version,
            String mainEntrypoint, java.util.Map<String, String> depends) throws IOException {
        writeAje(ajeFile, id, version, mainEntrypoint, depends, null, null);
    }

    /**
     * Writes a synthetic {@code .aje} archive with main + side entrypoints.
     *
     * @param ajeFile        the destination .aje file
     * @param id             the mod id
     * @param version        the mod version
     * @param mainEntrypoint the main entrypoint class (may be {@code null})
     * @param depends        the dependency map (may be {@code null})
     * @param sideKey        the side entrypoint key ({@code "client"} or {@code "server"}), or {@code null}
     * @param sideEntrypoints the side entrypoint classes, or {@code null}
     * @throws IOException if the archive cannot be written
     */
    private static void writeAje(Path ajeFile, String id, String version,
            String mainEntrypoint, java.util.Map<String, String> depends,
            String sideKey, List<String> sideEntrypoints) throws IOException {
        Files.createDirectories(ajeFile.getParent());
        StringBuilder entrypoints = new StringBuilder("{");
        boolean first = true;
        if (mainEntrypoint != null) {
            entrypoints.append("\"main\":[\"").append(mainEntrypoint).append("\"]");
            first = false;
        }
        if (sideKey != null && sideEntrypoints != null && !sideEntrypoints.isEmpty()) {
            if (!first) {
                entrypoints.append(",");
            }
            entrypoints.append("\"").append(sideKey).append("\":[");
            for (int i = 0; i < sideEntrypoints.size(); i++) {
                if (i > 0) {
                    entrypoints.append(",");
                }
                entrypoints.append("\"").append(sideEntrypoints.get(i)).append("\"");
            }
            entrypoints.append("]");
        }
        entrypoints.append("}");

        String dependsJson = depends == null || depends.isEmpty()
                ? "{}"
                : depends.entrySet().stream()
                        .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .map(s -> "{" + s + "}")
                        .orElse("{}");

        String json = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
                  "displayName": "%s",
                  "description": "test",
                  "environment": "*",
                  "entrypoints": %s,
                  "mixins": [],
                  "depends": %s,
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(id, version, id, entrypoints, dependsJson);
        writeZip(ajeFile, "aprism.manifest.json", json);
    }

    /**
     * Writes a synthetic {@code .aep} archive containing an
     * {@code aprism.extension.json} for a loader-support extension.
     *
     * @param aepFile  the destination .aep file
     * @param json     the manifest JSON
     * @throws IOException if the archive cannot be written
     */
    private static void writeAep(Path aepFile, String json) throws IOException {
        Files.createDirectories(aepFile.getParent());
        writeZip(aepFile, "aprism.extension.json", json);
    }

    /**
     * Builds a loader-support extension manifest JSON for the given loader key.
     *
     * @param loaderKey the loader key (Fa, Fo, N, L, Q)
     * @return the JSON content
     */
    private static String loaderSupportAepJson(String loaderKey) {
        return """
                {
                  "extensionId": "%s-support",
                  "type": "loader-support",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": "%s",
                  "loaderRange": "[1.0.0,2.0.0)",
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": null,
                  "provides": ["%s-loader"],
                  "depends": {}
                }
                """.formatted(loaderKey, loaderKey, loaderKey);
    }

    /**
     * Builds an extension manifest JSON with the given fields.
     *
     * @param extensionId the extension id
     * @param type        the extension type (loader-support, api-extension, platform-adapter, converter)
     * @param entrypoint  the entrypoint class (may be {@code null})
     * @param loaderKey   the loader key (may be {@code null})
     * @param loaderRange the loader version range (may be {@code null})
     * @return the JSON content
     */
    private static String extensionJson(String extensionId, String type, String entrypoint,
            String loaderKey, String loaderRange) {
        String entryJson = entrypoint == null ? "null" : "\"" + entrypoint + "\"";
        String loaderKeyJson = loaderKey == null ? "null" : "\"" + loaderKey + "\"";
        String loaderRangeJson = loaderRange == null ? "null" : "\"" + loaderRange + "\"";
        return """
                {
                  "extensionId": "%s",
                  "type": "%s",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": %s,
                  "loaderRange": %s,
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": %s,
                  "provides": [],
                  "depends": {}
                }
                """.formatted(extensionId, type, loaderKeyJson, loaderRangeJson, entryJson);
    }

    /**
     * Writes a single-entry zip archive.
     *
     * @param file    the destination file
     * @param entry   the entry name
     * @param content the entry content
     * @throws IOException if the archive cannot be written
     */
    private static void writeZip(Path file, String entry, String content) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /**
     * Writes a jar containing a single entry with the given content.
     *
     * @param jarFile the destination jar file
     * @param entry   the entry name
     * @param content the entry content
     * @throws IOException if the jar cannot be written
     */
    private static void writeJar(Path jarFile, String entry, String content) throws IOException {
        Files.createDirectories(jarFile.getParent());
        writeZip(jarFile, entry, content);
    }

    /**
     * Builds a minimal {@code fabric.mod.json} for the given mod id.
     *
     * @param id      the mod id
     * @param version the mod version
     * @return the JSON content
     */
    private static String fabricModJson(String id, String version) {
        return """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "%s",
                  "name": "%s",
                  "environment": "*"
                }
                """.formatted(id, version, id);
    }
}

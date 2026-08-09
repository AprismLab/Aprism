package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;
import com.aprism.loader.LoadedBedrockModContainer;
import com.aprism.manifest.AprismManifest;

/**
 * Tests for the fail-closed {@link BedrockInjectionPlan}.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockInjectionPlanTest {

    private BedrockVersionDatabase db;
    private BedrockInjectionPlan plan;

    @BeforeEach
    void setUp() {
        db = new BedrockVersionDatabase();
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 42, true, ""));
        db.register(new BedrockVersionDatabase.VersionEntry("26.1.0", 40, false, "incomplete"));
        plan = new BedrockInjectionPlan(db);
    }

    private static AprismManifest manifest(String id) {
        return new AprismManifest(1, id, "1.0.0", id, "", "*",
                Map.of(), List.of(), Map.of(), Map.of(), null, List.of(), Map.of());
    }

    private static LoadedBedrockModContainer mod(String id, BedrockPlatform platform, String... entryPaths) {
        return new LoadedBedrockModContainer(manifest(id), null,
                Map.of(platform, List.of(entryPaths)), false, false, false);
    }

    @Test
    void plansNativeInjectionForSupportedVersion() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        BedrockInjectionPlan.Plan p = plan.plan("26.2.0", BedrockPlatform.WINDOWS, mods);

        assertThat(p.isFeasible()).isTrue();
        assertThat(p.refusal()).isNull();
        assertThat(p.actions()).hasSize(1);
        assertThat(p.actions().get(0).modId()).isEqualTo("alpha");
        assertThat(p.actions().get(0).platform()).isEqualTo(BedrockPlatform.WINDOWS);
        assertThat(p.actions().get(0).entryPath()).isEqualTo("native/windows/alpha.dll");
    }

    @Test
    void refusesUnknownVersion() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        BedrockInjectionPlan.Plan p = plan.plan("99.9.9", BedrockPlatform.WINDOWS, mods);

        assertThat(p.isFeasible()).isFalse();
        assertThat(p.refusal()).isEqualTo(BedrockInjectionPlan.RefusalReason.VERSION_UNKNOWN);
        assertThat(p.actions()).isEmpty();
    }

    @Test
    void refusesUnsupportedVersion() {
        var mods = List.of(mod("alpha", BedrockPlatform.WINDOWS, "native/windows/alpha.dll"));
        BedrockInjectionPlan.Plan p = plan.plan("26.1.0", BedrockPlatform.WINDOWS, mods);

        assertThat(p.isFeasible()).isFalse();
        assertThat(p.refusal()).isEqualTo(BedrockInjectionPlan.RefusalReason.VERSION_UNSUPPORTED);
        assertThat(p.actions()).isEmpty();
    }

    @Test
    void refusesEmptyModList() {
        BedrockInjectionPlan.Plan p = plan.plan("26.2.0", BedrockPlatform.WINDOWS, List.of());

        assertThat(p.isFeasible()).isFalse();
        assertThat(p.refusal()).isEqualTo(BedrockInjectionPlan.RefusalReason.NO_MODS);
        assertThat(p.actions()).isEmpty();
    }

    @Test
    void refusesWhenNoNativeLibsForPlatform() {
        // Mod only has Android natives; asking for Windows yields no actions.
        var mods = List.of(mod("alpha", BedrockPlatform.ANDROID, "native/android/alpha.so"));
        BedrockInjectionPlan.Plan p = plan.plan("26.2.0", BedrockPlatform.WINDOWS, mods);

        assertThat(p.isFeasible()).isFalse();
        assertThat(p.refusal()).isEqualTo(BedrockInjectionPlan.RefusalReason.NO_NATIVE_LIBS);
        assertThat(p.actions()).isEmpty();
    }

    @Test
    void collectsActionsFromMultipleModsAndLibs() {
        var mods = List.of(
                mod("alpha", BedrockPlatform.WINDOWS, "native/windows/a.dll", "native/windows/b.dll"),
                mod("beta", BedrockPlatform.WINDOWS, "native/windows/beta.dll"));
        BedrockInjectionPlan.Plan p = plan.plan("26.2.0", BedrockPlatform.WINDOWS, mods);

        assertThat(p.isFeasible()).isTrue();
        assertThat(p.actions()).hasSize(3);
        assertThat(p.actions()).extracting(BedrockInjectionPlan.InjectionAction::entryPath)
                .containsExactly("native/windows/a.dll", "native/windows/b.dll", "native/windows/beta.dll");
    }

    @Test
    void versionCheckHappensBeforeModCheck() {
        // Even with no mods, an unknown version must report VERSION_UNKNOWN (not NO_MODS).
        BedrockInjectionPlan.Plan p = plan.plan("99.9.9", BedrockPlatform.WINDOWS, List.of());

        assertThat(p.isFeasible()).isFalse();
        assertThat(p.refusal()).isEqualTo(BedrockInjectionPlan.RefusalReason.VERSION_UNKNOWN);
    }
}

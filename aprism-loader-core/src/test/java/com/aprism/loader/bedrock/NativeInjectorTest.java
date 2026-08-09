package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;
import com.aprism.manifest.ManifestParseException;

/**
 * Tests for the {@link NativeInjector} SPI contract, exercised through a fake
 * implementation that records lifecycle calls.
 *
 * @author BlockConnect@StarsailsClover
 */
class NativeInjectorTest {

    /** Records lifecycle calls for assertions. */
    private static final class RecordingInjector implements NativeInjector {
        final List<String> calls = new ArrayList<>();
        boolean attachOk = true;
        boolean injectOk = true;
        boolean unattachOk = true;

        @Override
        public NativeInjectionResult attach(AttachmentTarget target) {
            calls.add("attach:" + target.beVersion() + ":" + target.processId());
            return attachOk ? NativeInjectionResult.ok() : NativeInjectionResult.fail("attach refused");
        }

        @Override
        public NativeInjectionResult inject(NativeInjectionRequest request) {
            calls.add("inject:" + request.modId() + ":" + request.stagedLibrary());
            return injectOk ? NativeInjectionResult.ok() : NativeInjectionResult.fail("inject failed");
        }

        @Override
        public NativeInjectionResult unattach() {
            calls.add("unattach");
            return unattachOk ? NativeInjectionResult.ok() : NativeInjectionResult.fail("unattach failed");
        }
    }

    private static BedrockInjectionPlan.Plan feasiblePlan(String... modIds) {
        List<BedrockInjectionPlan.InjectionAction> actions = new ArrayList<>();
        for (String id : modIds) {
            actions.add(new BedrockInjectionPlan.InjectionAction(
                    id, BedrockPlatform.WINDOWS, "native/windows/" + id + ".dll"));
        }
        return new BedrockInjectionPlan.Plan(true, null, actions, "26.2.0");
    }

    private static BedrockInjectionPlan.Plan refusedPlan() {
        return new BedrockInjectionPlan.Plan(false,
                BedrockInjectionPlan.RefusalReason.VERSION_UNKNOWN, List.of(), "99.9.9");
    }

    private static Map<String, Path> stagedFor(String... modIds) {
        Map<String, Path> m = new java.util.LinkedHashMap<>();
        for (String id : modIds) {
            m.put("native/windows/" + id + ".dll", Path.of("stage", id, id + ".dll"));
        }
        return m;
    }

    @Test
    void injectAllAttachesInjectsThenUnattachesInOrder() {
        RecordingInjector injector = new RecordingInjector();
        var target = new NativeInjector.AttachmentTarget(
                BedrockPlatform.WINDOWS, "26.2.0", 1234L, Path.of("Minecraft.exe"));

        var result = injector.injectAll(feasiblePlan("alpha", "beta"), target, stagedFor("alpha", "beta"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(injector.calls).containsExactly(
                "attach:26.2.0:1234",
                "inject:alpha:" + Path.of("stage", "alpha", "alpha.dll"),
                "inject:beta:" + Path.of("stage", "beta", "beta.dll"),
                "unattach");
    }

    @Test
    void injectAllRefusesInfeasiblePlan() {
        RecordingInjector injector = new RecordingInjector();
        var target = new NativeInjector.AttachmentTarget(
                BedrockPlatform.WINDOWS, "99.9.9", 1L, Path.of("Minecraft.exe"));

        var result = injector.injectAll(refusedPlan(), target, Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.message()).contains("not feasible");
        assertThat(injector.calls).isEmpty();
    }

    @Test
    void injectAllStopsOnAttachFailure() {
        RecordingInjector injector = new RecordingInjector();
        injector.attachOk = false;
        var target = new NativeInjector.AttachmentTarget(
                BedrockPlatform.WINDOWS, "26.2.0", 1L, Path.of("Minecraft.exe"));

        var result = injector.injectAll(feasiblePlan("alpha"), target, stagedFor("alpha"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.message()).contains("attach refused");
        assertThat(injector.calls).containsExactly("attach:26.2.0:1");
    }

    @Test
    void injectAllStopsOnInjectFailureAndStillUnattaches() {
        RecordingInjector injector = new RecordingInjector();
        injector.injectOk = false;
        var target = new NativeInjector.AttachmentTarget(
                BedrockPlatform.WINDOWS, "26.2.0", 1L, Path.of("Minecraft.exe"));

        var result = injector.injectAll(feasiblePlan("alpha", "beta"), target, stagedFor("alpha", "beta"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(injector.calls).containsExactly(
                "attach:26.2.0:1",
                "inject:alpha:" + Path.of("stage", "alpha", "alpha.dll"),
                "unattach");
    }

    @Test
    void injectAllFailsWhenStagedLibraryMissing() {
        RecordingInjector injector = new RecordingInjector();
        var target = new NativeInjector.AttachmentTarget(
                BedrockPlatform.WINDOWS, "26.2.0", 1L, Path.of("Minecraft.exe"));

        // Plan references alpha.dll but the staged map is empty.
        var result = injector.injectAll(feasiblePlan("alpha"), target, Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.message()).contains("no staged library");
        assertThat(injector.calls).contains("unattach");
    }

    @Test
    void resultFactoryMethods() {
        assertThat(NativeInjector.NativeInjectionResult.ok().isSuccess()).isTrue();
        assertThat(NativeInjector.NativeInjectionResult.ok("done").message()).isEqualTo("done");
        assertThat(NativeInjector.NativeInjectionResult.fail("bad").isSuccess()).isFalse();
    }
}

package com.aprism.loader.loaderext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.aprism.api.AprismPhase;
import com.aprism.loader.LoadedModContainer;
import com.aprism.manifest.AprismManifest;

/**
 * Tests the {@link LoaderEntrypointHandler} SPI seam: registration, lookup,
 * replacement, clearing, and dispatch delegation. This is the extraction point
 * that lets foreign-loader entrypoint support live in the AprismRefract
 * sub-project instead of {@code aprism-loader-core}.
 *
 * @author BlockConnect@StarsailsClover
 */
class LoaderEntrypointRegistryTest {

    /** A loader key used only by these tests. */
    private static final String TEST_KEY = "Tx";

    @AfterEach
    void tearDown() {
        LoaderEntrypointRegistry.clear();
    }

    /** Records every invoke call for assertions. */
    private static final class RecordingHandler implements LoaderEntrypointHandler {
        private final String key;
        private final List<AprismPhase> calls = new ArrayList<>();

        RecordingHandler(String key) {
            this.key = key;
        }

        @Override
        public String loaderKey() {
            return key;
        }

        @Override
        public void invoke(LoadedModContainer container, AprismPhase phase) {
            calls.add(phase);
        }
    }

    private static LoadedModContainer container(String loaderKey) {
        AprismManifest manifest = new AprismManifest(
                1, "testmod", "1.0.0", "Test Mod", "desc", "*",
                java.util.Map.of(), List.of(), java.util.Map.of(),
                java.util.Map.of(), null, List.of(), java.util.Map.of());
        return new LoadedModContainer(manifest, Path.of("testmod.jar"), loaderKey);
    }

    @Test
    void registerAndGetRoundTrip() {
        RecordingHandler handler = new RecordingHandler(TEST_KEY);
        LoaderEntrypointRegistry.register(handler);
        assertThat(LoaderEntrypointRegistry.get(TEST_KEY)).isSameAs(handler);
    }

    @Test
    void getUnknownKeyReturnsNull() {
        assertThat(LoaderEntrypointRegistry.get("nope")).isNull();
        assertThat(LoaderEntrypointRegistry.get(null)).isNull();
    }

    @Test
    void laterRegistrationReplacesEarlier() {
        RecordingHandler first = new RecordingHandler(TEST_KEY);
        RecordingHandler second = new RecordingHandler(TEST_KEY);
        LoaderEntrypointRegistry.register(first);
        LoaderEntrypointRegistry.register(second);
        assertThat(LoaderEntrypointRegistry.get(TEST_KEY)).isSameAs(second);
    }

    @Test
    void clearRemovesAllHandlers() {
        LoaderEntrypointRegistry.register(new RecordingHandler(TEST_KEY));
        LoaderEntrypointRegistry.register(new RecordingHandler("Ty"));
        LoaderEntrypointRegistry.clear();
        assertThat(LoaderEntrypointRegistry.get(TEST_KEY)).isNull();
        assertThat(LoaderEntrypointRegistry.get("Ty")).isNull();
    }

    @Test
    void registerNullHandlerRejected() {
        assertThatThrownBy(() -> LoaderEntrypointRegistry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlerReceivesInvokedPhase() {
        RecordingHandler handler = new RecordingHandler(TEST_KEY);
        LoaderEntrypointRegistry.register(handler);
        LoaderEntrypointHandler looked = LoaderEntrypointRegistry.get(TEST_KEY);
        looked.invoke(container(TEST_KEY), AprismPhase.INIT);
        looked.invoke(container(TEST_KEY), AprismPhase.CLIENT);
        assertThat(handler.calls).containsExactly(AprismPhase.INIT, AprismPhase.CLIENT);
    }

    @Test
    void defaultIsExclusiveIsTrue() {
        assertThat(new RecordingHandler(TEST_KEY).isExclusive()).isTrue();
    }
}

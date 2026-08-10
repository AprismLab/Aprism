package com.aprism.loader.resourcereload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.resourcereload.ResourceReloadListener;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the resource-reload listener registry (v26.3-Alpha.9, Fabric
 * ResourceManagerReloadListener parity): registration-window gating,
 * duplicate rejection, fail-safe reload firing, and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class ResourceReloadRegistryImplTest {

    private final ResourceReloadRegistryImpl registry = new ResourceReloadRegistryImpl();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class WindowGating {

        @Test
        void registerBeforeWindowOpensIsRejected() {
            assertThatThrownBy(() -> registry.register(() -> { }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("registration window");
        }

        @Test
        void registerWhileWindowIsOpenIsAccepted() {
            registry.openWindow();

            registry.register(() -> { });

            assertThat(registry.registeredListeners()).hasSize(1);
        }

        @Test
        void registerAfterFreezeIsRejected() {
            registry.openWindow();
            registry.register(() -> { });
            registry.freezeWindow();

            assertThat(registry.isFrozen()).isTrue();
            assertThatThrownBy(() -> registry.register(() -> { }))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void clearResetsWindowAndFrozenState() {
            registry.openWindow();
            registry.register(() -> { });
            registry.freezeWindow();

            registry.clear();

            assertThat(registry.isFrozen()).isFalse();
            assertThat(registry.isWindowOpen()).isFalse();
            assertThat(registry.registeredListeners()).isEmpty();
        }
    }

    @Nested
    class Validation {

        @Test
        void nullListenerIsRejected() {
            registry.openWindow();

            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void duplicateListenerIsRejected() {
            registry.openWindow();
            ResourceReloadListener listener = () -> { };
            registry.register(listener);

            assertThatThrownBy(() -> registry.register(listener))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        void registrationOrderIsPreserved() {
            registry.openWindow();
            ResourceReloadListener first = () -> { };
            ResourceReloadListener second = () -> { };
            registry.register(first);
            registry.register(second);

            assertThat(registry.registeredListeners()).containsExactly(first, second);
        }
    }

    @Nested
    class ReloadFiring {

        @Test
        void fireReloadInvokesEveryListener() {
            registry.openWindow();
            List<String> fired = new ArrayList<>();
            registry.register(() -> fired.add("a"));
            registry.register(() -> fired.add("b"));

            registry.fireReload();

            assertThat(fired).containsExactly("a", "b");
        }

        @Test
        void throwingListenerDoesNotAbortOthers() {
            registry.openWindow();
            List<String> fired = new ArrayList<>();
            registry.register(() -> fired.add("before"));
            registry.register(() -> { throw new RuntimeException("boom"); });
            registry.register(() -> fired.add("after"));

            registry.fireReload();

            assertThat(fired).containsExactly("before", "after");
        }

        @Test
        void fireReloadWithNoListenersIsSafe() {
            registry.fireReload();
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesResourceReloadRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getResourceReloadRegistry()).isNotNull();
        }

        @Test
        void runtimeShutdownClearsListeners() {
            AprismRuntime runtime = AprismRuntime.instance();
            ResourceReloadRegistryImpl impl = (ResourceReloadRegistryImpl) runtime.getResourceReloadRegistry();
            impl.openWindow();
            impl.register(() -> { });

            runtime.shutdown();

            assertThat(runtime.getResourceReloadRegistry().registeredListeners()).isEmpty();
        }
    }
}

package com.aprism.loader.keybinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.keybinding.KeyBindingSpec;
import com.aprism.loader.AprismRuntime;

/**
 * Tests for the key-binding registry (v26.3-Alpha.8, Fabric
 * KeyBindingRegistry parity): registration-window gating, duplicate
 * rejection, spec validation, and runtime wiring.
 *
 * @author BlockConnect@StarsailsClover
 */
class KeyBindingRegistryImplTest {

    private final KeyBindingRegistryImpl registry = new KeyBindingRegistryImpl();

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Nested
    class WindowGating {

        @Test
        void registerBeforeWindowOpensIsRejected() {
            assertThatThrownBy(() -> registry.register(
                    new KeyBindingSpec("kb.action", "category", 75)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("registration window");
        }

        @Test
        void registerWhileWindowIsOpenIsAccepted() {
            registry.openWindow();

            registry.register(new KeyBindingSpec("kb.action", "category", 75));

            assertThat(registry.registeredKeyBindings()).hasSize(1);
            assertThat(registry.registeredKeyBindings().get(0).defaultKeyCode()).isEqualTo(75);
        }

        @Test
        void registerAfterFreezeIsRejected() {
            registry.openWindow();
            registry.register(new KeyBindingSpec("kb.action", "category", 75));
            registry.freezeWindow();

            assertThat(registry.isFrozen()).isTrue();
            assertThat(registry.isWindowOpen()).isFalse();
            assertThatThrownBy(() -> registry.register(
                    new KeyBindingSpec("kb.other", "category", 0)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void clearResetsWindowAndFrozenState() {
            registry.openWindow();
            registry.register(new KeyBindingSpec("kb.action", "category", 75));
            registry.freezeWindow();

            registry.clear();

            assertThat(registry.isFrozen()).isFalse();
            assertThat(registry.isWindowOpen()).isFalse();
            assertThat(registry.registeredKeyBindings()).isEmpty();
        }
    }

    @Nested
    class Validation {

        @Test
        void duplicateKeyBindingIdIsRejected() {
            registry.openWindow();
            registry.register(new KeyBindingSpec("kb.action", "category", 75));

            assertThatThrownBy(() -> registry.register(
                    new KeyBindingSpec("kb.action", "other", 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("duplicate key-binding id");
        }

        @Test
        void blankIdIsRejected() {
            assertThatThrownBy(() -> new KeyBindingSpec("", "category", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void blankCategoryIsRejected() {
            assertThatThrownBy(() -> new KeyBindingSpec("kb.action", "", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void nullSpecIsRejected() {
            registry.openWindow();

            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void registrationOrderIsPreserved() {
            registry.openWindow();
            registry.register(new KeyBindingSpec("kb.a", "cat", 65));
            registry.register(new KeyBindingSpec("kb.b", "cat", 66));

            assertThat(registry.registeredKeyBindings())
                    .extracting(KeyBindingSpec::id)
                    .containsExactly("kb.a", "kb.b");
        }
    }

    @Nested
    class RuntimeWiring {

        @Test
        void runtimeExposesKeyBindingRegistry() {
            AprismRuntime runtime = AprismRuntime.instance();

            assertThat(runtime.getKeyBindingRegistry()).isNotNull();
        }

        @Test
        void runtimeShutdownClearsKeyBindings() {
            AprismRuntime runtime = AprismRuntime.instance();
            KeyBindingRegistryImpl impl = (KeyBindingRegistryImpl) runtime.getKeyBindingRegistry();
            impl.openWindow();
            impl.register(new KeyBindingSpec("kb.action", "category", 75));

            runtime.shutdown();

            KeyBindingRegistryImpl fresh = (KeyBindingRegistryImpl) runtime.getKeyBindingRegistry();
            assertThat(fresh.isWindowOpen()).isFalse();
            assertThat(fresh.registeredKeyBindings()).isEmpty();
        }
    }
}

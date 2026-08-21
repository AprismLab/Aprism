package com.aprism.loader.keybinding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.keybinding.KeyBindingSpec;

/**
 * JUnit 5 + AssertJ tests for {@link KeyBindingBindingInstaller}
 * (v26.5-Alpha.5).
 *
 * @author BlockConnect@StarsailsClover
 */
class KeyBindingBindingInstallerTest {

    private KeyBindingRegistryImpl registry;
    private KeyBindingBindingInstaller installer;

    @BeforeEach
    void setUp() {
        registry = new KeyBindingRegistryImpl();
        registry.openWindow();
        installer = new KeyBindingBindingInstaller(registry);
    }

    private static class RecordingBridge implements InputSystemBridge {
        final List<KeyBindingSpec> bound = new ArrayList<>();
        boolean unbindAllCalled;

        @Override
        public void bind(KeyBindingSpec spec) {
            bound.add(spec);
        }

        @Override
        public void unbindAll() {
            unbindAllCalled = true;
        }
    }

    private static class ThrowingBridge implements InputSystemBridge {
        final String failId;

        ThrowingBridge(String failId) {
            this.failId = failId;
        }

        @Override
        public void bind(KeyBindingSpec spec) {
            if (failId.equals(spec.id())) {
                throw new RuntimeException("simulated bind failure for " + spec.id());
            }
        }

        @Override
        public void unbindAll() {
        }
    }

    @Nested
    class BridgeAttachment {

        @Test
        void noBridgeByDefault() {
            assertThat(installer.isBridgeAttached()).isFalse();
        }

        @Test
        void setBridgeAttaches() {
            installer.setBridge(new RecordingBridge());
            assertThat(installer.isBridgeAttached()).isTrue();
        }

        @Test
        void setNullDetaches() {
            installer.setBridge(new RecordingBridge());
            installer.setBridge(null);
            assertThat(installer.isBridgeAttached()).isFalse();
        }
    }

    @Nested
    class BindKeyBindings {

        @Test
        void bindWithoutBridgeIsNoOp() {
            registry.register(new KeyBindingSpec("test:key", "category", 65));
            registry.freezeWindow();

            int bound = installer.bindKeyBindings();
            assertThat(bound).isEqualTo(0);
        }

        @Test
        void bindAllWithBridge() {
            var bridge = new RecordingBridge();
            installer.setBridge(bridge);

            registry.register(new KeyBindingSpec("kb1", "cat", 65));
            registry.register(new KeyBindingSpec("kb2", "cat", 66));
            registry.freezeWindow();

            int bound = installer.bindKeyBindings();
            assertThat(bound).isEqualTo(2);
            assertThat(bridge.bound).hasSize(2);
            assertThat(bridge.bound.get(0).id()).isEqualTo("kb1");
            assertThat(bridge.bound.get(1).id()).isEqualTo("kb2");
        }

        @Test
        void bindEmptyListReturnsZero() {
            registry.freezeWindow();
            installer.setBridge(new RecordingBridge());

            int bound = installer.bindKeyBindings();
            assertThat(bound).isEqualTo(0);
        }

        @Test
        void failingBindingDoesNotBlockOthers() {
            var bridge = new ThrowingBridge("bad");
            installer.setBridge(bridge);

            registry.register(new KeyBindingSpec("good1", "cat", 65));
            registry.register(new KeyBindingSpec("bad", "cat", 66));
            registry.register(new KeyBindingSpec("good2", "cat", 67));
            registry.freezeWindow();

            int bound = installer.bindKeyBindings();
            assertThat(bound).isEqualTo(2);
        }
    }

    @Nested
    class Unbind {

        @Test
        void unbindAllCallsBridgeUnbindAll() {
            var bridge = new RecordingBridge();
            installer.setBridge(bridge);

            installer.unbindAll();

            assertThat(bridge.unbindAllCalled).isTrue();
            assertThat(installer.isBridgeAttached()).isFalse();
        }

        @Test
        void unbindAllWithoutBridgeIsNoOp() {
            installer.unbindAll();
            assertThat(installer.isBridgeAttached()).isFalse();
        }

        @Test
        void unbindAllCatchesThrowingBridge() {
            installer.setBridge(new InputSystemBridge() {
                @Override
                public void bind(KeyBindingSpec spec) {
                }

                @Override
                public void unbindAll() {
                    throw new RuntimeException("simulated unbind failure");
                }
            });

            installer.unbindAll();
            assertThat(installer.isBridgeAttached()).isFalse();
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void nullRegistryThrows() {
            try {
                new KeyBindingBindingInstaller(null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}

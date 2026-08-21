package com.aprism.loader.resourcereload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.loader.lowlevel.MethodHookRegistry;

/**
 * JUnit 5 + AssertJ tests for {@link ResourceReloadTrigger}
 * (v26.5-Alpha.7).
 *
 * @author BlockConnect@StarsailsClover
 */
class ResourceReloadTriggerTest {

    private ResourceReloadRegistryImpl registry;
    private ResourceReloadTrigger trigger;

    @BeforeEach
    void setUp() {
        MethodHookRegistry.clear();
        registry = new ResourceReloadRegistryImpl();
        registry.openWindow();
        trigger = new ResourceReloadTrigger(registry);
    }

    @AfterEach
    void tearDown() {
        MethodHookRegistry.clear();
    }

    @Nested
    class HookRegistration {

        @Test
        void installRegistersMethodHook() {
            var target = new ResourceReloadTrigger.ReloadHookTarget(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V");
            trigger.install(target);

            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V")).isTrue();
        }

        @Test
        void installAllRegistersMultiple() {
            var t1 = new ResourceReloadTrigger.ReloadHookTarget(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V");
            var t2 = new ResourceReloadTrigger.ReloadHookTarget(
                    "net/minecraft/client/Minecraft", "reloadResources", "()V");
            trigger.installAll(List.of(t1, t2));

            assertThat(trigger.getRegisteredTargets()).hasSize(2);
        }

        @Test
        void installNullIsNoOp() {
            trigger.install(null);
            assertThat(trigger.getRegisteredTargets()).isEmpty();
        }
    }

    @Nested
    class HookFiring {

        @Test
        void hookFireTriggersReload() {
            var fired = new java.util.concurrent.atomic.AtomicBoolean(false);
            registry.register(() -> fired.set(true));
            registry.freezeWindow();

            var target = new ResourceReloadTrigger.ReloadHookTarget(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V");
            trigger.install(target);

            // Simulate the method hook firing
            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V");
            MethodHookRegistry.fire(key);

            assertThat(fired.get()).isTrue();
        }
    }

    @Nested
    class HookLifecycle {

        @Test
        void uninstallAllRemovesHooks() {
            var target = new ResourceReloadTrigger.ReloadHookTarget(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V");
            trigger.install(target);

            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V")).isTrue();

            trigger.uninstallAll();

            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/ReloadableServerResources",
                    "reload", "()V")).isFalse();
            assertThat(trigger.getRegisteredTargets()).isEmpty();
        }
    }

    @Nested
    class HookTargetValidation {

        @Test
        void validTargetPasses() {
            var target = new ResourceReloadTrigger.ReloadHookTarget(
                    "net/minecraft/Test", "reload", "()V");
            assertThat(target.isValid()).isTrue();
        }

        @Test
        void blankClassNameFails() {
            var target = new ResourceReloadTrigger.ReloadHookTarget(
                    "", "reload", "()V");
            assertThat(target.isValid()).isFalse();
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void nullRegistryThrows() {
            try {
                new ResourceReloadTrigger(null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
            }
        }
    }
}

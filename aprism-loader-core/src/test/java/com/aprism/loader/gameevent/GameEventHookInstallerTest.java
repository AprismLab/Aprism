package com.aprism.loader.gameevent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.aprism.api.AprismEventBus;
import com.aprism.api.AprismEventListener;
import com.aprism.api.gameevent.GameTickEvent;
import com.aprism.api.gameevent.ClientRenderEvent;
import com.aprism.api.gameevent.WorldLoadEvent;
import com.aprism.api.gameevent.WorldUnloadEvent;
import com.aprism.loader.AprismEventBusImpl;
import com.aprism.loader.lowlevel.MethodHookRegistry;

/**
 * JUnit 5 + AssertJ tests for {@link GameEventHookInstaller} (v26.5-Alpha.3).
 *
 * <p>Tests verify that the installer correctly registers method hooks via
 * {@link MethodHookRegistry}, that the hooks fire the corresponding game
 * events on the shared event bus, and that uninstall removes the hooks.
 *
 * @author BlockConnect@StarsailsClover
 */
class GameEventHookInstallerTest {

    private AprismEventBus eventBus;
    private GameEventDispatcher dispatcher;
    private GameEventHookInstaller installer;

    @BeforeEach
    void setUp() {
        MethodHookRegistry.clear();
        eventBus = new AprismEventBusImpl();
        dispatcher = new GameEventDispatcher(eventBus);
        dispatcher.setAttached(true);
        installer = new GameEventHookInstaller(dispatcher);
    }

    @AfterEach
    void tearDown() {
        MethodHookRegistry.clear();
    }

    @Nested
    class HookRegistration {

        @Test
        void installRegistersMethodHook() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);

            installer.install(target);

            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V")).isTrue();
        }

        @Test
        void installAllRegistersMultipleHooks() {
            var t1 = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            var t2 = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/client/Minecraft", "render",
                    "(F)V", GameEventHookInstaller.EventType.RENDER);

            installer.installAll(List.of(t1, t2));

            assertThat(installer.getRegisteredTargets()).hasSize(2);
            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V")).isTrue();
            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/client/Minecraft", "render", "(F)V")).isTrue();
        }

        @Test
        void installNullTargetIsNoOp() {
            installer.install(null);
            assertThat(installer.getRegisteredTargets()).isEmpty();
        }

        @Test
        void installAllNullListIsNoOp() {
            installer.installAll(null);
            assertThat(installer.getRegisteredTargets()).isEmpty();
        }

        @Test
        void getRegisteredTargetsIsImmutableSnapshot() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            installer.install(target);

            var snapshot = installer.getRegisteredTargets();
            assertThat(snapshot).hasSize(1);
            // Should be unmodifiable
            assertThatThrownBy(() -> snapshot.add(target));
        }

        private void assertThatThrownBy(Runnable r) {
            try {
                r.run();
                throw new AssertionError("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException expected) {
                // expected
            }
        }
    }

    @Nested
    class HookFiring {

        @Test
        void tickStartHookFiresTickStartEvent() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            installer.install(target);

            var received = new java.util.concurrent.atomic.AtomicReference<GameTickEvent>();
            eventBus.register(GameTickEvent.class, event -> {
                if (event.getStage() == GameTickEvent.Stage.START) {
                    received.set(event);
                }
            });

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V");
            MethodHookRegistry.fire(key);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getTickNumber()).isEqualTo(0L);
        }

        @Test
        void tickEndHookFiresTickEndEvent() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_END);
            installer.install(target);

            var received = new java.util.concurrent.atomic.AtomicReference<GameTickEvent>();
            eventBus.register(GameTickEvent.class, event -> {
                if (event.getStage() == GameTickEvent.Stage.END) {
                    received.set(event);
                }
            });

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V");
            MethodHookRegistry.fire(key);

            assertThat(received.get()).isNotNull();
        }

        @Test
        void renderHookFiresRenderEvent() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/client/Minecraft", "render",
                    "(F)V", GameEventHookInstaller.EventType.RENDER);
            installer.install(target);

            var received = new java.util.concurrent.atomic.AtomicReference<ClientRenderEvent>();
            eventBus.register(ClientRenderEvent.class, received::set);

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/client/Minecraft", "render", "(F)V");
            MethodHookRegistry.fire(key);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getFrameNumber()).isEqualTo(0L);
        }

        @Test
        void worldLoadHookFiresWorldLoadEvent() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/level/ServerLevel", "load",
                    "()V", GameEventHookInstaller.EventType.WORLD_LOAD);
            installer.install(target);

            var received = new java.util.concurrent.atomic.AtomicReference<WorldLoadEvent>();
            eventBus.register(WorldLoadEvent.class, received::set);

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/level/ServerLevel", "load", "()V");
            MethodHookRegistry.fire(key);

            assertThat(received.get()).isNotNull();
        }

        @Test
        void worldUnloadHookFiresWorldUnloadEvent() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/level/ServerLevel", "unload",
                    "()V", GameEventHookInstaller.EventType.WORLD_UNLOAD);
            installer.install(target);

            var received = new java.util.concurrent.atomic.AtomicReference<WorldUnloadEvent>();
            eventBus.register(WorldUnloadEvent.class, received::set);

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/level/ServerLevel", "unload", "()V");
            MethodHookRegistry.fire(key);

            assertThat(received.get()).isNotNull();
        }
    }

    @Nested
    class HookLifecycle {

        @Test
        void uninstallAllRemovesHooks() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            installer.install(target);

            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V")).isTrue();

            installer.uninstallAll();

            assertThat(MethodHookRegistry.hasHook(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V")).isFalse();
            assertThat(installer.getRegisteredTargets()).isEmpty();
        }

        @Test
        void hooksDoNotFireWhenDispatcherDetached() {
            dispatcher.setAttached(false);
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            installer.install(target);

            var received = new java.util.concurrent.atomic.AtomicReference<GameTickEvent>();
            eventBus.register(GameTickEvent.class, received::set);

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V");
            MethodHookRegistry.fire(key);

            assertThat(received.get()).isNull();
        }

        @Test
        void multipleHookTargetsForSameMethodFireBothEvents() {
            var t1 = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            var t2 = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_END);
            installer.installAll(List.of(t1, t2));

            var startReceived = new java.util.concurrent.atomic.AtomicBoolean(false);
            var endReceived = new java.util.concurrent.atomic.AtomicBoolean(false);
            eventBus.register(GameTickEvent.class, event -> {
                if (event.getStage() == GameTickEvent.Stage.START) {
                    startReceived.set(true);
                } else {
                    endReceived.set(true);
                }
            });

            String key = MethodHookRegistry.hookKey(
                    "net/minecraft/server/MinecraftServer", "tickServer", "()V");
            MethodHookRegistry.fire(key);

            assertThat(startReceived.get()).isTrue();
            assertThat(endReceived.get()).isTrue();
        }
    }

    @Nested
    class HookTargetValidation {

        @Test
        void validHookTargetPassesValidation() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", GameEventHookInstaller.EventType.TICK_START);
            assertThat(target.isValid()).isTrue();
        }

        @Test
        void hookTargetWithBlankClassNameIsInvalid() {
            var target = new GameEventHookInstaller.HookTarget(
                    "", "tickServer", "()V", GameEventHookInstaller.EventType.TICK_START);
            assertThat(target.isValid()).isFalse();
        }

        @Test
        void hookTargetWithNullEventTypeIsInvalid() {
            var target = new GameEventHookInstaller.HookTarget(
                    "net/minecraft/server/MinecraftServer", "tickServer",
                    "()V", null);
            assertThat(target.isValid()).isFalse();
        }
    }

    @Nested
    class ConstructorValidation {

        @Test
        void nullDispatcherThrows() {
            try {
                new GameEventHookInstaller(null);
                throw new AssertionError("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}

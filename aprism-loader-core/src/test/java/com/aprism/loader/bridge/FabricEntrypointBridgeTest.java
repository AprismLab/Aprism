package com.aprism.loader.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aprism.api.AprismPhase;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;

/**
 * Tests for {@link FabricEntrypointBridge}: verifies that Fabric-convention
 * entrypoint methods are invoked for the mapped phases and that unmapped
 * phases are no-ops.
 *
 * @author BlockConnect@StarsailsClover
 */
class FabricEntrypointBridgeTest {

    /** A Fabric-style mod implementing all three entrypoint interfaces. */
    static final class FabricMod implements ModInitializer, ClientModInitializer,
            DedicatedServerModInitializer {

        static final List<String> CALLS = new ArrayList<>();

        @Override
        public void onInitialize() {
            CALLS.add("main");
        }

        @Override
        public void onInitializeClient() {
            CALLS.add("client");
        }

        @Override
        public void onInitializeServer() {
            CALLS.add("server");
        }
    }

    /** A Fabric mod with only a main entrypoint. */
    static final class MainOnlyMod implements ModInitializer {
        static final List<String> CALLS = new ArrayList<>();

        @Override
        public void onInitialize() {
            CALLS.add("main");
        }
    }

    @Test
    void initPhaseInvokesMain() {
        FabricMod.CALLS.clear();
        boolean invoked = FabricEntrypointBridge.invoke(new FabricMod(), AprismPhase.INIT);
        assertThat(invoked).isTrue();
        assertThat(FabricMod.CALLS).containsExactly("main");
    }

    @Test
    void clientPhaseInvokesClient() {
        FabricMod.CALLS.clear();
        boolean invoked = FabricEntrypointBridge.invoke(new FabricMod(), AprismPhase.CLIENT);
        assertThat(invoked).isTrue();
        assertThat(FabricMod.CALLS).containsExactly("client");
    }

    @Test
    void serverPhaseInvokesServer() {
        FabricMod.CALLS.clear();
        boolean invoked = FabricEntrypointBridge.invoke(new FabricMod(), AprismPhase.SERVER);
        assertThat(invoked).isTrue();
        assertThat(FabricMod.CALLS).containsExactly("server");
    }

    @Test
    void preinitSetupCompleteAreNoOpsForFabric() {
        FabricMod.CALLS.clear();
        assertThat(FabricEntrypointBridge.invoke(new FabricMod(), AprismPhase.PREINIT)).isFalse();
        assertThat(FabricEntrypointBridge.invoke(new FabricMod(), AprismPhase.SETUP)).isFalse();
        assertThat(FabricEntrypointBridge.invoke(new FabricMod(), AprismPhase.COMPLETE)).isFalse();
        assertThat(FabricMod.CALLS).isEmpty();
    }

    @Test
    void missingMethodForPhaseReturnsFalse() {
        // MainOnlyMod has no onInitializeClient, so CLIENT phase is a no-op
        MainOnlyMod.CALLS.clear();
        boolean invoked = FabricEntrypointBridge.invoke(new MainOnlyMod(), AprismPhase.CLIENT);
        assertThat(invoked).isFalse();
        assertThat(MainOnlyMod.CALLS).isEmpty();
    }

    @Test
    void methodNameMapping() {
        assertThat(FabricEntrypointBridge.methodNameFor(AprismPhase.INIT))
                .isEqualTo("onInitialize");
        assertThat(FabricEntrypointBridge.methodNameFor(AprismPhase.CLIENT))
                .isEqualTo("onInitializeClient");
        assertThat(FabricEntrypointBridge.methodNameFor(AprismPhase.SERVER))
                .isEqualTo("onInitializeServer");
        assertThat(FabricEntrypointBridge.methodNameFor(AprismPhase.PREINIT)).isNull();
        assertThat(FabricEntrypointBridge.methodNameFor(AprismPhase.SETUP)).isNull();
        assertThat(FabricEntrypointBridge.methodNameFor(AprismPhase.COMPLETE)).isNull();
    }
}

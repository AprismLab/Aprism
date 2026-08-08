package com.aprism.loader.testmods;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/**
 * Test fixture Forge mod entrypoint. Annotated with the Forge {@code @Mod}
 * shim so {@link com.aprism.loader.bridge.ForgeEntrypointBridge} can discover
 * it by bytecode scanning. The constructor records construction and the
 * injected event bus.
 *
 * <p>Because the integration test embeds this class's bytes into the mod jar,
 * the runtime defines a child-classloader copy. Tests therefore observe the
 * constructed instance through {@code container.getInstance()} and reflection,
 * not through the static accessors (which belong to the test classloader copy).
 *
 * @author BlockConnect@StarsailsClover
 */
@Mod("forgemod")
public final class ForgeRecordingMod {

    private static volatile boolean constructed;
    private final IEventBus bus;

    /**
     * Resets the static flag. Call at the start of each test.
     */
    public static void resetGlobal() {
        constructed = false;
    }

    /**
     * @return whether the constructor was invoked (test-classloader copy only)
     */
    public static boolean wasConstructed() {
        return constructed;
    }

    /**
     * Forge constructor with {@link IEventBus} injection.
     *
     * @param bus the mod-scoped event bus
     */
    public ForgeRecordingMod(IEventBus bus) {
        constructed = true;
        this.bus = bus;
    }

    /**
     * @return the event bus injected at construction
     */
    public IEventBus getInjectedBus() {
        return bus;
    }
}

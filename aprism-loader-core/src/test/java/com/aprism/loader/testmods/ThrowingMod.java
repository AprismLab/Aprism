package com.aprism.loader.testmods;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;

/**
 * Test fixture mod whose entrypoint throws during {@code onInitialize}. Used
 * by the Alpha 4 isolation tests to verify that a single broken mod does not
 * abort the lifecycle of the remaining mods.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ThrowingMod implements IAprismMod {

    private static volatile boolean preinitCalled;

    /**
     * Resets the static flag. Call at the start of each test.
     */
    public static void resetGlobal() {
        preinitCalled = false;
    }

    /**
     * @return whether {@link #onPreInitialize} was reached
     */
    public static boolean wasPreinitCalled() {
        return preinitCalled;
    }

    @Override
    public void onPreInitialize(AprismContext context) {
        preinitCalled = true;
    }

    @Override
    public void onInitialize(AprismContext context) {
        throw new IllegalStateException("ThrowingMod deliberately fails onInitialize");
    }

    @Override
    public void onSetup(AprismContext context) {
        // never reached for the failing phase path
    }

    @Override
    public void onComplete(AprismContext context) {
        // never reached for the failing phase path
    }
}

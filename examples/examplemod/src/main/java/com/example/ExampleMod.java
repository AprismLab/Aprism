package com.example;

import java.util.logging.Logger;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;

/**
 * The canonical example of an Aprism-native mod. Implements the required
 * {@link IAprismMod} interface and logs each lifecycle callback so that a
 * successful load is visible in the Aprism log output.
 *
 * <p>This mod intentionally uses no Mixin and no access widener: it is the
 * smallest mod that can prove the Aprism loading path end-to-end.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ExampleMod implements IAprismMod {

    @Override
    public void onInitialize(AprismContext context) {
        Logger log = context.getLogger();
        log.info("[ExampleMod] onInitialize: modId=" + context.getMod().getId()
                + ", version=" + context.getMod().getVersion());
    }

    @Override
    public void onPreInitialize(AprismContext context) {
        context.getLogger().info("[ExampleMod] onPreInitialize");
    }

    @Override
    public void onSetup(AprismContext context) {
        context.getLogger().info("[ExampleMod] onSetup");
    }

    @Override
    public void onComplete(AprismContext context) {
        context.getLogger().info("[ExampleMod] onComplete");
    }
}

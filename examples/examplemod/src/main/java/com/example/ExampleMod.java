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
        // v26.7-Alpha.1 live proof: register an item; the loader binds it into
        // the real MC registries after the common lifecycle.
        com.aprism.api.registry.ResourceKey key =
                com.aprism.api.registry.ResourceKey.parse("aprism:smoke_ruby");
        context.getItemRegistry().register(key, new com.aprism.api.registry.ItemContent(key, 32));
        log.info("[ExampleMod] registered content aprism:smoke_ruby");
        // v26.7-Alpha.5 live proof: a command bound into the live Brigadier
        // dispatcher when the integrated server exists.
        context.getCommandRegistration().register(new com.aprism.api.commands.CommandSpec(
                "aprism_hello", "smoke proof command", (Runnable) () -> { }));
        log.info("[ExampleMod] registered command aprism_hello");
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

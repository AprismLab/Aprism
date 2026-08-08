package com.example.mixinproof;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Real-game Mixin weaving proof (v26.0-Alpha.9). Injects into the Minecraft
 * client constructor so that, when Aprism's agent transforms the genuine
 * {@code net.minecraft.client.Minecraft} class at load time, a marker line is
 * printed to stdout. The presence of the marker in the live game log is the
 * assertion that Aprism's Mixin path weaves into real Minecraft bytecode.
 *
 * <p>The target is referenced by string so this mixin class has no compile-time
 * dependency on Minecraft (it compiles against Mixin + aprism-api only). The
 * handler captures zero constructor arguments (a valid suffix), so it needs no
 * reference to {@code GameConfig} either.
 *
 * @author BlockConnect@StarsailsClover
 */
@Mixin(targets = "net.minecraft.client.Minecraft")
public class MinecraftMixin {

    /**
     * Injected at the tail of the Minecraft constructor. Prints a unique marker
     * so the harness can assert the weave happened in the running game.
     *
     * @param ci the callback info (unused)
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void aprismMixinProof(CallbackInfo ci) {
        System.out.println("[APRISM-MIXIN-PROOF] woven into net.minecraft.client.Minecraft by Aprism");
    }
}

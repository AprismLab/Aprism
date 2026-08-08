package com.aprism.loader.mixintest;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A genuine SpongePowered {@code @Mixin} injector that targets a
 * <em>dynamically generated</em> class by name ({@code targets =}), not by a
 * compiled class reference. The target is produced at runtime by the test and
 * is never on any classpath, so Mixin's "already loaded too early" check
 * passes and the injection is woven for real.
 *
 * <p>{@code remap = false} because this runs in-process against unobfuscated
 * names (no intermediary mapping).
 *
 * @author BlockConnect@StarsailsClover
 */
@Mixin(targets = "com.aprism.loader.mixintest.gen.GeneratedTarget", remap = false)
public abstract class GeneratedTargetMixin {

    /**
     * Injects at the RETURN of {@code getValue()} and cancels with 42.
     *
     * @param cir the return callback
     */
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void aprism$overrideGetValue(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(42);
    }
}

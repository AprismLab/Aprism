package com.example.mixinproof;

import java.util.logging.Logger;

import com.aprism.api.AprismContext;
import com.aprism.api.IAprismMod;

/**
 * Entry point for the Mixin-weaving proof mod. Its sole job is to log a marker
 * on {@code onInitialize} so the harness can confirm the mod itself loaded; the
 * actual weaving proof is the marker printed by {@link MinecraftMixin}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MixinProofMod implements IAprismMod {

    @Override
    public void onInitialize(AprismContext context) {
        Logger log = context.getLogger();
        log.info("[APRISM-MIXIN-PROOF] mod 'mixinproof' initialized (mixin target: Minecraft)");
    }
}

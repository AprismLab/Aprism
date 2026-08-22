package com.mojang.brigadier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-sourceset stub of Brigadier's dispatcher surface (v26.7-Alpha.2).
 * NOT shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class CommandDispatcher<S> {

    public final Map<String, Object> registered = new LinkedHashMap<>();

    public Object register(com.mojang.brigadier.builder.LiteralArgumentBuilder<S> builder) {
        registered.put(builder.name, builder.command);
        return builder.buildNode();
    }
}

package com.mojang.brigadier;

/**
 * Test-sourceset stub of Brigadier's Command functional interface
 * (v26.7-Alpha.2). NOT shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public interface Command<S> {

    int run(com.mojang.brigadier.context.CommandContext<S> context);
}

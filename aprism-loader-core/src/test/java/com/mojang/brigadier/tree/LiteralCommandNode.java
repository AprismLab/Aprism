package com.mojang.brigadier.tree;

/**
 * Test-sourceset stub of Brigadier's LiteralCommandNode (v26.7-Alpha.2).
 * NOT shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class LiteralCommandNode<S> {

    public final String name;
    public final Object command;

    public LiteralCommandNode(String name, Object command) {
        this.name = name;
        this.command = command;
    }
}

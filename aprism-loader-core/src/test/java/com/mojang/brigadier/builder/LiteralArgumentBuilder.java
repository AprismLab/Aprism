package com.mojang.brigadier.builder;

/**
 * Test-sourceset stub of Brigadier's literal builder (v26.7-Alpha.2).
 * NOT shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class LiteralArgumentBuilder<S> {

    public final String name;
    public Object command;

    protected LiteralArgumentBuilder(String name) {
        this.name = name;
    }

    public static <S> LiteralArgumentBuilder<S> literal(String name) {
        return new LiteralArgumentBuilder<>(name);
    }

    public LiteralArgumentBuilder<S> executes(com.mojang.brigadier.Command<S> command) {
        this.command = command;
        return this;
    }

    @SuppressWarnings("unchecked")
    public com.mojang.brigadier.tree.LiteralCommandNode<S> buildNode() {
        return new com.mojang.brigadier.tree.LiteralCommandNode<>(name, command);
    }
}

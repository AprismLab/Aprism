package com.game;

/**
 * Test stand-in for an obfuscated Minecraft class. In a real pre-26.1 game the
 * class is named {@code com/game/Target} (official) but mods compiled against
 * Intermediary reference it as {@code net/minecraft/class_999}. The remap
 * pipeline bridges the two.
 *
 * @author BlockConnect@StarsailsClover
 */
public class Target {

    /** An official-named field (intermediary: field_999). */
    public int officialField = 42;

    /** An official-named method (intermediary: method_999). */
    public int officialMethod() {
        return officialField;
    }
}

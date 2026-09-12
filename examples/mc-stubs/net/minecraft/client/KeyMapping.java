package net.minecraft.client;

import net.minecraft.resources.Identifier;

/**
 * Test-sourceset stub of MC KeyMapping + Category record (v26.7-Alpha.3).
 * NOT shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class KeyMapping {

    public final String name;
    public final int key;

    public KeyMapping(String name, int key, Category category) {
        this.name = name;
        this.key = key;
    }

    public Category getCategory() {
        return Category.MISC;
    }

    public record Category(Identifier id) {
        public static final Category MISC =
            new Category(Identifier.parse("aprism:misc"));
    }
}
package net.minecraft.core;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-sourceset stub of the MC 26.x Registry surface used by the content
 * binder (v26.7-Alpha.1). NOT shipped in production artifacts.
 */
public class Registry<T> {

    private final Map<Identifier, T> entries = new LinkedHashMap<>();

    public static <V, T extends V> T register(Registry<V> registry, Identifier id, T value) {
        registry.entries.put(id, value);
        return value;
    }

    public T get(Identifier id) {
        return entries.get(id);
    }

    public int size() {
        return entries.size();
    }
}

package com.aprism.loader.registry;

import com.aprism.api.registry.BlockContent;
import com.aprism.api.registry.EntityContent;
import com.aprism.api.registry.ItemContent;
import com.aprism.api.registry.TypedRegistry;

/**
 * Aggregate holder of the typed game-content registries (v26.3-Alpha.2, QA0
 * gap #2). Provides typed access to the block, item, and entity registries
 * so mods register content without downcasting the generic
 * {@code AprismRegistry}.
 *
 * <p>The native game binding (projecting registered content into real
 * Minecraft registries) is delegated to the platform adapter layer and is
 * not part of the loader core.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class GameRegistries {

    private final TypedRegistryImpl<BlockContent> blocks = new TypedRegistryImpl<>();
    private final TypedRegistryImpl<ItemContent> items = new TypedRegistryImpl<>();
    private final TypedRegistryImpl<EntityContent> entities = new TypedRegistryImpl<>();

    /**
     * @return the typed block registry
     */
    public TypedRegistry<BlockContent> blocks() {
        return blocks;
    }

    /**
     * @return the typed item registry
     */
    public TypedRegistry<ItemContent> items() {
        return items;
    }

    /**
     * @return the typed entity registry
     */
    public TypedRegistry<EntityContent> entities() {
        return entities;
    }

    /**
     * Drops all registered content (runtime shutdown).
     */
    public void clear() {
        blocks.clear();
        items.clear();
        entities.clear();
    }
}

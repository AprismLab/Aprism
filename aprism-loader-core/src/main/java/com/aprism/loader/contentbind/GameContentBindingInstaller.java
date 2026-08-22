package com.aprism.loader.contentbind;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.aprism.api.registry.BlockContent;
import com.aprism.api.registry.ItemContent;
import com.aprism.api.registry.ResourceKey;
import com.aprism.loader.registry.GameRegistries;

/**
 * Binds Aprism-native content records into the real Minecraft registries
 * (v26.7-Alpha.1, QA2 content-superset gap).
 *
 * <p>On the NO_REMAP profile (MC 26.1+, unobfuscated) the loader reaches the
 * real registry surface by reflection against official class names:
 * {@code net.minecraft.core.registries.BuiltInRegistries.ITEM} /
 * {@code .BLOCK} plus the static
 * {@code net.minecraft.core.Registry.register(Registry, Identifier, T)}
 * helper. A bound item therefore exists in the live game's item registry.
 *
 * <p>Fail-closed contract: non-NO_REMAP profiles refuse with
 * PROFILE_UNSUPPORTED; missing MC classes/methods refuse with
 * TARGET_UNRESOLVED instead of throwing; per-entry isolation means one
 * failing record never aborts the remaining bindings.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class GameContentBindingInstaller {

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    static final String BUILT_IN_REGISTRIES = "net.minecraft.core.registries.BuiltInRegistries";
    static final String REGISTRY_HELPER = "net.minecraft.core.Registry";
    static final String IDENTIFIER = "net.minecraft.resources.Identifier";
    static final String MC_ITEM = "net.minecraft.world.item.Item";
    static final String MC_ITEM_PROPERTIES = "net.minecraft.world.item.Item$Properties";
    static final String MC_BLOCK = "net.minecraft.world.level.block.Block";
    static final String MC_BLOCK_PROPERTIES =
            "net.minecraft.world.level.block.state.BlockBehaviour$Properties";

    private final GameRegistries gameRegistries;
    private boolean remapProfile;

    /**
     * Outcome of one bind attempt.
     *
     * @param kind    {@code item} or {@code block}
     * @param key     the Aprism resource key that was bound
     * @param ok      whether the binding reached the real registry
     * @param refusal refusal reason when not ok ({@code PROFILE_UNSUPPORTED},
     *                {@code TARGET_UNRESOLVED}, or {@code ENTRY_FAILED})
     */
    public record BindingResult(String kind, ResourceKey key, boolean ok, String refusal) {
    }

    public GameContentBindingInstaller(GameRegistries gameRegistries) {
        this.gameRegistries = Objects.requireNonNull(gameRegistries, "gameRegistries");
    }

    /**
     * Gates binding by remap profile: only the NO_REMAP profile (26.x,
     * unobfuscated) has stable official names to reflect against.
     *
     * @param remapProfile true when the runtime selected a remapped profile
     */
    public void setRemapProfile(boolean remapProfile) {
        this.remapProfile = remapProfile;
    }

    /**
     * Binds every registered item and block into the real registries.
     * Never throws; failures are isolated per entry.
     *
     * @return per-entry results in registration order
     */
    public List<BindingResult> bindAll() {
        List<BindingResult> results = new ArrayList<>();
        List<ItemContent> items = gameRegistries.items().keys().stream()
                .map(k -> gameRegistries.items().get(k).orElseThrow()).toList();
        List<BlockContent> blocks = gameRegistries.blocks().keys().stream()
                .map(k -> gameRegistries.blocks().get(k).orElseThrow()).toList();
        if (remapProfile) {
            for (ItemContent c : items) {
                results.add(new BindingResult("item", c.id(), false, "PROFILE_UNSUPPORTED"));
            }
            for (BlockContent c : blocks) {
                results.add(new BindingResult("block", c.id(), false, "PROFILE_UNSUPPORTED"));
            }
            LOG.fine("GameContentBindingInstaller: refused on remapped profile");
            return results;
        }
        RegistryHandles handles = resolveHandles();
        if (handles == null) {
            for (ItemContent c : items) {
                results.add(new BindingResult("item", c.id(), false, "TARGET_UNRESOLVED"));
            }
            for (BlockContent c : blocks) {
                results.add(new BindingResult("block", c.id(), false, "TARGET_UNRESOLVED"));
            }
            return results;
        }
        for (ItemContent content : items) {
            results.add(bindItem(handles, content));
        }
        for (BlockContent content : blocks) {
            results.add(bindBlock(handles, content));
        }
        long ok = results.stream().filter(BindingResult::ok).count();
        LOG.info("Content binding: " + ok + "/" + results.size()
                + " unit(s) bound to the live registries");
        return results;
    }

    private BindingResult bindItem(RegistryHandles handles, ItemContent content) {
        try {
            Class<?> propertiesClass = handles.itemClass.getClassLoader()
                    .loadClass(MC_ITEM_PROPERTIES);
            Object properties = propertiesClass.getConstructor().newInstance();
            Method stacks = findNoArgReturningMethod(propertiesClass,
                    new String[] {"stacksTo", "maxCount", "maxStackSize"});
            Object propertiesOut = stacks != null && content.maxStack() != 64
                    ? stacks.invoke(properties, content.maxStack())
                    : properties;
            Constructor<?> ctor = handles.itemClass.getConstructor(propertiesClass);
            Object item = ctor.newInstance(propertiesOut);
            return register(handles, "item", content.id(), item);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warning("Failed to bind item '" + content.id().combined() + "': " + e);
            return new BindingResult("item", content.id(), false, "ENTRY_FAILED");
        }
    }

    private BindingResult bindBlock(RegistryHandles handles, BlockContent content) {
        try {
            Class<?> propertiesClass = handles.blockClass.getClassLoader()
                    .loadClass(MC_BLOCK_PROPERTIES);
            Object properties = noArgInstance(propertiesClass);
            if (properties == null) {
                properties = invokeStaticFactory(propertiesClass,
                        new String[] {"create", "of"});
            }
            if (properties == null) {
                return new BindingResult("block", content.id(), false, "TARGET_UNRESOLVED");
            }
            Object block = handles.blockClass.getConstructor(propertiesClass)
                    .newInstance(properties);
            return register(handles, "block", content.id(), block);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warning("Failed to bind block '" + content.id().combined() + "': " + e);
            return new BindingResult("block", content.id(), false, "ENTRY_FAILED");
        }
    }

    private BindingResult register(RegistryHandles handles, String kind, ResourceKey key,
            Object value) {
        try {
            IdentifierFactory ids = handles.identifiers();
            Object identifier = ids.create(key.namespace(), key.name());
            Method register = handles.registryHelper().getMethod(
                    "register", handles.registryClass(), ids.type(), Object.class);
            register.setAccessible(true);
            register.invoke(null, handles.registryFor(kind), identifier, value);
            return new BindingResult(kind, key, true, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warning("Failed to register " + kind + " '" + key.combined() + "': " + e);
            return new BindingResult(kind, key, false, "ENTRY_FAILED");
        }
    }

    private RegistryHandles resolveHandles() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader() != null
                    ? Thread.currentThread().getContextClassLoader()
                    : getClass().getClassLoader();
            Class<?> registries = loader.loadClass(BUILT_IN_REGISTRIES);
            Class<?> registryHelper = loader.loadClass(REGISTRY_HELPER);
            Class<?> registryIface = loader.loadClass("net.minecraft.core.Registry");
            Class<?> identifier = loader.loadClass(IDENTIFIER);
            Field itemField = registries.getField("ITEM");
            Field blockField = registries.getField("BLOCK");
            Object itemRegistry = itemField.get(null);
            Object blockRegistry = blockField.get(null);
            Class<?> itemClass = loader.loadClass(MC_ITEM);
            Class<?> blockClass = loader.loadClass(MC_BLOCK);
            return new RegistryHandles(registryIface, registryHelper, itemRegistry,
                    blockRegistry, itemClass, blockClass,
                    IdentifierFactory.detect(identifier));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.fine("GameContentBindingInstaller: MC registry surface unresolved: " + e);
            return null;
        }
    }

    private static Method findNoArgReturningMethod(Class<?> type, String[] candidates) {
        for (String name : candidates) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            }
        }
        return null;
    }

    private static Object noArgInstance(Class<?> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object invokeStaticFactory(Class<?> type, String[] candidates)
            throws ReflectiveOperationException {
        for (String name : candidates) {
            try {
                Method m = type.getMethod(name);
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(null);
                }
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            }
        }
        return null;
    }

    /** Minimal functional view of the target Identifier class. */
    record IdentifierFactory(Class<?> type, Constructor<?> nsPathCtor, Method parse) {

        static IdentifierFactory detect(Class<?> identifier) throws NoSuchMethodException {
            Constructor<?> ctor = null;
            try {
                ctor = identifier.getConstructor(String.class, String.class);
            } catch (NoSuchMethodException ignored) {
                // fall through to parse-only
            }
            Method parseMeth = null;
            try {
                parseMeth = identifier.getMethod("parse", String.class);
                if (!java.lang.reflect.Modifier.isStatic(parseMeth.getModifiers())) {
                    parseMeth = null;
                }
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
            if (ctor == null && parseMeth == null) {
                throw new NoSuchMethodException("no usable Identifier factory");
            }
            return new IdentifierFactory(identifier, ctor, parseMeth);
        }

        Object create(String namespace, String path) throws ReflectiveOperationException {
            if (nsPathCtor != null) {
                return nsPathCtor.newInstance(namespace, path);
            }
            return parse.invoke(null, namespace + ":" + path);
        }
    }

    private record RegistryHandles(Class<?> registryClass, Class<?> registryHelper,
            Object itemRegistry, Object blockRegistry, Class<?> itemClass,
            Class<?> blockClass, IdentifierFactory identifiers) {

        Object registryFor(String kind) {
            return "block".equals(kind) ? blockRegistry : itemRegistry;
        }
    }
}

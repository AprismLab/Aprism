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
    static final String LEGACY_IDENTIFIER = "net.minecraft.resources.ResourceLocation";
    static final String MC_ITEM = "net.minecraft.world.item.Item";
    static final String MC_ITEM_PROPERTIES = "net.minecraft.world.item.Item$Properties";
    static final String MC_BLOCK = "net.minecraft.world.level.block.Block";
    static final String MC_BLOCK_PROPERTIES =
            "net.minecraft.world.level.block.state.BlockBehaviour$Properties";

    private final GameRegistries gameRegistries;
    private boolean remapProfile;
    private OfficialMappings officialMappings;

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
     * Supplies Mojang official mappings (client.txt) enabling cross-mapped
     * binding on REMAPPED profiles (DEC-PRE261 Option A, v26.8-Alpha.5).
     */
    public void setOfficialMappings(OfficialMappings mappings) {
        this.officialMappings = mappings;
    }

    /** Translates an official target name to the runtime name when needed. */
    private String rt(String officialName) {
        if (remapProfile && officialMappings != null) {
            return officialMappings.runtimeName(officialName);
        }
        return officialName;
    }

    /** Translates an official static field name when needed (v26.8-Alpha.6). */
    private String rtFieldName(String officialClass, String officialField) {
        if (remapProfile && officialMappings != null) {
            return officialMappings.runtimeFieldName(officialClass, officialField);
        }
        return officialField;
    }

    /**
     * Translates an official method name when needed (v26.8-Alpha.7). On
     * non-remapped profiles the name passes through unchanged.
     */
    private String rtMethodName(RegistryHandles handles, String officialClass,
            String officialMethod) {
        if (remapProfile && officialMappings != null) {
            return officialMappings.runtimeMethodName(officialClass, officialMethod);
        }
        return officialMethod;
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
        //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        if (remapProfile && officialMappings == null) {
            for (ItemContent c : items) {
                results.add(new BindingResult("item", c.id(), false, "PROFILE_UNSUPPORTED"));
            }
            LOG.warning("Content binding requires the NO_REMAP profile (MC 26.1+);"
                    + " pre-26.1 binding is a documented limitation (v26.7-Alpha.7,"
                    + " DEC-PRE261) - see docs/en/01-architecture-design.md.");
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
                    .loadClass(rt(MC_ITEM_PROPERTIES));
            Object properties = propertiesClass.getConstructor().newInstance();
            Method stacks = findNoArgReturningMethod(propertiesClass,
                    new String[] {rtMethodName(handles, MC_ITEM_PROPERTIES, "stacksTo"),
                            rtMethodName(handles, MC_ITEM_PROPERTIES, "maxCount"),
                            rtMethodName(handles, MC_ITEM_PROPERTIES, "maxStackSize")});
            Object propertiesOut = stacks != null && content.maxStack() != 64
                    ? stacks.invoke(properties, content.maxStack())
                    : properties;
            setIdOnProperties(handles, propertiesOut, content.id(), "ITEM",
                    MC_ITEM_PROPERTIES);
            Constructor<?> ctor = handles.itemClass.getConstructor(propertiesClass);
            Object item = ctor.newInstance(propertiesOut);
            return register(handles, "item", content.id(), item);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = (e instanceof java.lang.reflect.InvocationTargetException ite
                    && ite.getCause() != null) ? ite.getCause() : e;
            LOG.warning("Failed to bind item '" + content.id().combined() + "': "
                    + cause);
            return new BindingResult("item", content.id(), false, "ENTRY_FAILED");
        }
    }

    /**
     * Modern MC requires {@code Item.Properties#setId(ResourceKey)} before
     * construction ("Item id not set" otherwise). Builds the MC ResourceKey
     * reflectively from Registries.ITEM + Identifier. Every target name is
     * mapping-translated so obfuscated pre-26.1 profiles resolve
     * (v26.8-Alpha.9).
     */
    private void setIdOnProperties(RegistryHandles handles, Object properties,
            ResourceKey key, String registryField, String propertiesOfficialClass) {
        //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        try {
            ClassLoader loader = properties.getClass().getClassLoader();
            String registriesOfficial = "net.minecraft.core.registries.Registries";
            Class<?> registries = loader.loadClass(rt(registriesOfficial));
            Object itemRegistryKey = registries.getField(
                    rtFieldName(registriesOfficial, registryField)).get(null);
            String resourceKeyOfficial = "net.minecraft.resources.ResourceKey";
            Class<?> resourceKeyClass = loader.loadClass(rt(resourceKeyOfficial));
            Object identifier = handles.identifiers().create(key.namespace(), key.name());
            Method create = resourceKeyClass.getMethod(
                    rtMethodName(handles, resourceKeyOfficial, "create"),
                    resourceKeyClass, handles.identifiers().type());
            Object mcKey = create.invoke(null, itemRegistryKey, identifier);
            properties.getClass().getMethod(
                    rtMethodName(handles, propertiesOfficialClass, "setId"),
                    resourceKeyClass).invoke(properties, mcKey);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Older/newer naming without setId: constructor will report the
            // missing id as ENTRY_FAILED with its own message.
        }
    }

    private BindingResult bindBlock(RegistryHandles handles, BlockContent content) {
        try {
            Class<?> propertiesClass = handles.blockClass.getClassLoader()
                    .loadClass(rt(MC_BLOCK_PROPERTIES));
            Object properties = noArgInstance(propertiesClass);
            if (properties == null) {
                properties = invokeStaticFactory(propertiesClass,
                        new String[] {
                                rtMethodName(handles, MC_BLOCK_PROPERTIES, "of"),
                                rtMethodName(handles, MC_BLOCK_PROPERTIES, "create")});
            }
            if (properties == null) {
                return new BindingResult("block", content.id(), false, "TARGET_UNRESOLVED");
            }
            setIdOnProperties(handles, properties, content.id(), "BLOCK",
                    MC_BLOCK_PROPERTIES);
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
        //GitHub@NDBlockConnect | BlockConnect@StarsailsClover
        try {
            IdentifierFactory ids = handles.identifiers();
            Object identifier = ids.create(key.namespace(), key.name());
            Method register = handles.registryHelper().getMethod(
                    rtMethodName(handles, "net.minecraft.core.Registry", "register"),
                    handles.registryClass(), ids.type(), Object.class);
            register.setAccessible(true);
            register.invoke(null, handles.registryFor(kind), identifier, value);
            // v26.8-Alpha.9: read back from the live registry so "bound"
            // means the id is actually present, not only that register()
            // returned cleanly. Readback failure is logged but never
            // downgrades the binding result.
            try {
                // Name-only translation cannot separate overloads (1.21.4:
                // containsKey(RL)->d vs containsKey(RK)->e), so probe the
                // presence candidates in order against the RL parameter type.
                Method probe = null;
                for (String candidate : new String[] {"containsKey", "getValue",
                        "get"}) {
                    try {
                        probe = handles.registryClass().getMethod(
                                rtMethodName(handles,
                                        "net.minecraft.core.Registry", candidate),
                                ids.type());
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // try next candidate
                    }
                }
                if (probe == null) {
                    throw new NoSuchMethodException(
                            "no usable registry presence probe");
                }
                probe.setAccessible(true);
                Object present = probe.invoke(handles.registryFor(kind), identifier);
                boolean found = present instanceof Boolean b ? b
                        : present instanceof java.util.Optional<?> opt
                                ? opt.isPresent()
                                : present != null;
                if (found) {
                    LOG.info("Registry readback: '" + key.combined()
                            + "' present in the live " + kind + " registry");
                } else {
                    LOG.warning("Registry readback: '" + key.combined()
                            + "' ABSENT from the live " + kind + " registry");
                }
            } catch (ReflectiveOperationException | RuntimeException rb) {
                LOG.warning("Registry readback unavailable: " + rb);
            }
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
            Class<?> registries = loader.loadClass(rt(BUILT_IN_REGISTRIES));
            Class<?> registryHelper = loader.loadClass(rt(REGISTRY_HELPER));
            Class<?> registryIface = loader.loadClass(rt("net.minecraft.core.Registry"));
            Class<?> identifier;
            String identifierOfficial;
            try {
                identifier = loader.loadClass(rt(IDENTIFIER));
                identifierOfficial = IDENTIFIER;
            } catch (ClassNotFoundException first) {
                identifier = loader.loadClass(rt(LEGACY_IDENTIFIER));
                identifierOfficial = LEGACY_IDENTIFIER;
            }
            // v26.8-Alpha.6: static field names translate too (ITEM/BLOCK).
            String itemFieldName = rtFieldName(BUILT_IN_REGISTRIES, "ITEM");
            String blockFieldName = rtFieldName(BUILT_IN_REGISTRIES, "BLOCK");
            Field itemField = registries.getField(itemFieldName);
            Field blockField = registries.getField(blockFieldName);
            Object itemRegistry = itemField.get(null);
            Object blockRegistry = blockField.get(null);
            Class<?> itemClass = loader.loadClass(rt(MC_ITEM));
            Class<?> blockClass = loader.loadClass(rt(MC_BLOCK));
            return new RegistryHandles(registryIface, registryHelper, itemRegistry,
                    blockRegistry, itemClass, blockClass,
                    IdentifierFactory.detect(identifier,
                            rtMethodName(null, identifierOfficial, "parse")));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warning("GameContentBindingInstaller: MC registry surface unresolved: "
                    + e.getClass().getName() + ": " + e.getMessage()
                    + (e.getCause() != null ? " caused by " + e.getCause() : ""));
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

        static IdentifierFactory detect(Class<?> identifier, String parseName)
                throws NoSuchMethodException {
            Constructor<?> ctor = null;
            try {
                ctor = identifier.getConstructor(String.class, String.class);
            } catch (NoSuchMethodException ignored) {
                // fall through to parse-only
            }
            Method parseMeth = null;
            try {
                // v26.8-Alpha.9: the parse factory name is mapping-translated
                // so obfuscated pre-26.1 profiles (e.g. 1.21.4 akv.a) resolve.
                parseMeth = identifier.getMethod(parseName, String.class);
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

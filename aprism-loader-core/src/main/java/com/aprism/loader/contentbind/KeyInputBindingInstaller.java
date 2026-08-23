package com.aprism.loader.contentbind;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.aprism.api.keybinding.KeyBindingRegistry;
import com.aprism.api.keybinding.KeyBindingSpec;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Binds registered Aprism key bindings into the live Minecraft input system
 * (v26.7-Alpha.3).
 *
 * <p>Target surface (official 26.x client names): {@code Minecraft
 * .getInstance()} is statically reachable once the client exists; its
 * {@code options} field exposes {@code Options#keyMappings}. Each
 * {@link KeyBindingSpec} becomes a {@code net.minecraft.client.KeyMapping}
 * constructed through its documented constructor and appended to that array
 * reflectively.
 *
 * <p>Fail-closed contract: no live client -> NO_CLIENT refusal for every
 * spec; remapped profiles refuse outright; per-entry isolation; never
 * throws into the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class KeyInputBindingInstaller {
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    private static final String MC_MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String MC_KEY_MAPPING = "net.minecraft.client.KeyMapping";
    private static final String MC_CATEGORY = "net.minecraft.client.KeyMapping$Category";

    private final KeyBindingRegistry registry;
    private boolean remapProfile;
    private Object minecraftInstance;

    /**
     * Outcome of one bind attempt.
     *
     * @param id      the binding id
     * @param ok      whether the mapping reached the live input system
     * @param refusal refusal reason when not ok ({@code PROFILE_UNSUPPORTED},
     *                {@code NO_CLIENT}, {@code ENTRY_FAILED})
     */
    public record BindResult(String id, boolean ok, String refusal) {
    }

    public KeyInputBindingInstaller(KeyBindingRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Gates binding on the remap profile (mirrors the other binders). */
    public void setRemapProfile(boolean remapProfile) {
        this.remapProfile = remapProfile;
    }

    /**
     * Supplies the live client instance ({@code Minecraft.getInstance()}
     * result). Null detaches; binds before attachment refuse.
     *
     * @param minecraftInstance the live client instance
     */
    public void attachClient(Object minecraftInstance) {
        this.minecraftInstance = minecraftInstance;
    }

    /**
     * Attempts to auto-discover the live client via
     * {@code Minecraft.getInstance()}.
     *
     * @return true when a client was discovered and attached
     */
    public boolean discoverClient() {
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> mc = loader.loadClass(MC_MINECRAFT);
            Object instance = mc.getMethod("getInstance").invoke(null);
            if (instance != null) {
                attachClient(instance);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // dedicated server / non-client host: stay detached
        }
        return false;
    }

    /**
     * Binds every frozen key-binding spec. Never throws.
     */
    public List<BindResult> bindAll() {
        List<BindResult> results = new ArrayList<>();
        List<KeyBindingSpec> specs = registry.registeredKeyBindings();
        if (remapProfile) {
            for (KeyBindingSpec s : specs) {
                results.add(new BindResult(s.id(), false, "PROFILE_UNSUPPORTED"));
            }
            return results;
        }
        Object client = minecraftInstance;
        if (client == null && !discoverClient()) {
            for (KeyBindingSpec s : specs) {
                results.add(new BindResult(s.id(), false, "NO_CLIENT"));
            }
            return results;
        }
        client = minecraftInstance;
        try {
            Object options = client.getClass().getField("options").get(client);
            Object[] mappings = currentMappings(options);
            List<Object> appended = new ArrayList<>();
            for (KeyBindingSpec spec : specs) {
                try {
                    appended.add(buildMapping(spec, options));
                    results.add(new BindResult(spec.id(), true, null));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Throwable cause = (e instanceof java.lang.reflect.InvocationTargetException ite
                            && ite.getCause() != null) ? ite.getCause() : e;
                    LOG.warning("Failed to bind key '" + spec.id() + "': " + cause);
                    results.add(new BindResult(spec.id(), false, "ENTRY_FAILED"));
                }
            }
            writeMappings(options, mappings, appended);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warning("Failed to access live key mappings: " + e);
            results.replaceAll(r -> r.ok() ? r : new BindResult(r.id(), false, "ENTRY_FAILED"));
        }
        long ok = results.stream().filter(BindResult::ok).count();
        LOG.info("Key binding: " + ok + "/" + results.size() + " bound");
        return results;
    }
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static Object[] currentMappings(Object options)
            throws ReflectiveOperationException {
        Object all = options.getClass().getField("keyMappings").get(options);
        return (Object[]) all;
    }

    @SuppressWarnings("unchecked")
    private static void writeMappings(Object options, Object[] existing,
            List<Object> appended) throws ReflectiveOperationException {
        Object[] merged = (Object[]) Array.newInstance(
                existing.getClass().getComponentType(),
                existing.length + appended.size());
        System.arraycopy(existing, 0, merged, 0, existing.length);
        for (int i = 0; i < appended.size(); i++) {
            merged[existing.length + i] = appended.get(i);
        }
        java.lang.reflect.Field kmField = options.getClass().getField("keyMappings");
        kmField.setAccessible(true);
        kmField.set(options, merged);
    }

    private Object buildMapping(KeyBindingSpec spec, Object options)
            throws ReflectiveOperationException {
        ClassLoader loader = options.getClass().getClassLoader();
        Class<?> mappingClass = loader.loadClass(MC_KEY_MAPPING);
        Class<?> categoryClass = loader.loadClass(MC_CATEGORY);
        Object category = resolveCategory(categoryClass);
        // Prefer the simple (name, defaultKey, category) constructor; GLFW
        // keycode convention matches KeyBindingSpec.defaultKeyCode().
        Constructor<?> ctor = mappingClass.getConstructor(
                String.class, int.class, categoryClass);
        return ctor.newInstance(spec.id(), spec.defaultKeyCode(), category);
    }

    /**
     * Resolves the permissive category: the static MISC constant when present
     * (26.x record), else any declared constant, else constructs one from an
     * Identifier.
     */
    private static Object resolveCategory(Class<?> categoryClass)
            throws ReflectiveOperationException {
        try {
            return categoryClass.getField("MISC").get(null);
        } catch (NoSuchFieldException ignored) {
            // fall through
        }
        if (categoryClass.isEnum()) {
            Object[] constants = categoryClass.getEnumConstants();
            if (constants.length > 0) {
                return constants[0];
            }
        }
        Class<?> identifier = categoryClass.getClassLoader()
                .loadClass("net.minecraft.resources.Identifier");
        Object id;
        try {
            id = identifier.getConstructor(String.class, String.class)
                    .newInstance("aprism", "misc");
        } catch (NoSuchMethodException e) {
            id = identifier.getMethod("parse", String.class)
                    .invoke(null, "aprism:misc");
        }
        return categoryClass.getConstructor(identifier).newInstance(id);
    }
}

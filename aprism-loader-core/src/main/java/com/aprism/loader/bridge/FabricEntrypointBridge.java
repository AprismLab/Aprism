package com.aprism.loader.bridge;

import java.lang.reflect.Method;

import com.aprism.api.AprismPhase;

/**
 * Bridges Fabric loader entrypoint conventions onto Aprism phase dispatch.
 *
 * <p>Fabric mods declare entrypoints whose classes implement Fabric's own
 * interfaces rather than {@link com.aprism.api.IAprismMod}:
 *
 * <ul>
 *   <li>{@code main} → {@code ModInitializer.onInitialize()} (no args)</li>
 *   <li>{@code client} → {@code ClientModInitializer.onInitializeClient()}</li>
 *   <li>{@code server} → {@code DedicatedServerModInitializer.onInitializeServer()}</li>
 * </ul>
 *
 * <p>Aprism replaces the Fabric Loader at runtime, so this bridge invokes those
 * no-arg entrypoint methods reflectively. The lookup is interface-agnostic: it
 * searches for the conventional method name on the entrypoint instance rather
 * than casting to Fabric types, so it works whether the Fabric API classes are
 * present on the classpath or supplied by an embedded loader-support extension.
 *
 * <p>Only the {@link AprismPhase#INIT}, {@link AprismPhase#CLIENT}, and
 * {@link AprismPhase#SERVER} phases map to Fabric entrypoints; Fabric has no
 * PREINIT/SETUP/COMPLETE equivalents, so those phases are no-ops for Fabric
 * mods.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FabricEntrypointBridge {

    /** The no-arg method name for the Fabric {@code main} entrypoint. */
    public static final String MAIN_METHOD = "onInitialize";
    /** The no-arg method name for the Fabric {@code client} entrypoint. */
    public static final String CLIENT_METHOD = "onInitializeClient";
    /** The no-arg method name for the Fabric {@code server} entrypoint. */
    public static final String SERVER_METHOD = "onInitializeServer";

    private FabricEntrypointBridge() {
    }

    /**
     * Maps an Aprism phase to the Fabric entrypoint method name to invoke.
     *
     * @param phase the Aprism lifecycle phase
     * @return the Fabric method name, or {@code null} if the phase has no
     *         Fabric equivalent
     */
    public static String methodNameFor(AprismPhase phase) {
        return switch (phase) {
            case INIT -> MAIN_METHOD;
            case CLIENT -> CLIENT_METHOD;
            case SERVER -> SERVER_METHOD;
            case PREINIT, SETUP, COMPLETE -> null;
        };
    }

    /**
     * Invokes the Fabric entrypoint method appropriate to the phase on the
     * given entrypoint instance.
     *
     * @param instance the instantiated Fabric entrypoint object
     * @param phase    the Aprism lifecycle phase
     * @return {@code true} if a Fabric entrypoint method was found and invoked,
     *         {@code false} if the phase has no Fabric equivalent
     */
    public static boolean invoke(Object instance, AprismPhase phase) {
        String methodName = methodNameFor(phase);
        if (methodName == null) {
            return false;
        }
        Method method = findNoArgMethod(instance.getClass(), methodName);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(instance);
            return true;
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Fabric entrypoint " + methodName
                    + " failed on " + instance.getClass().getName(), cause);
        }
    }

    /**
     * Finds a no-arg method with the given name on the class or any of its
     * superclasses/interfaces.
     *
     * @param clazz      the entrypoint class
     * @param methodName the method name to look up
     * @return the method, or {@code null} if not found
     */
    private static Method findNoArgMethod(Class<?> clazz, String methodName) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                // try interfaces of this class
                for (Class<?> iface : c.getInterfaces()) {
                    Method m = findInInterface(iface, methodName);
                    if (m != null) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Searches an interface (and its super-interfaces) for the no-arg method.
     *
     * @param iface      the interface
     * @param methodName the method name
     * @return the method, or {@code null} if not found
     */
    private static Method findInInterface(Class<?> iface, String methodName) {
        try {
            return iface.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
            for (Class<?> parent : iface.getInterfaces()) {
                Method m = findInInterface(parent, methodName);
                if (m != null) {
                    return m;
                }
            }
            return null;
        }
    }
}

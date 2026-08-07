package com.aprism.loader.remap;

/**
 * Service provider interface for class/member name remapping. Implementations
 * translate identifiers between naming namespaces (e.g. Fabric Intermediary
 * names and the official runtime names of a pre-26.1 Minecraft build).
 *
 * <p>All names are in internal (slashed) form, e.g.
 * {@code net/minecraft/class_310}. Descriptors use JVM descriptor syntax, e.g.
 * {@code (Lnet/minecraft/class_123;)V}.
 *
 * <p>Implementations must never throw: unresolvable names are returned
 * unchanged so that unmapped identifiers pass through untouched.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface Remapper {

    /**
     * Maps a class name. Returns the original name when no mapping exists.
     *
     * @param internalName the slashed class name
     * @return the mapped name, or {@code internalName} if unmapped
     */
    String mapClassName(String internalName);

    /**
     * Maps a method name. Returns the original name when no mapping exists.
     *
     * @param owner      the slashed owner class name (source namespace)
     * @param name       the method name (source namespace)
     * @param descriptor the method descriptor (source namespace)
     * @return the mapped name, or {@code name} if unmapped
     */
    String mapMethodName(String owner, String name, String descriptor);

    /**
     * Maps a field name. Returns the original name when no mapping exists.
     *
     * @param owner      the slashed owner class name (source namespace)
     * @param name       the field name (source namespace)
     * @param descriptor the field descriptor (source namespace)
     * @return the mapped name, or {@code name} if unmapped
     */
    String mapFieldName(String owner, String name, String descriptor);

    /**
     * Maps every class reference inside a JVM type descriptor (method
     * descriptors, field descriptors). Primitive types pass through
     * unchanged; object types are mapped via {@link #mapClassName}.
     *
     * @param descriptor the descriptor in the source namespace
     * @return the mapped descriptor
     */
    String mapDesc(String descriptor);

    /**
     * @return a remapper that maps everything to itself (used by the
     *         no-remap profile for Minecraft 26.1+)
     */
    static Remapper noop() {
        return NoopRemapper.INSTANCE;
    }

    /**
     * Identity remapper for the no-remap profile.
     */
    final class NoopRemapper implements Remapper {

        static final NoopRemapper INSTANCE = new NoopRemapper();

        private NoopRemapper() {
        }

        @Override
        public String mapClassName(String internalName) {
            return internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return name;
        }

        @Override
        public String mapDesc(String descriptor) {
            return descriptor;
        }
    }
}

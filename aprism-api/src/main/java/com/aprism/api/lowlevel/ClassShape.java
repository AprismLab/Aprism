package com.aprism.api.lowlevel;

import java.util.List;

/**
 * A structural snapshot of a class file parsed from bytecode
 * (v26.4-Alpha.3, deep bytecode-hook API). Gives mods a typed view of a
 * class's shape — name, superclass, interfaces, access flags, methods and
 * fields — without requiring mods to depend on a bytecode library
 * themselves.
 *
 * <p>All names are in slashed binary form (e.g. {@code java/lang/String});
 * method and field descriptors are JVM descriptor strings.
 *
 * @param className the slashed binary class name
 * @param superName the slashed superclass name ({@code null} for
 *                  {@code java/lang/Object})
 * @param interfaces the slashed interface names
 * @param access the class access flags ({@code Opcodes} bitmask)
 * @param methods the declared methods
 * @param fields the declared fields
 * @author BlockConnect@StarsailsClover
 */
public record ClassShape(
        String className,
        String superName,
        List<String> interfaces,
        int access,
        List<MethodShape> methods,
        List<FieldShape> fields) {

    /**
     * A declared method of the class.
     *
     * @param name the method name
     * @param descriptor the JVM method descriptor
     * @param access the method access flags
     */
    public record MethodShape(String name, String descriptor, int access) {

        /**
         * Canonical compact constructor: validates name and descriptor.
         */
        public MethodShape {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("method name must be non-blank");
            }
            if (descriptor == null || descriptor.isBlank()) {
                throw new IllegalArgumentException("method descriptor must be non-blank");
            }
        }

        /**
         * @return the hook key form {@code name+descriptor} used by the
         *         method-hook registry
         */
        public String hookForm() {
            return name + descriptor;
        }
    }

    /**
     * A declared field of the class.
     *
     * @param name the field name
     * @param descriptor the JVM field descriptor
     * @param access the field access flags
     */
    public record FieldShape(String name, String descriptor, int access) {

        /**
         * Canonical compact constructor: validates name and descriptor.
         */
        public FieldShape {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("field name must be non-blank");
            }
            if (descriptor == null || descriptor.isBlank()) {
                throw new IllegalArgumentException("field descriptor must be non-blank");
            }
        }
    }

    /**
     * Canonical compact constructor: defensive copies and validation.
     */
    public ClassShape {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("class name must be non-blank");
        }
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        methods = methods == null ? List.of() : List.copyOf(methods);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /**
     * @param name the method name
     * @param descriptor the method descriptor
     * @return whether a method with the exact name and descriptor is
     *         declared
     */
    public boolean declaresMethod(String name, String descriptor) {
        for (MethodShape method : methods) {
            if (method.name().equals(name) && method.descriptor().equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name the field name
     * @return whether a field with the exact name is declared
     */
    public boolean declaresField(String name) {
        for (FieldShape field : fields) {
            if (field.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}

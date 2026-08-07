package com.aprism.loader.remap;

/**
 * {@link Remapper} implementation backed by parsed {@link TinyMappings}.
 * Remaps in one of two directions:
 *
 * <ul>
 *   <li><b>FORWARD</b> (namespace 0 → namespace 1), e.g. official → intermediary</li>
 *   <li><b>REVERSE</b> (namespace 1 → namespace 0), e.g. intermediary → official</li>
 * </ul>
 *
 * <p>The reverse member lookup is subtle: tiny v2 stores member descriptors
 * only in namespace 0, so a reverse lookup first translates the incoming
 * descriptor (namespace 1) to namespace 0 through the class map, then
 * queries the reverse index.
 *
 * <p>All lookups are O(1). Unresolvable names pass through unchanged.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class TinyRemapper implements Remapper {

    /** Remap direction. */
    public enum Direction {
        /** namespace 0 → namespace 1 (e.g. official → intermediary). */
        FORWARD,
        /** namespace 1 → namespace 0 (e.g. intermediary → official). */
        REVERSE
    }

    private final TinyMappings mappings;
    private final Direction direction;

    private TinyRemapper(TinyMappings mappings, Direction direction) {
        this.mappings = mappings;
        this.direction = direction;
    }

    /**
     * Creates a remapper from namespace 0 to namespace 1.
     *
     * @param mappings the parsed mappings
     * @return the forward remapper
     */
    public static TinyRemapper officialToIntermediary(TinyMappings mappings) {
        return new TinyRemapper(mappings, Direction.FORWARD);
    }

    /**
     * Creates a remapper from namespace 1 to namespace 0.
     *
     * @param mappings the parsed mappings
     * @return the reverse remapper
     */
    public static TinyRemapper intermediaryToOfficial(TinyMappings mappings) {
        return new TinyRemapper(mappings, Direction.REVERSE);
    }

    /**
     * @return the direction of this remapper
     */
    public Direction direction() {
        return direction;
    }

    @Override
    public String mapClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return internalName;
        }
        String mapped = direction == Direction.FORWARD
                ? mappings.classNamedOf(internalName)
                : mappings.classIntermediaryOf(internalName);
        return mapped != null ? mapped : internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (descriptor == null || "<init>".equals(name) || "<clinit>".equals(name)) {
            return name;
        }
        String mapped;
        if (direction == Direction.FORWARD) {
            mapped = mappings.methodNamedOf(owner, descriptor, name);
        } else {
            // Desc arrives in namespace 1; the reverse index is keyed by the
            // namespace-0 descriptor, so translate it first.
            String desc0 = translateDescriptor(descriptor, Direction.REVERSE);
            mapped = mappings.methodIntermediaryOf(owner, desc0, name);
        }
        return mapped != null ? mapped : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        String mapped;
        if (direction == Direction.FORWARD) {
            mapped = mappings.fieldNamedOf(owner, descriptor, name);
        } else {
            String desc0 = translateDescriptor(descriptor, Direction.REVERSE);
            mapped = mappings.fieldIntermediaryOf(owner, desc0, name);
        }
        return mapped != null ? mapped : name;
    }

    @Override
    public String mapDesc(String descriptor) {
        return translateDescriptor(descriptor, direction);
    }

    /**
     * Maps every object-type reference inside a JVM descriptor. Primitives
     * and array dimensions pass through.
     *
     * @param descriptor the descriptor in the source namespace
     * @param dir        the direction to apply
     * @return the translated descriptor
     */
    private String translateDescriptor(String descriptor, Direction dir) {
        if (descriptor == null || descriptor.isEmpty()) {
            return descriptor;
        }
        StringBuilder out = new StringBuilder(descriptor.length() + 16);
        int i = 0;
        int len = descriptor.length();
        while (i < len) {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'L' -> {
                    int end = descriptor.indexOf(';', i);
                    if (end < 0) {
                        // malformed descriptor: copy the rest verbatim
                        out.append(descriptor, i, len);
                        return out.toString();
                    }
                    String internal = descriptor.substring(i + 1, end);
                    String mapped = dir == Direction.FORWARD
                            ? mappings.classNamedOf(internal)
                            : mappings.classIntermediaryOf(internal);
                    out.append('L').append(mapped != null ? mapped : internal).append(';');
                    i = end + 1;
                }
                case '[' -> {
                    out.append('[');
                    i++;
                }
                default -> {
                    // primitives (B C D F I J S Z V) and anything else: copy
                    out.append(c);
                    i++;
                }
            }
        }
        return out.toString();
    }
}

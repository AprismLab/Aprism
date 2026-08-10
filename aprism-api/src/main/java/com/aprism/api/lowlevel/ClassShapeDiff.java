package com.aprism.api.lowlevel;

import java.util.List;

/**
 * A structural diff between two class shapes (v26.4-Alpha.3, deep
 * bytecode-hook API). Supports the "validate before redefine" workflow:
 * before redefining a loaded class, mods can compare the live shape
 * against the proposed bytes and inspect exactly what would change.
 *
 * @param addedMethods methods present only in the new shape
 * @param removedMethods methods present only in the old shape
 * @param addedFields fields present only in the new shape
 * @param removedFields fields present only in the old shape
 * @param superclassChanged whether the superclass differs
 * @param interfacesChanged whether the interface set differs
 * @author BlockConnect@StarsailsClover
 */
public record ClassShapeDiff(
        List<String> addedMethods,
        List<String> removedMethods,
        List<String> addedFields,
        List<String> removedFields,
        boolean superclassChanged,
        boolean interfacesChanged) {

    /**
     * Canonical compact constructor: defensive copies.
     */
    public ClassShapeDiff {
        addedMethods = addedMethods == null ? List.of() : List.copyOf(addedMethods);
        removedMethods = removedMethods == null ? List.of() : List.copyOf(removedMethods);
        addedFields = addedFields == null ? List.of() : List.copyOf(addedFields);
        removedFields = removedFields == null ? List.of() : List.copyOf(removedFields);
    }

    /**
     * @return whether the two shapes are structurally identical (no
     *         methods, fields, superclass, or interface changes)
     */
    public boolean isEmpty() {
        return addedMethods.isEmpty() && removedMethods.isEmpty()
                && addedFields.isEmpty() && removedFields.isEmpty()
                && !superclassChanged && !interfacesChanged;
    }

    /**
     * @return whether the diff contains any structural change that stock
     *         {@code Instrumentation.redefineClasses} cannot perform
     *         (added/removed fields or methods, or hierarchy changes)
     */
    public boolean isStructural() {
        return !addedMethods.isEmpty() || !removedMethods.isEmpty()
                || !addedFields.isEmpty() || !removedFields.isEmpty()
                || superclassChanged || interfacesChanged;
    }
}

package com.aprism.api.foreignlang;

import java.util.List;

/**
 * The signature of a foreign function as seen across the language boundary
 * (v26.4-Alpha.8, Cpp2Java / Rust2Java reference). Signatures are
 * expressed entirely in {@link ForeignType} terms, the shared ABI-mapping
 * vocabulary of the AprismJDK design (§6).
 *
 * @param name the function name as exported by the native side
 * @param parameterTypes the parameter types in declaration order
 * @param returnType the return type ({@link ForeignType#VOID} for
 *                   procedures)
 * @author BlockConnect@StarsailsClover
 */
public record ForeignSignature(String name, List<ForeignType> parameterTypes,
                               ForeignType returnType) {

    /**
     * Canonical compact constructor: defensive copies and validation.
     */
    public ForeignSignature {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function name must be non-blank");
        }
        if (returnType == null) {
            throw new IllegalArgumentException("return type must be non-null");
        }
        parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        for (ForeignType parameter : parameterTypes) {
            if (parameter == null || !parameter.isParameterType()) {
                throw new IllegalArgumentException("invalid parameter type: " + parameter);
            }
        }
    }

    /**
     * @return the number of parameters
     */
    public int arity() {
        return parameterTypes.size();
    }
}

package com.aprism.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an Aprism mod entrypoint. When the mod manifest does not
 * declare an explicit {@code entrypoints} map, the loader scans the mod's
 * embedded jar(s) for classes annotated with {@code @AprismMod} and uses them
 * as the {@code main} entrypoint.
 *
 * <p>The annotated class must implement {@link IAprismMod}. The optional
 * {@link #value()} specifies the mod id; when present it must match the
 * {@code id} field in {@code aprism.manifest.json}. When absent, any
 * {@code @AprismMod} class in the mod's jar is accepted.
 *
 * <p>This annotation is {@link RetentionPolicy#RUNTIME runtime-visible} so the
 * scanner can read it without loading the class (via ASM), and also so
 * reflection-based tools can discover mod classes after load.
 *
 * @author BlockConnect@StarsailsClover
 * @since v26.5-Alpha.1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AprismMod {

    /**
     * The mod id this entrypoint belongs to. When non-empty, the scanner
     * verifies it matches the manifest id. When empty (default), the
     * entrypoint is accepted for the containing mod unconditionally.
     *
     * @return the mod id, or empty string to accept unconditionally
     */
    String value() default "";
}

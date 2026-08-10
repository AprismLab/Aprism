package net.minecraftforge.fml.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forge API shim: the {@code @Mod} annotation that marks a Forge mod's
 * entrypoint class. Provided by Aprism so that genuine Forge mods can be
 * discovered and instantiated without the real Forge/FML runtime on the
 * classpath.
 *
 * <p>Mirrors the Forge {@code net.minecraftforge.fml.common.Mod} contract: the
 * annotation {@link #value()} must match one of the mod ids declared in the
 * mod's {@code META-INF/mods.toml}. Forge entrypoints are NOT declared in the
 * manifest; they are discovered by scanning classes for this annotation. The
 * annotated class's constructor IS the mod initialization (optionally accepting
 * an {@code IEventBus}).
 *
 * @author BlockConnect@StarsailsClover
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Mod {

    /**
     * The mod id this entrypoint class belongs to. Must match one of the mod
     * ids in {@code META-INF/mods.toml}.
     *
     * @return the mod id
     */
    String value();
}

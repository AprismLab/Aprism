package com.aprism.loader.lowlevel;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.logging.Logger;

/**
 * Runtime class redefinition built on {@link Instrumentation}. This is the
 * lower-level capability that MCJEBooster-style engines use to re-shape
 * already-loaded Minecraft classes (e.g. the server tick loop) after the JVM
 * has started.
 *
 * <p>Part of the v26.1-Alpha.8 lower-level API foundation (goal #2). Two
 * operations are exposed:
 * <ul>
 *   <li>{@link #redefine(Class, byte[])} — replace the bytecode of an
 *       already-loaded class. The class must be modifiable
 *       ({@link Instrumentation#isModifiableClass(Class)}).</li>
 *   <li>{@link #retransform(Class[])} — re-run all registered
 *       {@code ClassFileTransformer}s over already-loaded classes so that
 *       late-registered Mixins or hooks take effect without a restart.</li>
 * </ul>
 *
 * <p>All operations are fault-tolerant: failures are logged and reported via
 * the return value rather than thrown, so a bad redefinition never crashes
 * the host game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ClassRedefiner {

    private static final Logger LOG = Logger.getLogger("aprism.lowlevel");

    private final Instrumentation instrumentation;

    /**
     * @param instrumentation the instrumentation handle from the agent
     *                        premain/agentmain entry
     */
    public ClassRedefiner(Instrumentation instrumentation) {
        if (instrumentation == null) {
            throw new IllegalArgumentException("instrumentation must not be null");
        }
        this.instrumentation = instrumentation;
    }

    /**
     * Whether the instrumentation handle supports class redefinition at all.
     *
     * @return true when {@link Instrumentation#isRedefineClassesSupported()}
     */
    public boolean isRedefineSupported() {
        return instrumentation.isRedefineClassesSupported();
    }

    /**
     * Whether the instrumentation handle supports retransformation.
     *
     * @return true when {@link Instrumentation#isRetransformClassesSupported()}
     */
    public boolean isRetransformSupported() {
        return instrumentation.isRetransformClassesSupported();
    }

    /**
     * Redefines the bytecode of an already-loaded class.
     *
     * <p>The target class must already be loaded and modifiable. If
     * redefinition is unsupported or the class is not modifiable, the call is
     * logged and returns {@code false} without throwing.
     *
     * @param target    the loaded class to redefine
     * @param newBytes  the replacement class-file bytes
     * @return true when the redefinition succeeded
     */
    public boolean redefine(Class<?> target, byte[] newBytes) {
        if (target == null || newBytes == null || newBytes.length == 0) {
            return false;
        }
        if (!isRedefineSupported()) {
            LOG.warning("Class redefinition not supported by this JVM; cannot redefine "
                    + target.getName());
            return false;
        }
        if (!instrumentation.isModifiableClass(target)) {
            LOG.warning("Class " + target.getName() + " is not modifiable; redefinition skipped");
            return false;
        }
        try {
            instrumentation.redefineClasses(new ClassDefinition(target, newBytes));
            LOG.info("Redefined class " + target.getName());
            return true;
        } catch (ClassNotFoundException | UnmodifiableClassException e) {
            LOG.warning("Failed to redefine " + target.getName() + ": " + e);
            return false;
        }
    }

    /**
     * Re-runs all registered class-file transformers over the given loaded
     * classes so that late-registered Mixins, access wideners, or method hooks
     * take effect without restarting the JVM.
     *
     * <p>Classes that are not retransformable are skipped with a log message.
     *
     * @param targets the loaded classes to retransform
     * @return the number of classes actually retransformed
     */
    public int retransform(Class<?>... targets) {
        if (targets == null || targets.length == 0) {
            return 0;
        }
        if (!isRetransformSupported()) {
            LOG.warning("Retransformation not supported by this JVM");
            return 0;
        }
        int done = 0;
        for (Class<?> target : targets) {
            if (target == null) {
                continue;
            }
            if (!instrumentation.isModifiableClass(target)) {
                LOG.warning("Class " + target.getName() + " is not retransformable; skipped");
                continue;
            }
            try {
                instrumentation.retransformClasses(target);
                done++;
            } catch (UnmodifiableClassException e) {
                LOG.warning("Failed to retransform " + target.getName() + ": " + e);
            }
        }
        if (done > 0) {
            LOG.info("Retransformed " + done + " class(es)");
        }
        return done;
    }
}

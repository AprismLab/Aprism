package com.aprism.loader;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.util.ReEntranceLock;

/**
 * Aprism's {@link org.spongepowered.asm.service.IMixinService} implementation.
 * Bridges SpongePowered Mixin to the Aprism runtime by routing class loading,
 * bytecode access, and class tracking through the {@link AprismClassLoader}.
 *
 * <p>This service is discovered by SpongePowered Mixin via the Java
 * {@link java.util.ServiceLoader} mechanism (see
 * {@code META-INF/services/org.spongepowered.asm.service.IMixinService}). The
 * environment selects the first service whose {@link #isValid()} returns
 * {@code true}; Aprism's service is valid whenever the runtime has bound a
 * classloader via {@link AprismMixinBootstrap#bootstrap}.
 *
 * <p>The classloader reference is not injected via the constructor (the
 * ServiceLoader requires a no-arg constructor) but is instead read from the
 * static holder in {@link AprismMixinBootstrap}, which is set before
 * {@link MixinEnvironment#init} is invoked.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismMixinService extends MixinServiceAbstract {

    private final ReEntranceLock lock = new ReEntranceLock(2);
    private final IClassProvider classProvider = new AprismClassProvider();
    private final IClassBytecodeProvider bytecodeProvider = new AprismBytecodeProvider();
    private final ITransformerProvider transformerProvider = new AprismTransformerProvider();
    private final IClassTracker classTracker = new AprismClassTracker();
    private final IMixinAuditTrail auditTrail = new AprismAuditTrail();
    private final IContainerHandle primaryContainer = new AprismContainerHandle("aprism-loader-core");

    /**
     * @return the service name used by SpongePowered Mixin for logging
     */
    @Override
    public String getName() {
        return "Aprism";
    }

    /**
     * Valid whenever the Aprism runtime has bound a classloader. This causes
     * the Mixin environment to select this service over the bundled
     * LaunchWrapper / ModLauncher fallbacks (which return {@code false} when
     * their host platforms are absent).
     *
     * @return whether the Aprism classloader is available
     */
    @Override
    public boolean isValid() {
        return AprismMixinBootstrap.getClassLoader() != null;
    }

    @Override
    public IClassProvider getClassProvider() {
        return classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return transformerProvider;
    }

    @Override
    public IClassTracker getClassTracker() {
        return classTracker;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return auditTrail;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.emptyList();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return primaryContainer;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        AprismClassLoader cl = AprismMixinBootstrap.getClassLoader();
        if (cl != null) {
            return cl.getResourceAsStream(name);
        }
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Override
    protected ILogger createLogger(String name) {
        return new AprismLogger(java.util.logging.Logger.getLogger("aprism.mixin." + name));
    }

    /**
     * Class provider that routes class lookups through the Aprism classloader.
     * Mixin calls this when it needs to resolve mixin classes, target classes,
     * or reference classes during transformation.
     */
    private static final class AprismClassProvider implements IClassProvider {
        @Override
        @SuppressWarnings("deprecation")
        public java.net.URL[] getClassPath() {
            AprismClassLoader cl = AprismMixinBootstrap.getClassLoader();
            if (cl instanceof AprismClassLoader aprism) {
                return aprism.getURLs();
            }
            return new java.net.URL[0];
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return loadOrThrow(name, false);
        }

        @Override
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return loadOrThrow(name, initialize);
        }

        @Override
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return loadOrThrow(name, initialize);
        }

        private static Class<?> loadOrThrow(String name, boolean initialize) throws ClassNotFoundException {
            AprismClassLoader cl = AprismMixinBootstrap.getClassLoader();
            if (cl != null) {
                Class<?> c = cl.loadClass(name);
                if (initialize) {
                    cl.loadClass(name);
                }
                return c;
            }
            throw new ClassNotFoundException(name);
        }
    }

    /**
     * Bytecode provider that reads class bytes from the Aprism classloader and
     * returns ASM {@link ClassNode} trees. Mixin uses this to inspect target
     * classes when applying mixins.
     */
    private static final class AprismBytecodeProvider implements IClassBytecodeProvider {
        @Override
        public ClassNode getClassNode(String name) throws ClassNotFoundException, java.io.IOException {
            return getClassNode(name, false, 0);
        }

        @Override
        public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, java.io.IOException {
            return getClassNode(name, runTransformers, 0);
        }

        @Override
        public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, java.io.IOException {
            String resource = name.replace('.', '/') + ".class";
            AprismClassLoader cl = AprismMixinBootstrap.getClassLoader();
            ClassLoader loader = cl != null ? cl : Thread.currentThread().getContextClassLoader();
            try (InputStream is = loader.getResourceAsStream(resource)) {
                if (is == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = is.readAllBytes();
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, readerFlags);
                return node;
            }
        }
    }

    /**
     * Transformer provider. Aprism does not expose additional transformers to
     * the Mixin environment; the Mixin transformer itself is created and
     * registered internally by {@link MixinEnvironment} during init.
     */
    private static final class AprismTransformerProvider implements ITransformerProvider {
        @Override
        public Collection<ITransformer> getTransformers() {
            return Collections.emptyList();
        }

        @Override
        public Collection<ITransformer> getDelegatedTransformers() {
            return Collections.emptyList();
        }

        @Override
        public void addTransformerExclusion(String name) {
            AprismMixinBootstrap.addTransformerExclusion(name);
        }
    }

    /**
     * Class tracker. Tracks invalid (failed-to-load) classes so the Mixin
     * environment can skip them on subsequent passes.
     */
    private static final class AprismClassTracker implements IClassTracker {
        private final java.util.Set<String> invalidClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public void registerInvalidClass(String name) {
            invalidClasses.add(name);
        }

        @Override
        public boolean isClassLoaded(String name) {
            // Use the defined-class check rather than Class.forName: the
            // latter would ACTIVELY LOAD the class, which Mixin then treats as
            // "target loaded too early" and aborts the injection. In production
            // Mixin targets are transformed at load time, before they are
            // defined, so only already-defined classes count as loaded.
            AprismClassLoader cl = AprismMixinBootstrap.getClassLoader();
            if (cl != null) {
                if (cl.isClassDefined(name)) {
                    return true;
                }
            }
            // Also consult the platform/system loaders for JDK classes
            return false;
        }

        @Override
        public String getClassRestrictions(String name) {
            if (invalidClasses.contains(name)) {
                return "invalid";
            }
            // Mixin calls .length() on this value; null would throw an NPE in
            // MixinInfo and silently abort ALL mixin preparation. An empty
            // string means "no restrictions" for a normally loadable class.
            return "";
        }
    }

    /**
     * No-op audit trail. Real audit logging can be wired here later.
     */
    private static final class AprismAuditTrail implements IMixinAuditTrail {
        @Override
        public void onApply(String targetClass, String mixinClass) {
            // no-op
        }

        @Override
        public void onPostProcess(String targetClass) {
            // no-op
        }

        @Override
        public void onGenerate(String targetClass, String generatedName) {
            // no-op
        }
    }

    /**
     * Minimal container handle representing the loader-core jar.
     */
    private static final class AprismContainerHandle implements IContainerHandle {
        private final String id;

        AprismContainerHandle(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDescription() {
            return "Aprism Loader Core";
        }

        @Override
        public String getAttribute(String key) {
            return null;
        }

        @Override
        public Collection<IContainerHandle> getNestedContainers() {
            return Collections.emptyList();
        }
    }

    /**
     * Bridges SpongePowered Mixin's {@link ILogger} to a JDK logger.
     */
    private static final class AprismLogger implements ILogger {
        private final java.util.logging.Logger delegate;

        AprismLogger(java.util.logging.Logger delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getId() {
            return delegate.getName();
        }

        @Override
        public String getType() {
            return "jdk";
        }

        @Override
        public void catching(Level level, Throwable t) {
            delegate.log(toJdk(level), "Catching", t);
        }

        @Override
        public void catching(Throwable t) {
            delegate.log(java.util.logging.Level.SEVERE, "Catching", t);
        }

        @Override
        public void debug(String message, Object... params) {
            delegate.log(java.util.logging.Level.FINE, format(message, params));
        }

        @Override
        public void debug(String message, Throwable t) {
            delegate.log(java.util.logging.Level.FINE, message, t);
        }

        @Override
        public void error(String message, Object... params) {
            delegate.log(java.util.logging.Level.SEVERE, format(message, params));
        }

        @Override
        public void error(String message, Throwable t) {
            delegate.log(java.util.logging.Level.SEVERE, message, t);
        }

        @Override
        public void fatal(String message, Object... params) {
            delegate.log(java.util.logging.Level.SEVERE, format(message, params));
        }

        @Override
        public void fatal(String message, Throwable t) {
            delegate.log(java.util.logging.Level.SEVERE, message, t);
        }

        @Override
        public void info(String message, Object... params) {
            delegate.log(java.util.logging.Level.INFO, format(message, params));
        }

        @Override
        public void info(String message, Throwable t) {
            delegate.log(java.util.logging.Level.INFO, message, t);
        }

        @Override
        public void log(Level level, String message, Object... params) {
            delegate.log(toJdk(level), format(message, params));
        }

        @Override
        public void log(Level level, String message, Throwable t) {
            delegate.log(toJdk(level), message, t);
        }

        @Override
        public <T extends Throwable> T throwing(T t) {
            delegate.log(java.util.logging.Level.SEVERE, "Throwing", t);
            return t;
        }

        @Override
        public void trace(String message, Object... params) {
            delegate.log(java.util.logging.Level.FINER, format(message, params));
        }

        @Override
        public void trace(String message, Throwable t) {
            delegate.log(java.util.logging.Level.FINER, message, t);
        }

        @Override
        public void warn(String message, Object... params) {
            delegate.log(java.util.logging.Level.WARNING, format(message, params));
        }

        @Override
        public void warn(String message, Throwable t) {
            delegate.log(java.util.logging.Level.WARNING, message, t);
        }

        private static java.util.logging.Level toJdk(Level level) {
            return switch (level) {
                case FATAL, ERROR -> java.util.logging.Level.SEVERE;
                case WARN -> java.util.logging.Level.WARNING;
                case INFO -> java.util.logging.Level.INFO;
                case DEBUG -> java.util.logging.Level.FINE;
                case TRACE -> java.util.logging.Level.FINER;
            };
        }

        private static String format(String message, Object... params) {
            if (params == null || params.length == 0) {
                return message;
            }
            // SpongePowered Mixin uses SLF4J-style {} placeholders (not
            // java.text.MessageFormat {0} style). Substitute each {} with the
            // next param in order.
            StringBuilder sb = new StringBuilder(message.length() + 32);
            int paramIndex = 0;
            for (int i = 0; i < message.length(); i++) {
                char c = message.charAt(i);
                if (c == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}'
                        && paramIndex < params.length) {
                    sb.append(params[paramIndex++]);
                    i++;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}

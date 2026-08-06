package com.aprism.loader;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Class file transformer that applies Mixin patches and access wideners to
 * Minecraft classes as they are loaded. Uses ASM for bytecode manipulation and
 * maintains a list of registered transformations for lookup by class name.
 *
 * <p>Transformation pipeline (applied in order for every class loaded via the
 * javaagent {@link java.lang.instrument.Instrumentation} handle):
 * <ol>
 *   <li><b>Registered transformations</b>: precomputed bytecode replacements
 *       keyed by class name (e.g. for hard-patched classes).</li>
 *   <li><b>Mixin delegation</b>: delegates to the SpongePowered Mixin
 *       transformer via {@link AprismMixinBootstrap#transformClassBytes} so
 *       that {@code @Mixin}/{@code @Inject}/{@code @Redirect} annotations
 *       declared by loaded mods are applied. This is the canonical bytecode
 *       injection path per FACT.md 9.3.</li>
 *   <li><b>Access wideners</b>: applies Fabric-style access widener rules
 *       (accessible/extendable/mutable) to classes, methods, and fields via
 *       an ASM {@link ClassVisitor} pass.</li>
 * </ol>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismClassTransformer implements ClassFileTransformer {

    /**
     * A registered bytecode transformation: a class name paired with the
     * fully-patched bytes that should replace the original class bytes.
     *
     * @param className     the binary class name (dotted or slashed)
     * @param patchedBytes  the replacement bytecode
     */
    public record Transformation(String className, byte[] patchedBytes) {
    }

    private final List<Transformation> transformations = new ArrayList<>();
    private final AccessWidener accessWidener = new AccessWidener();

    /**
     * Registers a precomputed transformation for a class.
     *
     * @param transformation the transformation to register
     */
    public void register(Transformation transformation) {
        transformations.add(transformation);
    }

    /**
     * @return the shared {@link AccessWidener} instance. Rules can be added
     *         to it at any time; they take effect on the next class
     *         transformation pass.
     */
    public AccessWidener getAccessWidener() {
        return accessWidener;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return null;
        }
        byte[] bytes = applyRegisteredTransformations(className, classfileBuffer);
        bytes = applyMixins(className, bytes);
        bytes = applyAccessWideners(className, bytes);
        return bytes;
    }

    /**
     * Applies any pre-registered transformation whose class name matches.
     *
     * @param className the slashed class name passed by the JVM
     * @param bytes     the original bytecode
     * @return the transformed bytecode, or the original if no match
     */
    private byte[] applyRegisteredTransformations(String className, byte[] bytes) {
        for (Transformation t : transformations) {
            if (t.className().replace('.', '/').equals(className)) {
                return t.patchedBytes();
            }
        }
        return bytes;
    }

    /**
     * Delegates to the SpongePowered Mixin transformer so that
     * {@code @Mixin}/{@code @Inject} annotations declared by loaded mods are
     * applied to target classes. When the Mixin environment is not bootstrapped
     * (e.g. before {@link AprismRuntime#initialize}), this is a passthrough.
     *
     * @param className the slashed class name
     * @param bytes     the bytecode to transform
     * @return the mixin-transformed bytecode, or the original if Mixin is unavailable
     */
    private byte[] applyMixins(String className, byte[] bytes) {
        if (!AprismMixinBootstrap.isAvailable()) {
            return bytes;
        }
        String dotted = className.replace('/', '.');
        return AprismMixinBootstrap.transformClassBytes(dotted, bytes);
    }

    /**
     * Applies registered access widener rules to the class via an ASM
     * read/write pass. If no rules target this class, the original bytes are
     * returned unchanged (no ASM pass is performed).
     *
     * <p>Rule application:
     * <ul>
     *   <li>{@code accessible} class -> set ACC_PUBLIC, clear ACC_PRIVATE/ACC_PROTECTED</li>
     *   <li>{@code extendable} class -> set ACC_PROTECTED, clear ACC_PRIVATE/ACC_FINAL</li>
     *   <li>{@code accessible} method/field -> set ACC_PUBLIC, clear ACC_PRIVATE/ACC_PROTECTED</li>
     *   <li>{@code extendable} method -> set ACC_PROTECTED, clear ACC_PRIVATE/ACC_FINAL</li>
     *   <li>{@code mutable} field -> clear ACC_FINAL</li>
     * </ul>
     *
     * @param className the slashed class name
     * @param bytes     the bytecode to widen
     * @return the (possibly rewritten) bytecode
     */
    private byte[] applyAccessWideners(String className, byte[] bytes) {
        if (!accessWidener.hasRules()) {
            return bytes;
        }
        List<AccessWidener.WidenerRule> rules = accessWidener.getRulesForClass(className);
        if (rules.isEmpty()) {
            return bytes;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new AccessWideningVisitor(Opcodes.ASM9, writer, rules);
            reader.accept(visitor, 0);
            return writer.toByteArray();
        } catch (Exception e) {
            return bytes;
        }
    }

    /**
     * ASM {@link ClassVisitor} that applies access widener rules to the
     * visited class, its methods, and its fields.
     */
    private static final class AccessWideningVisitor extends ClassVisitor {

        private final List<AccessWidener.WidenerRule> rules;

        AccessWideningVisitor(int api, ClassVisitor cv, List<AccessWidener.WidenerRule> rules) {
            super(api, cv);
            this.rules = rules;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            int newAccess = access;
            for (AccessWidener.WidenerRule rule : rules) {
                if (rule.isClassRule()) {
                    newAccess = applyClassAccess(newAccess, rule.accessType());
                }
            }
            super.visit(version, newAccess, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                String signature, Object value) {
            int newAccess = access;
            for (AccessWidener.WidenerRule rule : rules) {
                if (rule.isFieldRule()
                        && rule.memberName().equals(name)
                        && rule.descriptor().equals(descriptor)) {
                    newAccess = applyMemberAccess(newAccess, rule.accessType());
                }
            }
            return super.visitField(newAccess, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            int newAccess = access;
            for (AccessWidener.WidenerRule rule : rules) {
                if (rule.isMethodRule()
                        && rule.memberName().equals(name)
                        && rule.descriptor().equals(descriptor)) {
                    newAccess = applyMemberAccess(newAccess, rule.accessType());
                }
            }
            return super.visitMethod(newAccess, name, descriptor, signature, exceptions);
        }

        /**
         * Applies a class-level access rule.
         *
         * @param access     the original access flags
         * @param accessType the widener access type
         * @return the modified access flags
         */
        private static int applyClassAccess(int access, AccessWidener.AccessType accessType) {
            switch (accessType) {
                case ACCESSIBLE -> {
                    return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }
                case EXTENDABLE -> {
                    return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) | Opcodes.ACC_PROTECTED;
                }
                default -> {
                    return access;
                }
            }
        }

        /**
         * Applies a method or field access rule.
         *
         * @param access     the original access flags
         * @param accessType the widener access type
         * @return the modified access flags
         */
        private static int applyMemberAccess(int access, AccessWidener.AccessType accessType) {
            switch (accessType) {
                case ACCESSIBLE -> {
                    return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
                }
                case EXTENDABLE -> {
                    return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) | Opcodes.ACC_PROTECTED;
                }
                case MUTABLE -> {
                    return access & ~Opcodes.ACC_FINAL;
                }
                default -> {
                    return access;
                }
            }
        }
    }
}

package com.aprism.loader;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Class file transformer that applies Mixin patches and access wideners to
 * Minecraft classes as they are loaded. Uses ASM for bytecode manipulation and
 * maintains a list of registered transformations for lookup by class name.
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

    /**
     * Registers a precomputed transformation for a class.
     *
     * @param transformation the transformation to register
     */
    public void register(Transformation transformation) {
        transformations.add(transformation);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (className == null) {
            return null;
        }
        byte[] bytes = applyRegisteredTransformations(className, classfileBuffer);
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
     * Runs an ASM read/write pass that serves as the access-widener application
     * point. Real widener rules are applied by the Mixin subsystem; this pass
     * ensures the class is rewritten through ASM and returned.
     *
     * @param className the slashed class name
     * @param bytes     the bytecode to widen
     * @return the (possibly rewritten) bytecode
     */
    private byte[] applyAccessWideners(String className, byte[] bytes) {
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, 0);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            };
            reader.accept(visitor, 0);
            return writer.toByteArray();
        } catch (Exception e) {
            return bytes;
        }
    }
}

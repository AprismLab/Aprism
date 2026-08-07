package com.aprism.loader.remap;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

/**
 * Applies a {@link Remapper} to Java bytecode, rewriting every class, method,
 * and field reference (plus their descriptors) from one naming namespace to
 * another.
 *
 * <p>Built on ASM's {@link ClassRemapper} visitor, delegating name resolution
 * to an Aprism {@link Remapper}. The ASM {@link org.objectweb.asm.commons.Remapper}
 * adapter translates ASM's remapping contract onto the Aprism SPI.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BytecodeRemapper {

    private final Remapper remapper;

    private BytecodeRemapper(Remapper remapper) {
        this.remapper = remapper;
    }

    /**
     * Creates a bytecode remapper around the given remapper.
     *
     * @param remapper the name remapper
     * @return the bytecode remapper
     */
    public static BytecodeRemapper of(Remapper remapper) {
        return new BytecodeRemapper(remapper);
    }

    /**
     * Rewrites the given class bytecode, translating all class/member
     * references through the remapper.
     *
     * @param classBytes the original class bytecode
     * @return the remapped bytecode
     * @throws IOException if the bytecode cannot be parsed
     */
    public byte[] remap(byte[] classBytes) throws IOException {
        ClassReader reader = new ClassReader(classBytes);
        // COMPUTE_FRAMES is not needed: remapping never changes the control
        // flow, only the referenced names/descriptors. COMPUTE_MAXS keeps the
        // writer correct when descriptors widen slightly.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassRemapper visitor = new ClassRemapper(writer, new AsmRemapperAdapter(remapper));
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    /**
     * Bridges the Aprism {@link Remapper} SPI onto ASM's remapper contract.
     */
    private static final class AsmRemapperAdapter extends org.objectweb.asm.commons.Remapper {

        private final Remapper delegate;

        private AsmRemapperAdapter(Remapper delegate) {
            this.delegate = delegate;
        }

        @Override
        public String map(String internalName) {
            return delegate.mapClassName(internalName);
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            return delegate.mapMethodName(owner, name, descriptor);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return delegate.mapFieldName(owner, name, descriptor);
        }

        @Override
        public String mapDesc(String descriptor) {
            return delegate.mapDesc(descriptor);
        }
    }
}

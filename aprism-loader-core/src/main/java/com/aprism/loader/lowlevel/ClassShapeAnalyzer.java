package com.aprism.loader.lowlevel;

import com.aprism.api.lowlevel.ClassShape;
import com.aprism.api.lowlevel.ClassShapeDiff;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parses class-file bytes into a typed {@link ClassShape} and computes
 * structural diffs between shapes (v26.4-Alpha.3, deep bytecode-hook API).
 *
 * <p>This is the loader-side engine behind the deep API: it reads class
 * files with ASM so that mods can introspect and compare class structure
 * without depending on a bytecode library themselves.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ClassShapeAnalyzer {

    private ClassShapeAnalyzer() {
    }

    /**
     * Parses the given class-file bytes into a {@link ClassShape}.
     *
     * @param classBytes the class-file bytes
     * @return the parsed shape
     * @throws IllegalArgumentException if the bytes are null or not a valid
     *                                  class file
     */
    public static ClassShape analyze(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            throw new IllegalArgumentException("class bytes must be non-empty");
        }
        ClassReader reader;
        try {
            reader = new ClassReader(classBytes);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("bytes are not a valid class file", malformed);
        }
        ShapeCollector collector = new ShapeCollector();
        reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        return new ClassShape(collector.className, collector.superName,
                collector.interfaces, collector.access, collector.methods, collector.fields);
    }

    /**
     * Computes the structural diff between an old shape and a new shape.
     *
     * @param oldShape the live/current shape
     * @param newShape the proposed shape
     * @return the diff describing what would change
     */
    public static ClassShapeDiff diff(ClassShape oldShape, ClassShape newShape) {
        Objects.requireNonNull(oldShape, "oldShape");
        Objects.requireNonNull(newShape, "newShape");

        Set<String> oldMethods = methodKeys(oldShape);
        Set<String> newMethods = methodKeys(newShape);
        List<String> addedMethods = new ArrayList<>(newMethods.stream()
                .filter(key -> !oldMethods.contains(key)).toList());
        List<String> removedMethods = new ArrayList<>(oldMethods.stream()
                .filter(key -> !newMethods.contains(key)).toList());

        Set<String> oldFields = fieldKeys(oldShape);
        Set<String> newFields = fieldKeys(newShape);
        List<String> addedFields = new ArrayList<>(newFields.stream()
                .filter(key -> !oldFields.contains(key)).toList());
        List<String> removedFields = new ArrayList<>(oldFields.stream()
                .filter(key -> !newFields.contains(key)).toList());

        boolean superclassChanged = !Objects.equals(oldShape.superName(), newShape.superName());
        boolean interfacesChanged = !new LinkedHashSet<>(oldShape.interfaces())
                .equals(new LinkedHashSet<>(newShape.interfaces()));

        return new ClassShapeDiff(addedMethods, removedMethods, addedFields, removedFields,
                superclassChanged, interfacesChanged);
    }

    private static Set<String> methodKeys(ClassShape shape) {
        Set<String> keys = new LinkedHashSet<>();
        for (ClassShape.MethodShape method : shape.methods()) {
            keys.add(method.name() + method.descriptor());
        }
        return keys;
    }

    private static Set<String> fieldKeys(ClassShape shape) {
        Set<String> keys = new LinkedHashSet<>();
        for (ClassShape.FieldShape field : shape.fields()) {
            keys.add(field.name());
        }
        return keys;
    }

    /**
     * ASM visitor that collects the class shape.
     */
    private static final class ShapeCollector extends ClassVisitor {

        private String className;
        private String superName;
        private final List<String> interfaces = new ArrayList<>();
        private int access;
        private final List<ClassShape.MethodShape> methods = new ArrayList<>();
        private final List<ClassShape.FieldShape> fields = new ArrayList<>();

        private ShapeCollector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            this.className = name;
            this.access = access;
            this.superName = superName;
            if (interfaces != null) {
                for (String iface : interfaces) {
                    this.interfaces.add(iface);
                }
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            methods.add(new ClassShape.MethodShape(name, descriptor, access));
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                String signature, Object value) {
            fields.add(new ClassShape.FieldShape(name, descriptor, access));
            return null;
        }
    }
}

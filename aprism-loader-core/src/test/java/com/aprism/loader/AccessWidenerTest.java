package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * JUnit 5 + AssertJ tests for the {@link AccessWidener} rule parser and the
 * {@link AprismClassTransformer}'s access-widening ASM pass.
 *
 * @author BlockConnect@StarsailsClover
 */
class AccessWidenerTest {

    /**
     * Builds a minimal class with a private field and a private method so the
     * access widener rules have targets to transform.
     *
     * @return the class bytecode
     */
    private static byte[] generateTestClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "net/TestTarget", null, "java/lang/Object", null);
        // private final int secretField = 0
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "secretField", "I", null, Integer.valueOf(0)).visitEnd();
        // private void secretMethod() {}
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE,
                "secretMethod", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Reads the access flags of a field from class bytecode.
     *
     * @param bytes      the class bytecode
     * @param fieldName  the field name
     * @param descriptor the field descriptor
     * @return the access flags, or -1 if not found
     */
    private static int getFieldAccess(byte[] bytes, String fieldName, String descriptor) {
        ClassReader reader = new ClassReader(bytes);
        final int[] result = {-1};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc,
                    String signature, Object value) {
                if (name.equals(fieldName) && desc.equals(descriptor)) {
                    result[0] = access;
                }
                return null;
            }
        }, 0);
        return result[0];
    }

    /**
     * Reads the access flags of a method from class bytecode.
     *
     * @param bytes      the class bytecode
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @return the access flags, or -1 if not found
     */
    private static int getMethodAccess(byte[] bytes, String methodName, String descriptor) {
        ClassReader reader = new ClassReader(bytes);
        final int[] result = {-1};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                if (name.equals(methodName) && desc.equals(descriptor)) {
                    result[0] = access;
                }
                return null;
            }
        }, 0);
        return result[0];
    }

    /**
     * Reads the access flags of the class itself.
     *
     * @param bytes the class bytecode
     * @return the access flags
     */
    private static int getClassAccess(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        final int[] result = {-1};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                result[0] = access;
            }
        }, 0);
        return result[0];
    }

    @Test
    void parsesAccessibleClassRule() throws IOException {
        AccessWidener aw = new AccessWidener();
        String content = "accessWidener v1 named\naccessible class net.TestTarget\n";
        aw.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertThat(aw.ruleCount()).isEqualTo(1);
        List<AccessWidener.WidenerRule> rules = aw.getRulesForClass("net/TestTarget");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).isClassRule()).isTrue();
        assertThat(rules.get(0).accessType()).isEqualTo(AccessWidener.AccessType.ACCESSIBLE);
    }

    @Test
    void parsesAccessibleMethodRule() throws IOException {
        AccessWidener aw = new AccessWidener();
        String content = "accessWidener v1 named\naccessible method net.TestTarget secretMethod ()V\n";
        aw.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        List<AccessWidener.WidenerRule> rules = aw.getRulesForClass("net/TestTarget");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).isMethodRule()).isTrue();
        assertThat(rules.get(0).memberName()).isEqualTo("secretMethod");
        assertThat(rules.get(0).descriptor()).isEqualTo("()V");
    }

    @Test
    void parsesMutableFieldRule() throws IOException {
        AccessWidener aw = new AccessWidener();
        String content = "accessWidener v1 named\nmutable field net.TestTarget secretField I\n";
        aw.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        List<AccessWidener.WidenerRule> rules = aw.getRulesForClass("net/TestTarget");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).isFieldRule()).isTrue();
        assertThat(rules.get(0).accessType()).isEqualTo(AccessWidener.AccessType.MUTABLE);
    }

    @Test
    void parsesMultipleRules() throws IOException {
        AccessWidener aw = new AccessWidener();
        String content = """
                accessWidener v1 named
                accessible class net.TestTarget
                accessible method net.TestTarget secretMethod ()V
                mutable field net.TestTarget secretField I
                extendable class net.Other
                """;
        aw.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertThat(aw.ruleCount()).isEqualTo(4);
        assertThat(aw.getRulesForClass("net/TestTarget")).hasSize(3);
        assertThat(aw.getRulesForClass("net/Other")).hasSize(1);
    }

    @Test
    void skipsCommentsAndBlankLines() throws IOException {
        AccessWidener aw = new AccessWidener();
        String content = """
                # comment
                accessWidener v1 named

                # another comment
                accessible class net.TestTarget
                """;
        aw.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        assertThat(aw.ruleCount()).isEqualTo(1);
    }

    @Test
    void mergeCombinesRules() throws IOException {
        AccessWidener aw1 = new AccessWidener();
        aw1.parse(new ByteArrayInputStream(
                "accessWidener v1 named\naccessible class net.A\n".getBytes(StandardCharsets.UTF_8)));
        AccessWidener aw2 = new AccessWidener();
        aw2.parse(new ByteArrayInputStream(
                "accessWidener v1 named\naccessible class net.B\n".getBytes(StandardCharsets.UTF_8)));
        aw1.merge(aw2);
        assertThat(aw1.ruleCount()).isEqualTo(2);
        assertThat(aw1.getRulesForClass("net/A")).hasSize(1);
        assertThat(aw1.getRulesForClass("net/B")).hasSize(1);
    }

    @Test
    void noRulesReturnsEmptyList() {
        AccessWidener aw = new AccessWidener();
        assertThat(aw.hasRules()).isFalse();
        assertThat(aw.getRulesForClass("any/Class")).isEmpty();
    }

    @Test
    void transformerMakesFieldAccessible() throws IOException, IllegalClassFormatException {
        byte[] original = generateTestClass();
        assertThat(getFieldAccess(original, "secretField", "I"))
                .isEqualTo(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);

        AccessWidener aw = new AccessWidener();
        aw.parse(new ByteArrayInputStream(
                "accessWidener v1 named\naccessible field net.TestTarget secretField I\n"
                        .getBytes(StandardCharsets.UTF_8)));

        AprismClassTransformer transformer = new AprismClassTransformer();
        transformer.getAccessWidener().merge(aw);

        byte[] transformed = transformer.transform(
                null, "net/TestTarget", null, null, original);
        int newAccess = getFieldAccess(transformed, "secretField", "I");
        assertThat(newAccess & Opcodes.ACC_PUBLIC).isNotZero();
        assertThat(newAccess & Opcodes.ACC_PRIVATE).isZero();
    }

    @Test
    void transformerMakesFieldMutable() throws IOException, IllegalClassFormatException {
        byte[] original = generateTestClass();
        assertThat(getFieldAccess(original, "secretField", "I"))
                .isEqualTo(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);

        AccessWidener aw = new AccessWidener();
        aw.parse(new ByteArrayInputStream(
                "accessWidener v1 named\nmutable field net.TestTarget secretField I\n"
                        .getBytes(StandardCharsets.UTF_8)));

        AprismClassTransformer transformer = new AprismClassTransformer();
        transformer.getAccessWidener().merge(aw);

        byte[] transformed = transformer.transform(
                null, "net/TestTarget", null, null, original);
        int newAccess = getFieldAccess(transformed, "secretField", "I");
        assertThat(newAccess & Opcodes.ACC_FINAL)
                .as("Final flag should be removed by mutable rule")
                .isZero();
    }

    @Test
    void transformerMakesMethodAccessible() throws IOException, IllegalClassFormatException {
        byte[] original = generateTestClass();
        assertThat(getMethodAccess(original, "secretMethod", "()V"))
                .isEqualTo(Opcodes.ACC_PRIVATE);

        AccessWidener aw = new AccessWidener();
        aw.parse(new ByteArrayInputStream(
                "accessWidener v1 named\naccessible method net.TestTarget secretMethod ()V\n"
                        .getBytes(StandardCharsets.UTF_8)));

        AprismClassTransformer transformer = new AprismClassTransformer();
        transformer.getAccessWidener().merge(aw);

        byte[] transformed = transformer.transform(
                null, "net/TestTarget", null, null, original);
        int newAccess = getMethodAccess(transformed, "secretMethod", "()V");
        assertThat(newAccess & Opcodes.ACC_PUBLIC).isNotZero();
        assertThat(newAccess & Opcodes.ACC_PRIVATE).isZero();
    }

    @Test
    void transformerMakesClassExtendable() throws IOException, IllegalClassFormatException {
        byte[] original = generateTestClass();
        assertThat(getClassAccess(original) & Opcodes.ACC_FINAL).isNotZero();

        AccessWidener aw = new AccessWidener();
        aw.parse(new ByteArrayInputStream(
                "accessWidener v1 named\nextendable class net.TestTarget\n"
                        .getBytes(StandardCharsets.UTF_8)));

        AprismClassTransformer transformer = new AprismClassTransformer();
        transformer.getAccessWidener().merge(aw);

        byte[] transformed = transformer.transform(
                null, "net/TestTarget", null, null, original);
        int newAccess = getClassAccess(transformed);
        assertThat(newAccess & Opcodes.ACC_FINAL)
                .as("Final flag should be removed by extendable rule")
                .isZero();
        assertThat(newAccess & Opcodes.ACC_PROTECTED).isNotZero();
    }

    @Test
    void transformerPassthroughWhenNoRules() throws IllegalClassFormatException {
        byte[] original = generateTestClass();
        AprismClassTransformer transformer = new AprismClassTransformer();
        byte[] transformed = transformer.transform(
                null, "net/TestTarget", null, null, original);
        // With no rules, the bytes should be returned unchanged
        assertThat(transformed).isSameAs(original);
    }

    @Test
    void transformerPassthroughForUnmatchedClass() throws IOException, IllegalClassFormatException {
        byte[] original = generateTestClass();
        AccessWidener aw = new AccessWidener();
        aw.parse(new ByteArrayInputStream(
                "accessWidener v1 named\naccessible class net.Other\n"
                        .getBytes(StandardCharsets.UTF_8)));
        AprismClassTransformer transformer = new AprismClassTransformer();
        transformer.getAccessWidener().merge(aw);
        byte[] transformed = transformer.transform(
                null, "net/TestTarget", null, null, original);
        assertThat(transformed).isSameAs(original);
    }
}

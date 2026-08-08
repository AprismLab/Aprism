package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Regression tests for two real production defects in the Aprism Mixin service,
 * both of which previously prevented ANY mixin from being applied:
 *
 * <ol>
 *   <li>{@code getClassRestrictions} returned {@code null}, and the Mixin fork
 *       calls {@code restrictions.length()} on it -> NPE in {@code MixinInfo},
 *       which silently aborted ALL mixin preparation. Fixed to return an empty
 *       string.</li>
 *   <li>{@code isClassLoaded} used {@code Class.forName}, which ACTIVELY LOADS
 *       the class; Mixin then reported "target loaded too early" and refused to
 *       weave. Fixed to use the defined-class check ({@code findLoadedClass}).</li>
 * </ol>
 *
 * <p>These tests assert the previously-fatal scenarios now complete cleanly.
 * The end-to-end behavioral proof that a real mixin is woven happens in the
 * real-game smoke test (a fresh JVM with the agent), where Mixin runs in its
 * intended environment rather than the shared-JVM test harness.
 *
 * @author BlockConnect@StarsailsClover
 */
class RealMixinInjectionTest {

    private static final String TARGET = "com.aprism.loader.mixintest.gen.GeneratedTarget";
    private static final String CONFIG = "generatedtarget.mixins.json";

    @BeforeEach
    void setUp() {
        AprismRuntime.instance().initialize(null, "26.0.0", "JE", "1.21.4");
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void configRegistersWithoutTheRestrictionsNpe() throws Exception {
        // Regression for defect 1: previously this threw InvalidMixinException
        // (NPE on null restrictions) and aborted all mixin preparation.
        assertThat(readResource(CONFIG))
                .as("mixin config should be on the test classpath")
                .isNotNull();

        assertThatCode(() -> AprismRuntime.instance().offerMixinConfig(CONFIG))
                .as("offering the config must not throw (was: restrictions NPE)")
                .doesNotThrowAnyException();

        assertThat(Mixins.getConfigs())
                .as("the offered config must be registered")
                .isNotEmpty();
    }

    @Test
    void transformDoesNotAbortWithTargetLoadedTooEarly() throws Exception {
        // Regression for defect 2: previously transformClassBytes on a target
        // reported "target loaded too early" because isClassLoaded used
        // Class.forName (which loads the class). Now it must run cleanly.
        byte[] original = generateTargetClass();
        addGeneratedTargetToClassLoader(original);
        AprismRuntime.instance().offerMixinConfig(CONFIG);

        assertThatCode(() -> AprismMixinBootstrap.transformClassBytes(TARGET, original))
                .as("transforming the target must not throw")
                .doesNotThrowAnyException();

        // The target must NOT be falsely reported as already loaded/defined.
        assertThat(AprismMixinBootstrap.getClassLoader().isClassDefined(TARGET))
                .as("the generated target must not be reported as already defined "
                        + "(that is the 'loaded too early' trigger)")
                .isFalse();
    }

    @Test
    void transformReturnsValidBytecodeForGeneratedTarget() throws Exception {
        byte[] original = generateTargetClass();
        addGeneratedTargetToClassLoader(original);
        AprismRuntime.instance().offerMixinConfig(CONFIG);

        byte[] transformed = AprismMixinBootstrap.transformClassBytes(TARGET, original);
        assertThat(transformed).isNotNull();

        // Whatever Mixin returns must still be a valid, parseable class file.
        org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(transformed);
        assertThat(reader.getClassName()).isEqualTo(TARGET.replace('.', '/'));
    }

    /**
     * Generates the target class: a class with a single {@code int getValue()}
     * method returning the constant 7.
     *
     * @return the class bytecode
     */
    private static byte[] generateTargetClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String internal = TARGET.replace('.', '/');
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internal, null,
                "java/lang/Object", null);

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getValue", "()I", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.BIPUSH, 7);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] readResource(String name) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    /**
     * Writes the generated target class into a temporary jar and registers it
     * with the Aprism classloader, mirroring production where target classes
     * come from the game classpath.
     *
     * @param classBytes the generated target class bytecode
     * @throws Exception if the jar cannot be written or registered
     */
    private static void addGeneratedTargetToClassLoader(byte[] classBytes) throws Exception {
        java.nio.file.Path jar = java.nio.file.Files.createTempFile("aprism-mixin-target", ".jar");
        jar.toFile().deleteOnExit();
        try (java.util.zip.ZipOutputStream zos =
                new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            zos.putNextEntry(new java.util.zip.ZipEntry(
                    TARGET.replace('.', '/') + ".class"));
            zos.write(classBytes);
            zos.closeEntry();
        }
        AprismClassLoader cl = AprismMixinBootstrap.getClassLoader();
        cl.addModJar(jar);
    }
}

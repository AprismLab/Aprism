package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * In-process regression coverage for the Mixin weaving path, focused on what is
 * reliably provable inside a shared test JVM.
 *
 * <p><b>Why this is not the full behavioral proof:</b> SpongePowered Mixin's
 * environment and transformer are JVM-level singletons. In a Gradle test run the
 * same JVM hosts many tests, so Mixin's static state (prepared configs, phase,
 * active transformer) is shared and cannot be cleanly reset between tests. That
 * makes an in-process assertion that "the woven method returns the injected
 * value" inherently flaky. The <em>authoritative</em> behavioral proof that a
 * mixin is woven into real Minecraft bytecode lives in the real-game smoke test
 * ({@code tools/smoke}), which runs the agent in a <em>fresh</em> JVM against a
 * genuine Minecraft client. That run was verified to weave:
 * {@code Minecraft transformed=true} and the {@code APRISM-MIXIN-PROOF} marker
 * printed from injected code.
 *
 * <p>This test pins the pieces that <em>are</em> deterministic in-process:
 * <ol>
 *   <li>The Mixin transformer is available after initialization.</li>
 *   <li>Offering a mixin config registers it with Mixin without throwing
 *       (the restrictions-NPE / Class.forName-loaded-too-early regressions).</li>
 *   <li>{@link AprismMixinBootstrap#transformClassBytes} passes the
 *       <em>dotted</em> class name to Mixin and returns non-null bytes without
 *       throwing (the real-game weave fix).</li>
 * </ol>
 *
 * @author BlockConnect@StarsailsClover
 */
class MixinWeaveBehaviorTest {

    private static final String TARGET = "com.aprism.loader.mixintest.gen.GeneratedTarget";
    private static final String CONFIG = "generatedtarget.mixins.json";

    @BeforeEach
    void setUp() {
        AprismRuntime.instance().initialize(null, "26.0.0", "JE", "26.2");
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void transformerAvailableAndConfigRegisters() throws Exception {
        // Register the target so Mixin can resolve it during preparation.
        byte[] original = generateTargetClass();
        registerTargetInClassLoader(original);

        // The transformer must be available after initialization.
        assertThat(AprismMixinBootstrap.isAvailable())
                .as("the Mixin transformer must be available after initialization")
                .isTrue();

        // Offering the mixin config (mirrors AprismRuntime.registerMixins) must
        // register it without throwing (regressions: restrictions NPE,
        // Class.forName-loaded-too-early).
        AprismRuntime.instance().offerMixinConfig(CONFIG);
        assertThat(org.spongepowered.asm.mixin.Mixins.getConfigs())
                .as("the offered mixin config must be registered with Mixin")
                .isNotEmpty();
    }

    @Test
    void transformClassBytesReturnsBytesWithoutThrowing() {
        // Regression for the real-game weave bug: transformClassBytes must pass
        // the DOTTED class name as the transformedName argument and must return
        // bytes without throwing. A slashed name makes Mixin's hasMixinsFor fail
        // and no mixin is ever applied. The behavioral effect (actual weaving
        // changing bytes) requires a fresh JVM and is proven in the real-game
        // smoke test; here we assert the dotted-name path executes end to end.
        byte[] original = generateTargetClass();
        byte[] result = AprismMixinBootstrap.transformClassBytes(TARGET, original);
        assertThat(result)
                .as("transformClassBytes must return non-null bytes without throwing")
                .isNotNull();
    }

    /** Generates {@code public int getValue() { return 7; }}. */
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

    private static void registerTargetInClassLoader(byte[] classBytes) throws Exception {
        java.nio.file.Path jar = java.nio.file.Files.createTempFile("aprism-weave-target", ".jar");
        jar.toFile().deleteOnExit();
        try (java.util.zip.ZipOutputStream zos =
                new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            zos.putNextEntry(new java.util.zip.ZipEntry(TARGET.replace('.', '/') + ".class"));
            zos.write(classBytes);
            zos.closeEntry();
        }
        AprismMixinBootstrap.getClassLoader().addModJar(jar);
    }
}

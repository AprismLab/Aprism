package com.aprism.loader.remap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.aprism.api.AprismPhase;
import com.aprism.loader.AprismRuntime;

/**
 * Integration tests for the remap pipeline wired through the runtime:
 * a mod compiled against Intermediary names is loaded under a pre-26.1
 * profile, remapped to official names, and its references resolve against a
 * real official-named class. This is the exact load-time remapping that
 * pre-26.1 Minecraft requires.
 *
 * @author BlockConnect@StarsailsClover
 */
class RemapPipelineTest {

    @TempDir
    Path gameRoot;

    /** official (ns0) -> intermediary (ns1) mappings for the test target. */
    private static final String TINY = """
            tiny\t2\t0\tofficial\tintermediary
            c\tcom/game/Target\tnet/minecraft/class_999
            \tm\t()I\tofficialMethod\tmethod_999
            \tf\tI\tofficialField\tfield_999
            """;

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    /**
     * Generates a mod class implementing IAprismMod. Its onInitialize builds
     * {@code new net/minecraft/class_999()}, invokes {@code method_999()} and
     * reads {@code field_999} — all Intermediary names. Stores the results in
     * static fields so the test can inspect them after dispatch.
     */
    private static byte[] modClassWithIntermediaryRefs() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/mod/RemapMod", null,
                "java/lang/Object", new String[] { "com/aprism/api/IAprismMod" });

        // static int methodResult;
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "methodResult", "I",
                null, null).visitEnd();
        // static int fieldResult;
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fieldResult", "I",
                null, null).visitEnd();

        // <init>
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        // onInitialize(AprismContext): build Target via intermediary names
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "onInitialize",
                "(Lcom/aprism/api/AprismContext;)V", null, null);
        mv.visitCode();
        // Target t = new net/minecraft/class_999();
        mv.visitTypeInsn(Opcodes.NEW, "net/minecraft/class_999");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/class_999",
                "<init>", "()V", false);
        // methodResult = t.method_999();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_999",
                "method_999", "()I", false);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "com/mod/RemapMod", "methodResult", "I");
        // fieldResult = t.field_999;
        mv.visitTypeInsn(Opcodes.NEW, "net/minecraft/class_999");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/class_999",
                "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/class_999", "field_999", "I");
        mv.visitFieldInsn(Opcodes.PUTSTATIC, "com/mod/RemapMod", "fieldResult", "I");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void writeTinyFile(Path file) throws Exception {
        Files.writeString(file, TINY, StandardCharsets.UTF_8);
    }

    private static void writeAje(Path ajeFile, String id, String modClassName,
            byte[] classBytes) throws Exception {
        Files.createDirectories(ajeFile.getParent());
        String manifest = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "1.0.0",
                  "displayName": "%s",
                  "description": "remap test",
                  "environment": "*",
                  "entrypoints": {"main": ["%s"]},
                  "mixins": [],
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """.formatted(id, id, modClassName);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(ajeFile))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("remap.jar"));
            zos.write(jarWith(classBytes, modClassName));
            zos.closeEntry();
        }
    }

    private static byte[] jarWith(byte[] classBytes, String className) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(className.replace('.', '/') + ".class"));
            zos.write(classBytes);
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    @Test
    void remappedModInvokesOfficialClass() throws Exception {
        Path tinyFile = gameRoot.resolve("mappings.tiny");
        writeTinyFile(tinyFile);
        writeAje(gameRoot.resolve("mods").resolve("remapmod.aje"), "remapmod",
                "com.mod.RemapMod", modClassWithIntermediaryRefs());

        // Pre-26.1 profile: remapping is required
        AprismRuntime.instance().initialize(null, "v26.0-Alpha.2", "JE", "1.21.4");
        assertThat(AprismRuntime.instance().getMcProfile()).isEqualTo(McProfile.REMAPPED);

        AprismRuntime.instance().loadIntermediaryMappings(tinyFile);
        assertThat(AprismRuntime.instance().getBytecodeRemapper())
                .as("bytecode remapper installed for remapped profile")
                .isNotNull();

        AprismRuntime.instance().performLoad(gameRoot, gameRoot.resolve("aprism-extensions"));
        assertThat(AprismRuntime.instance().getMod("remapmod")).isNotNull();

        AprismRuntime.instance().invokeEntrypoints(AprismPhase.INIT);

        // The mod's intermediary references were remapped to official and
        // resolved against the real com.game.Target on the classpath.
        Class<?> loaded = AprismRuntime.instance().getClassLoader()
                .loadClass("com.mod.RemapMod");
        int methodResult = loaded.getField("methodResult").getInt(null);
        int fieldResult = loaded.getField("fieldResult").getInt(null);
        assertThat(methodResult)
                .as("intermediary method_999 remapped to officialMethod and invoked")
                .isEqualTo(42);
        assertThat(fieldResult)
                .as("intermediary field_999 remapped to officialField and read")
                .isEqualTo(42);
    }

    @Test
    void noRemapProfileIgnoresMappings() throws Exception {
        Path tinyFile = gameRoot.resolve("mappings.tiny");
        writeTinyFile(tinyFile);

        // 26.2 profile: no remapping
        AprismRuntime.instance().initialize(null, "v26.0-Alpha.2", "JE", "26.2");
        assertThat(AprismRuntime.instance().getMcProfile()).isEqualTo(McProfile.NO_REMAP);

        AprismRuntime.instance().loadIntermediaryMappings(tinyFile);
        assertThat(AprismRuntime.instance().getBytecodeRemapper())
                .as("no remapper for the no-remap profile")
                .isNull();
    }

    @Test
    void remappedProfileWithoutMappingsDoesNotRemap() throws Exception {
        AprismRuntime.instance().initialize(null, "v26.0-Alpha.2", "JE", "1.21.4");
        assertThat(AprismRuntime.instance().getMcProfile()).isEqualTo(McProfile.REMAPPED);
        assertThat(AprismRuntime.instance().getBytecodeRemapper())
                .as("remapper not installed until mappings are loaded")
                .isNull();
    }
}

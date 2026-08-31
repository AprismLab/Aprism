import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class WriteRealAje {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out.getParent());
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/mod/RealSmokeMod", null,
                "java/lang/Object", new String[] { "com/aprism/api/IAprismMod" });
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "onInitialize",
                "(Lcom/aprism/api/AprismContext;)V", null, null);
        mv.visitCode();
        // context.getItemRegistry().register(ResourceKey.parse(
        //         "aprism:realsmoke_item"), new ItemContent(key, 64));
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/aprism/api/AprismContext",
                "getItemRegistry",
                "()Lcom/aprism/api/registry/TypedRegistry;", true);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitLdcInsn("aprism:realsmoke_item");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/aprism/api/registry/ResourceKey", "parse",
                "(Ljava/lang/String;)Lcom/aprism/api/registry/ResourceKey;",
                false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitTypeInsn(Opcodes.NEW, "com/aprism/api/registry/ItemContent");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitIntInsn(Opcodes.BIPUSH, 16);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "com/aprism/api/registry/ItemContent", "<init>",
                "(Lcom/aprism/api/registry/ResourceKey;I)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "com/aprism/api/registry/TypedRegistry", "register",
                "(Lcom/aprism/api/registry/ResourceKey;Ljava/lang/Object;)"
                        + "Ljava/lang/Object;", true);
        mv.visitInsn(Opcodes.POP);
        // context.getBlockRegistry().register(ResourceKey.parse(
        //         "aprism:realsmoke_block"), new BlockContent(key, 1.0f, 1.0f, 0));
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/aprism/api/AprismContext",
                "getBlockRegistry",
                "()Lcom/aprism/api/registry/TypedRegistry;", true);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitLdcInsn("aprism:realsmoke_block");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "com/aprism/api/registry/ResourceKey", "parse",
                "(Ljava/lang/String;)Lcom/aprism/api/registry/ResourceKey;",
                false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitTypeInsn(Opcodes.NEW, "com/aprism/api/registry/BlockContent");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitLdcInsn(1.0f);
        mv.visitLdcInsn(1.0f);
        mv.visitIntInsn(Opcodes.BIPUSH, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "com/aprism/api/registry/BlockContent", "<init>",
                "(Lcom/aprism/api/registry/ResourceKey;FFI)V", false);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "com/aprism/api/registry/TypedRegistry", "register",
                "(Lcom/aprism/api/registry/ResourceKey;Ljava/lang/Object;)"
                        + "Ljava/lang/Object;", true);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "com/aprism/api/AprismContext", "getLogger",
                "()Ljava/util/logging/Logger;", true);
        mv.visitLdcInsn("[RealSmokeMod] onInitialize reached (remap path active); item+block registered");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/logging/Logger", "info",
                "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        byte[] cls = cw.toByteArray();
        String manifest = """
                {
                  "schemaVersion": 1,
                  "id": "realsmoke",
                  "version": "1.0.0",
                  "displayName": "RealSmokeMod",
                  "description": "real 1.21.4 launch smoke",
                  "environment": "*",
                  "entrypoints": {"main": ["com.mod.RealSmokeMod"]},
                  "mixins": [],
                  "depends": {},
                  "platforms": {},
                  "accessWidener": null,
                  "provides": [],
                  "custom": {}
                }
                """;
        ByteArrayOutputStream jarBos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(jarBos)) {
            zos.putNextEntry(new ZipEntry("com/mod/RealSmokeMod.class"));
            zos.write(cls);
            zos.closeEntry();
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("realsmoke.jar"));
            zos.write(jarBos.toByteArray());
            zos.closeEntry();
        }
        System.out.println("wrote " + out);
    }
}

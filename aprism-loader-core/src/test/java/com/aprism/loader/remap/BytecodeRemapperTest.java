package com.aprism.loader.remap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Tests for {@link BytecodeRemapper}: verifies that class, method, and field
 * references inside bytecode are rewritten from one naming namespace to
 * another (the intermediary-to-official direction used for pre-26.1 mods).
 *
 * @author BlockConnect@StarsailsClover
 */
class BytecodeRemapperTest {

    /**
     * official (ns0) to intermediary (ns1). A mod compiled against the
     * intermediary names below must be remapped to the official names so its
     * references resolve against the obfuscated game jar.
     */
    private static final String TINY = """
            tiny\t2\t0\tofficial\tintermediary
            c\tcom/game/Target\tnet/minecraft/class_999
            \tm\t()V\tofficialMethod\tmethod_999
            \tf\tI\tofficialField\tfield_999
            """;

    private static TinyMappings mappings() {
        try {
            return TinyMappings.parse(new StringReader(TINY));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a mod class that references intermediary names: instantiates
     * the intermediary class, invokes its intermediary method, and reads its
     * intermediary field.
     */
    private static byte[] modClassWithIntermediaryRefs() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/mod/ModMain", null,
                "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        // new net/minecraft/class_999()
        mv.visitTypeInsn(Opcodes.NEW, "net/minecraft/class_999");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/class_999",
                "<init>", "()V", false);
        // invoke method_999
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_999",
                "method_999", "()V", false);
        // getfield field_999 : I (pop the result)
        mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/class_999",
                "field_999", "I");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Collected references from a class file. */
    private record Refs(List<String> types, List<String> methods, List<String> fields) {
    }

    /** Reads a class file and collects the referenced types/methods/fields. */
    private static Refs collectRefs(byte[] bytes) throws Exception {
        List<String> types = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        types.add(type);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName,
                            String mDesc, boolean itf) {
                        if (!"<init>".equals(mName)) {
                            methods.add(owner + "." + mName + mDesc);
                        }
                        types.add(owner);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fName, String fDesc) {
                        fields.add(owner + "." + fName + ":" + fDesc);
                        types.add(owner);
                    }
                };
            }
        }, 0);
        return new Refs(types, methods, fields);
    }

    @Test
    void remapsIntermediaryClassReferencesToOfficial() throws Exception {
        Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
        byte[] remapped = BytecodeRemapper.of(r).remap(modClassWithIntermediaryRefs());
        Refs refs = collectRefs(remapped);

        assertThat(refs.types())
                .as("intermediary class references become official")
                .contains("com/game/Target")
                .doesNotContain("net/minecraft/class_999");
    }

    @Test
    void remapsIntermediaryMethodCallsToOfficial() throws Exception {
        Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
        byte[] remapped = BytecodeRemapper.of(r).remap(modClassWithIntermediaryRefs());
        Refs refs = collectRefs(remapped);

        assertThat(refs.methods())
                .as("intermediary method calls become official")
                .contains("com/game/Target.officialMethod()V")
                .doesNotContain("com/game/Target.method_999()V");
    }

    @Test
    void remapsIntermediaryFieldAccessToOfficial() throws Exception {
        Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
        byte[] remapped = BytecodeRemapper.of(r).remap(modClassWithIntermediaryRefs());
        Refs refs = collectRefs(remapped);

        assertThat(refs.fields())
                .as("intermediary field access becomes official")
                .contains("com/game/Target.officialField:I")
                .doesNotContain("com/game/Target.field_999:I");
    }

    @Test
    void noopRemapperLeavesBytecodeUnchanged() throws Exception {
        byte[] original = modClassWithIntermediaryRefs();
        byte[] same = BytecodeRemapper.of(Remapper.noop()).remap(original);
        Refs refs = collectRefs(same);
        assertThat(refs.types()).contains("net/minecraft/class_999");
        assertThat(refs.methods()).contains("net/minecraft/class_999.method_999()V");
    }

    @Test
    void remappedBytecodeStillValid() throws Exception {
        Remapper r = TinyRemapper.intermediaryToOfficial(mappings());
        byte[] remapped = BytecodeRemapper.of(r).remap(modClassWithIntermediaryRefs());
        // A ClassReader over the remapped bytes should parse without error and
        // report the same class name (remapping never renames the class itself).
        ClassReader reader = new ClassReader(remapped);
        assertThat(reader.getClassName()).isEqualTo("com/mod/ModMain");
    }
}

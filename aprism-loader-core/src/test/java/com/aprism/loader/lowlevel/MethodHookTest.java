package com.aprism.loader.lowlevel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Tests the v26.1-Alpha.8 lower-level method-hook machinery (goal #2):
 * {@link MethodHookRegistry} registration/firing and the
 * {@link MethodHookTransformer} bytecode injection.
 *
 * @author BlockConnect@StarsailsClover
 */
class MethodHookTest {

    /** A simple target class used for hook transformation. */
    public static class Target {
        public int compute() {
            return 42;
        }

        public abstract static class AbstractTarget {
            public abstract void abstractMethod();
        }
    }

    private static final String TARGET = "com/aprism/loader/lowlevel/MethodHookTest$Target";

    @AfterEach
    void tearDown() {
        MethodHookRegistry.clear();
    }

    @Test
    void registerAndFireInvokesListener() {
        AtomicInteger calls = new AtomicInteger();
        MethodHookRegistry.register(TARGET, "compute", "()I", calls::incrementAndGet);
        MethodHookRegistry.fire(MethodHookRegistry.hookKey(TARGET, "compute", "()I"));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void fireWithNoListenersIsNoOp() {
        // Must not throw
        MethodHookRegistry.fire(MethodHookRegistry.hookKey(TARGET, "compute", "()I"));
    }

    @Test
    void unregisterRemovesListener() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        MethodHookRegistry.register(TARGET, "compute", "()I", listener);
        MethodHookRegistry.unregister(TARGET, "compute", "()I", listener);
        MethodHookRegistry.fire(MethodHookRegistry.hookKey(TARGET, "compute", "()I"));
        assertThat(calls.get()).isZero();
    }

    @Test
    void hasHookReflectsRegistration() {
        assertThat(MethodHookRegistry.hasHook(TARGET, "compute", "()I")).isFalse();
        MethodHookRegistry.register(TARGET, "compute", "()I", () -> { });
        assertThat(MethodHookRegistry.hasHook(TARGET, "compute", "()I")).isTrue();
    }

    @Test
    void hasAnyHookForClassScansPrefix() {
        assertThat(MethodHookRegistry.hasAnyHookForClass(TARGET)).isFalse();
        MethodHookRegistry.register(TARGET, "compute", "()I", () -> { });
        assertThat(MethodHookRegistry.hasAnyHookForClass(TARGET)).isTrue();
        assertThat(MethodHookRegistry.hasAnyHookForClass("com/other/Class")).isFalse();
    }

    @Test
    void clearRemovesAllHooks() {
        MethodHookRegistry.register(TARGET, "compute", "()I", () -> { });
        MethodHookRegistry.clear();
        assertThat(MethodHookRegistry.hasHook(TARGET, "compute", "()I")).isFalse();
    }

    @Test
    void throwingListenerIsSwallowed() {
        AtomicInteger good = new AtomicInteger();
        MethodHookRegistry.register(TARGET, "compute", "()I", () -> {
            throw new RuntimeException("boom");
        });
        MethodHookRegistry.register(TARGET, "compute", "()I", good::incrementAndGet);
        // Must not propagate; both listeners attempted
        MethodHookRegistry.fire(MethodHookRegistry.hookKey(TARGET, "compute", "()I"));
        assertThat(good.get()).isEqualTo(1);
    }

    @Test
    void transformerInjectsFireCallIntoHookedMethod() {
        MethodHookRegistry.register(TARGET, "compute", "()I", () -> { });

        byte[] original = readClassBytes(TARGET);
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        MethodHookTransformer transformer =
                new MethodHookTransformer(Opcodes.ASM9, writer, TARGET);
        reader.accept(transformer, 0);
        byte[] transformed = writer.toByteArray();

        // The transformed bytecode differs and contains the fire() invocation
        assertThat(transformed).isNotEqualTo(original);
        assertThat(containsFireCall(transformed)).isTrue();
    }

    @Test
    void transformerLeavesUnhookedClassUnchanged() {
        // No hooks registered for this class
        byte[] original = readClassBytes(TARGET);
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        MethodHookTransformer transformer =
                new MethodHookTransformer(Opcodes.ASM9, writer, "com/unrelated/Class");
        reader.accept(transformer, 0);
        byte[] transformed = writer.toByteArray();

        assertThat(containsFireCall(transformed)).isFalse();
    }

    @Test
    void injectedHookActuallyFiresWhenInvoked() throws Exception {
        // This test proves end-to-end: register a hook, transform the target,
        // load the transformed class in a child classloader, and invoke the
        // hooked method; the hook must fire.
        List<String> fired = new ArrayList<>();
        MethodHookRegistry.register(TARGET, "compute", "()I", () -> fired.add("compute"));

        byte[] original = readClassBytes(TARGET);
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        MethodHookTransformer transformer =
                new MethodHookTransformer(Opcodes.ASM9, writer, TARGET);
        reader.accept(transformer, 0);
        byte[] hooked = writer.toByteArray();

        ClassLoader child = new SingleClassLoader(
                TARGET.replace('/', '.'), hooked, getClass().getClassLoader());
        Class<?> loaded = child.loadClass(TARGET.replace('/', '.'));
        Object instance = loaded.getDeclaredConstructor().newInstance();
        int result = (int) loaded.getMethod("compute").invoke(instance);

        assertThat(result).isEqualTo(42);
        assertThat(fired).containsExactly("compute");
    }

    /** Reads the raw bytes of a loaded class. */
    private static byte[] readClassBytes(String slashedName) {
        String resource = slashedName + ".class";
        try (var in = MethodHookTest.class.getClassLoader().getResourceAsStream(resource)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }

    /** Scans the constant pool for the MethodHookRegistry.fire call. */
    private static boolean containsFireCall(byte[] bytes) {
        // Quick check: the injected INVOKESTATIC references the owner
        // com/aprism/loader/lowlevel/MethodHookRegistry and method fire.
        String asString = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return asString.contains("MethodHookRegistry") && asString.contains("fire");
    }

    /** Defines exactly one class by name. */
    private static final class SingleClassLoader extends ClassLoader {
        private final String name;
        private final byte[] bytes;

        SingleClassLoader(String name, byte[] bytes, ClassLoader parent) {
            super(parent);
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
            if (className.equals(name)) {
                Class<?> c = findLoadedClass(className);
                if (c == null) {
                    c = defineClass(className, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            return super.loadClass(className, resolve);
        }
    }
}

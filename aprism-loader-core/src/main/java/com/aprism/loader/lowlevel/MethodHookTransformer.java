package com.aprism.loader.lowlevel;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM {@link ClassVisitor} that injects a dispatch call into the entry of
 * every method that has a registered {@link MethodHookRegistry} hook.
 *
 * <p>Part of the v26.1-Alpha.8 lower-level API foundation (goal #2). For each
 * method of a class being transformed, if
 * {@link MethodHookRegistry#hasHook(String, String, String)} returns true the
 * visitor prepends:
 *
 * <pre>
 * MethodHookRegistry.fire("&lt;className&gt;.&lt;methodName&gt;&lt;descriptor&gt;");
 * </pre>
 *
 * <p>The injected call is placed at the very start of the method body, before
 * any existing bytecode, so hooks observe the method on every invocation
 * (an "on-enter" hook). Constructors and abstract/native methods are skipped.
 * The class name used for the hook key is the slashed internal name, matching
 * {@link MethodHookRegistry#hookKey}.
 *
 * <p>If no hooks are registered for any method of the class, no bytecode is
 * changed (the visitor passes through), so this pass is cheap when idle.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MethodHookTransformer extends ClassVisitor {

    private final String className;

    /**
     * @param api       the ASM API version
     * @param delegate  the downstream class visitor
     * @param className the slashed internal name of the class being visited
     */
    public MethodHookTransformer(int api, ClassVisitor delegate, String className) {
        super(api, delegate);
        this.className = className;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        // Skip abstract, native, and constructor-like methods: they have no
        // concrete body to hook (constructors are intentionally excluded from
        // the Alpha.8 hook surface).
        if (mv == null
                || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || "<init>".equals(name)
                || "<clinit>".equals(name)) {
            return mv;
        }
        if (!MethodHookRegistry.hasHook(className, name, descriptor)) {
            return mv;
        }
        return new HookInjectionVisitor(api, mv, className, name, descriptor);
    }

    /**
     * Prepends the {@code MethodHookRegistry.fire(key)} call to a method body.
     */
    private static final class HookInjectionVisitor extends MethodVisitor {

        private final String hookKey;
        private boolean injected;

        HookInjectionVisitor(int api, MethodVisitor mv, String className, String methodName,
                String descriptor) {
            super(api, mv);
            this.hookKey = MethodHookRegistry.hookKey(className, methodName, descriptor);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (!injected) {
                injected = true;
                super.visitLdcInsn(hookKey);
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/aprism/loader/lowlevel/MethodHookRegistry",
                        "fire", "(Ljava/lang/String;)V", false);
            }
        }
    }
}

package com.aprism.loader.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import net.neoforged.bus.api.IEventBus;

/**
 * Bridges NeoForge loader conventions onto Aprism.
 *
 * <p>NeoForge differs from Fabric in two fundamental ways:
 * <ul>
 *   <li>Entrypoints are NOT declared in the manifest. Instead, a mod's
 *       entrypoint is any class annotated with
 *       {@code net.neoforged.fml.common.Mod(@Mod)} whose annotation value
 *       matches a mod id in {@code META-INF/neoforge.mods.toml}. Aprism
 *       discovers these by scanning the mod jar's bytecode.</li>
 *   <li>The entrypoint's constructor IS the initialization. NeoForge injects
 *       the mod-scoped {@code IEventBus} into the constructor; after
 *       construction everything is event-driven (there are no per-phase
 *       lifecycle methods).</li>
 * </ul>
 *
 * <p>{@link #findModClasses(Path, String)} scans a jar for {@code @Mod}
 * classes via ASM (no class loading, so it is safe before the classloader is
 * populated). {@link #construct(Class, IEventBus)} instantiates a discovered
 * class, preferring a constructor that accepts an {@link IEventBus} and
 * falling back to the no-arg constructor.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class NeoForgeEntrypointBridge {

    /** The runtime-visible descriptor of the {@code @Mod} annotation. */
    private static final String MOD_ANNOTATION_DESC = "Lnet/neoforged/fml/common/Mod;";

    private NeoForgeEntrypointBridge() {
    }

    /**
     * Scans a mod jar for classes annotated with {@code @Mod} whose annotation
     * value matches the given mod id.
     *
     * <p>Uses ASM over raw bytecode so it works before the classloader knows
     * about the jar. Every {@code .class} entry is visited; classes whose
     * {@code @Mod.value()} equals {@code modId} are collected by binary name.
     *
     * @param jarFile the mod jar path
     * @param modId   the mod id to match against {@code @Mod.value()}
     * @return the binary names of matching {@code @Mod} classes (may be empty)
     */
    public static List<String> findModClasses(Path jarFile, String modId) {
        List<String> result = new ArrayList<>();
        try (FileSystem fs = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            try (Stream<Path> stream = Files.walk(fs.getPath("/"))) {
                List<Path> classFiles = stream
                        .filter(p -> p.toString().endsWith(".class"))
                        .filter(p -> !p.toString().contains("module-info"))
                        .toList();
                for (Path classFile : classFiles) {
                    String binaryName = scanForMod(classFile, modId);
                    if (binaryName != null) {
                        result.add(binaryName);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan mod jar for @Mod classes: " + jarFile, e);
        }
        return result;
    }

    /**
     * Reads a single class file and returns its binary name if it carries a
     * {@code @Mod} annotation whose value matches {@code modId}.
     *
     * @param classFile the class file path (inside the jar filesystem)
     * @param modId     the mod id to match
     * @return the binary name, or {@code null} if not a matching {@code @Mod} class
     */
    private static String scanForMod(Path classFile, String modId) {
        try (InputStream in = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(in);
            ModAnnotationFinder finder = new ModAnnotationFinder(modId);
            reader.accept(finder, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return finder.isMatch() ? finder.className.replace('/', '.') : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Instantiates a NeoForge entrypoint class, injecting the given event bus.
     * Prefers a constructor accepting a single {@link IEventBus} argument; if
     * absent, falls back to the no-arg constructor.
     *
     * @param clazz    the {@code @Mod} entrypoint class
     * @param eventBus the mod-scoped event bus to inject
     * @return the constructed mod instance
     */
    public static Object construct(Class<?> clazz, IEventBus eventBus) {
        try {
            try {
                var ctor = clazz.getDeclaredConstructor(IEventBus.class);
                ctor.setAccessible(true);
                return ctor.newInstance(eventBus);
            } catch (NoSuchMethodException ignored) {
                var ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            }
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Failed to construct NeoForge entrypoint "
                    + clazz.getName(), cause);
        }
    }

    /**
     * ASM visitor that detects a runtime-visible {@code @Mod} annotation and
     * captures its {@code value()} element.
     */
    private static final class ModAnnotationFinder extends ClassVisitor {
        private final String modId;
        String className;
        private String annotationValue;

        ModAnnotationFinder(String modId) {
            super(Opcodes.ASM9);
            this.modId = modId;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            this.className = name;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (visible && MOD_ANNOTATION_DESC.equals(descriptor)) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name)) {
                            annotationValue = String.valueOf(value);
                        }
                    }
                };
            }
            return super.visitAnnotation(descriptor, visible);
        }

        boolean isMatch() {
            return modId != null && modId.equals(annotationValue);
        }
    }
}

package com.aprism.loader.bridge;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Bridges LiteLoader mod conventions onto Aprism.
 *
 * <p>LiteLoader differs from the Fabric/Forge family:
 * <ul>
 *   <li>Entrypoints are NOT declared in the manifest. The mod's entrypoint is
 *       a class that implements {@code com.mumfrey.liteloader.core.LiteMod}
 *       (the base mod interface; all LiteLoader callback interfaces derive
 *       from it). Historically LiteLoader also required the class name to
 *       begin with {@code LiteMod}, so classes matching that naming convention
 *       are accepted as well.</li>
 *   <li>Initialization is a single {@code init(File)} call made early in the
 *       startup sequence, where the {@code File} argument is the mod's config
 *       folder. There are no per-phase lifecycle methods; all other Aprism
 *       phases are no-ops for LiteLoader mods.</li>
 * </ul>
 *
 * <p>{@link #findModClasses(Path)} scans a {@code .litemod} archive for
 * candidate entrypoint classes via ASM (no class loading, safe before the
 * classloader is populated). {@link #invokeInit(Object, File)} instantiates
 * the discovery result's {@code init(File)} method reflectively.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class LiteLoaderEntrypointBridge {

    /** The internal descriptor of the LiteLoader base {@code LiteMod} interface. */
    private static final String LITEMOD_INTERFACE = "com/mumfrey/liteloader/core/LiteMod";

    /** The classic LiteLoader class-name prefix for mod entrypoint classes. */
    private static final String LITEMOD_CLASS_PREFIX = "LiteMod";

    private LiteLoaderEntrypointBridge() {
    }

    /**
     * Scans a {@code .litemod} archive for LiteLoader entrypoint classes.
     *
     * <p>A class qualifies if it directly implements the {@code LiteMod}
     * interface, or if its simple class name begins with {@code LiteMod}
     * (the historical naming convention). Abstract classes and interfaces are
     * excluded. Uses ASM over raw bytecode so it works before the classloader
     * knows about the archive.
     *
     * @param archiveFile the {@code .litemod} archive path
     * @return the binary names of candidate entrypoint classes (may be empty)
     */
    public static List<String> findModClasses(Path archiveFile) {
        List<String> result = new ArrayList<>();
        try (FileSystem fs = FileSystems.newFileSystem(archiveFile, (ClassLoader) null)) {
            try (Stream<Path> stream = Files.walk(fs.getPath("/"))) {
                List<Path> classFiles = stream
                        .filter(p -> p.toString().endsWith(".class"))
                        .filter(p -> !p.toString().contains("module-info"))
                        .toList();
                for (Path classFile : classFiles) {
                    String binaryName = scanForMod(classFile);
                    if (binaryName != null) {
                        result.add(binaryName);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to scan .litemod archive for LiteMod classes: " + archiveFile, e);
        }
        return result;
    }

    /**
     * Reads a single class file and returns its binary name if it is a
     * LiteLoader entrypoint candidate.
     *
     * @param classFile the class file path (inside the archive filesystem)
     * @return the binary name, or {@code null} if not a candidate
     */
    private static String scanForMod(Path classFile) {
        try (InputStream in = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(in);
            LiteModFinder finder = new LiteModFinder();
            reader.accept(finder, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return finder.isMatch() ? finder.className.replace('/', '.') : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Invokes the LiteLoader {@code init(File)} lifecycle method on the given
     * mod instance.
     *
     * @param instance     the instantiated LiteLoader mod
     * @param configFolder the mod's config folder
     * @return {@code true} if {@code init(File)} was found and invoked
     */
    public static boolean invokeInit(Object instance, File configFolder) {
        Method method = findInitMethod(instance.getClass());
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(instance, configFolder);
            return true;
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("LiteLoader init(File) failed on "
                    + instance.getClass().getName(), cause);
        }
    }

    /**
     * Finds the {@code init(File)} method on the class or any of its
     * superclasses.
     *
     * @param clazz the mod class
     * @return the method, or {@code null} if not found
     */
    private static Method findInitMethod(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod("init", File.class);
            } catch (NoSuchMethodException ignored) {
                // continue up the hierarchy
            }
        }
        return null;
    }

    /**
     * ASM visitor that detects a LiteLoader entrypoint candidate: a concrete
     * class that directly implements {@code LiteMod} or whose simple name
     * starts with {@code LiteMod}.
     */
    private static final class LiteModFinder extends ClassVisitor {
        String className;
        private boolean isInterfaceOrAbstract;
        private boolean implementsLiteMod;
        private boolean namedLikeLiteMod;

        LiteModFinder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            this.className = name;
            this.isInterfaceOrAbstract = (access & Opcodes.ACC_INTERFACE) != 0
                    || (access & Opcodes.ACC_ABSTRACT) != 0;
            if (interfaces != null) {
                for (String iface : interfaces) {
                    if (LITEMOD_INTERFACE.equals(iface)) {
                        implementsLiteMod = true;
                    }
                }
            }
            String simple = name.substring(name.lastIndexOf('/') + 1);
            this.namedLikeLiteMod = simple.startsWith(LITEMOD_CLASS_PREFIX);
        }

        boolean isMatch() {
            return !isInterfaceOrAbstract && (implementsLiteMod || namedLikeLiteMod);
        }
    }
}

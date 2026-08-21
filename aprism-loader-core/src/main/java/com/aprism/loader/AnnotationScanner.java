package com.aprism.loader;

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
import org.objectweb.asm.Type;

import com.aprism.api.AprismMod;

/**
 * Scans a mod's embedded jar(s) for classes annotated with
 * {@link AprismMod @AprismMod}, without loading them. Uses ASM to read
 * runtime-visible annotations from class files directly.
 *
 * <p>When a mod manifest does not declare an explicit {@code entrypoints}
 * map (or the {@code main} key is absent), the runtime delegates to this
 * scanner to discover entrypoint classes via annotation scanning.
 *
 * <p>The scanner reads class files from the mod's extracted jar paths (the
 * temp directory where .aje embedded jars were extracted). It returns the
 * fully-qualified class names of all classes carrying {@code @AprismMod}
 * whose {@code value()} either is empty or matches the expected mod id.
 *
 * @author BlockConnect@StarsailsClover
 * @since v26.5-Alpha.1
 */
public final class AnnotationScanner {

    /** The ASM API version used by the scanner. */
    private static final int ASM_API = Opcodes.ASM9;

    /** The internal name of the {@link AprismMod} annotation. */
    private static final String APRISM_MOD_DESC =
            Type.getDescriptor(AprismMod.class);

    private AnnotationScanner() {
    }

    /**
     * Scans the given jar paths for classes annotated with
     * {@link AprismMod @AprismMod}.
     *
     * @param jarPaths  the jar file paths to scan (typically the mod's
     *                  extracted embedded jars from a .aje archive)
     * @param expectedModId the mod id from the manifest; used to filter
     *                      {@code @AprismMod(value = "...")} entries.
     *                      Pass {@code null} or empty to accept all
     *                      {@code @AprismMod} classes regardless of value.
     * @return the list of fully-qualified class names (using dots) that
     *         carry a matching {@code @AprismMod} annotation, in scan order
     */
    public static List<String> scanModEntrypoints(List<Path> jarPaths, String expectedModId) {
        List<String> results = new ArrayList<>();
        String normalizedId = expectedModId == null ? "" : expectedModId.trim();
        for (Path jarPath : jarPaths) {
            scanJar(jarPath, normalizedId, results);
        }
        return results;
    }

    /**
     * Scans a single jar file for {@code @AprismMod}-annotated classes.
     *
     * @param jarPath      the jar file path
     * @param expectedModId the normalized mod id (empty = accept all)
     * @param results      the accumulator for discovered class names
     */
    private static void scanJar(Path jarPath, String expectedModId, List<String> results) {
        if (!Files.isRegularFile(jarPath)) {
            return;
        }
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
            Path root = fs.getPath("/");
            try (Stream<Path> entries = Files.walk(root)) {
                entries.filter(p -> p.toString().endsWith(".class"))
                       .filter(p -> !p.toString().endsWith("module-info.class"))
                       .filter(p -> !p.toString().endsWith("package-info.class"))
                       .forEach(classFile -> scanClassFile(classFile, expectedModId, results));
            }
        } catch (IOException e) {
            // A corrupt or unreadable jar is logged but does not abort the scan
            // of other jars.
            java.util.logging.Logger.getLogger("aprism.loader")
                    .warning("AnnotationScanner: failed to scan jar " + jarPath + ": " + e.getMessage());
        }
    }

    /**
     * Reads a single class file and checks for a matching
     * {@code @AprismMod} annotation.
     *
     * @param classFile     the class file path within the jar filesystem
     * @param expectedModId the normalized mod id (empty = accept all)
     * @param results       the accumulator for discovered class names
     */
    private static void scanClassFile(Path classFile, String expectedModId, List<String> results) {
        try (InputStream is = Files.newInputStream(classFile)) {
            ClassReader reader = new ClassReader(is);
            ModAnnotationVisitor visitor = new ModAnnotationVisitor(expectedModId);
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (visitor.found()) {
                results.add(visitor.className());
            }
        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            // Skip unreadable class files; they may be malformed or non-standard
        }
    }

    /**
     * ASM visitor that detects {@code @AprismMod} on a class and extracts the
     * {@code value()} attribute for filtering.
     */
    private static final class ModAnnotationVisitor extends ClassVisitor {

        private final String expectedModId;
        private String className;
        private boolean hasAprismMod;
        private String annotationValue;

        ModAnnotationVisitor(String expectedModId) {
            super(ASM_API);
            this.expectedModId = expectedModId;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                         String superName, String[] interfaces) {
            this.className = name;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (visible && APRISM_MOD_DESC.equals(descriptor)) {
                hasAprismMod = true;
                return new AnnotationVisitor(ASM_API) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name) && value instanceof String s) {
                            annotationValue = s;
                        }
                    }
                };
            }
            return null;
        }

        boolean found() {
            if (!hasAprismMod) {
                return false;
            }
            // If the annotation has no value (or empty), accept unconditionally
            if (annotationValue == null || annotationValue.isBlank()) {
                return true;
            }
            // If an expected mod id is specified, the annotation value must match
            if (expectedModId == null || expectedModId.isEmpty()) {
                return true;
            }
            return expectedModId.equals(annotationValue.trim());
        }

        String className() {
            // Convert internal name (com/example/Foo) to dotted form (com.example.Foo)
            return className == null ? "" : className.replace('/', '.');
        }
    }
}

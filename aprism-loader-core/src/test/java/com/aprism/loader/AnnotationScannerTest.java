package com.aprism.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.AnnotationVisitor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AnnotationScanner}. Builds synthetic jars containing
 * classes with and without the {@link com.aprism.api.AprismMod @AprismMod}
 * annotation, then verifies the scanner discovers the correct entrypoints.
 *
 * @author BlockConnect@StarsailsClover
 * @since v26.5-Alpha.1
 */
class AnnotationScannerTest {

    private static final String APRISM_MOD_DESC =
            org.objectweb.asm.Type.getDescriptor(com.aprism.api.AprismMod.class);

    @TempDir
    Path tempDir;

    // ── Basic discovery ──────────────────────────────────────────

    @Test
    void scanFindsAnnotatedClass() throws IOException {
        Path jar = buildJar("annotated.jar", List.of(
                new ClassEntry("com/example/MyMod", classBytes("com/example/MyMod", true, "")),
                new ClassEntry("com/example/Helper", classBytes("com/example/Helper", false, null))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "mymod");
        assertEquals(List.of("com.example.MyMod"), result);
    }

    @Test
    void scanReturnsEmptyForJarWithoutAnnotation() throws IOException {
        Path jar = buildJar("plain.jar", List.of(
                new ClassEntry("com/example/Plain", classBytes("com/example/Plain", false, null))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "mymod");
        assertTrue(result.isEmpty());
    }

    @Test
    void scanReturnsEmptyForEmptyJarList() {
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(), "mymod");
        assertTrue(result.isEmpty());
    }

    @Test
    void scanSkipsNonExistentFile() {
        List<String> result = AnnotationScanner.scanModEntrypoints(
                List.of(tempDir.resolve("nonexistent.jar")), "mymod");
        assertTrue(result.isEmpty());
    }

    // ── Value filtering ──────────────────────────────────────────

    @Test
    void scanFiltersByModIdWhenValueSpecified() throws IOException {
        Path jar = buildJar("filtered.jar", List.of(
                new ClassEntry("com/example/MatchingMod", classBytes("com/example/MatchingMod", true, "mymod")),
                new ClassEntry("com/example/OtherMod", classBytes("com/example/OtherMod", true, "othermod"))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "mymod");
        assertEquals(List.of("com.example.MatchingMod"), result);
    }

    @Test
    void scanAcceptsAllWhenExpectedIdIsEmpty() throws IOException {
        Path jar = buildJar("accept-all.jar", List.of(
                new ClassEntry("com/example/ModA", classBytes("com/example/ModA", true, "modA")),
                new ClassEntry("com/example/ModB", classBytes("com/example/ModB", true, "modB"))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "");
        assertEquals(2, result.size());
        assertTrue(result.contains("com.example.ModA"));
        assertTrue(result.contains("com.example.ModB"));
    }

    @Test
    void scanAcceptsAllWhenExpectedIdIsNull() throws IOException {
        Path jar = buildJar("null-id.jar", List.of(
                new ClassEntry("com/example/ModC", classBytes("com/example/ModC", true, "anything"))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), null);
        assertEquals(List.of("com.example.ModC"), result);
    }

    @Test
    void scanAcceptsAnnotatedClassWithEmptyValue() throws IOException {
        Path jar = buildJar("empty-value.jar", List.of(
                new ClassEntry("com/example/EmptyValueMod", classBytes("com/example/EmptyValueMod", true, ""))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "anything");
        assertEquals(List.of("com.example.EmptyValueMod"), result);
    }

    // ── Multiple jars ───────────────────────────────────────────

    @Test
    void scanMultipleJarsAccumulatesResults() throws IOException {
        Path jar1 = buildJar("jar1.jar", List.of(
                new ClassEntry("com/example/Mod1", classBytes("com/example/Mod1", true, ""))
        ));
        Path jar2 = buildJar("jar2.jar", List.of(
                new ClassEntry("com/example/Mod2", classBytes("com/example/Mod2", true, ""))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(
                List.of(jar1, jar2), "");
        assertEquals(2, result.size());
        assertTrue(result.contains("com.example.Mod1"));
        assertTrue(result.contains("com.example.Mod2"));
    }

    // ── Edge cases ──────────────────────────────────────────────

    @Test
    void scanSkipsModuleInfoAndPackageInfo() throws IOException {
        Path jar = buildJar("skip-info.jar", List.of(
                new ClassEntry("module-info", classBytes("module-info", false, null)),

                new ClassEntry("com/example/package-info", classBytes("com/example/package-info", false, null)),
                new ClassEntry("com/example/RealMod", classBytes("com/example/RealMod", true, ""))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "");
        assertEquals(List.of("com.example.RealMod"), result);
    }

    @Test
    void scanHandlesMultipleAnnotatedClassesInOneJar() throws IOException {
        Path jar = buildJar("multi.jar", List.of(
                new ClassEntry("com/example/ModA", classBytes("com/example/ModA", true, "")),
                new ClassEntry("com/example/ModB", classBytes("com/example/ModB", true, ""))
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "");
        assertEquals(2, result.size());
    }

    @Test
    void scanSkipsAnnotationOnNonVisibleAnnotation() throws IOException {
        byte[] classBytes = buildClassWithForeignAnnotation("com/example/FakeMod");
        Path jar = buildJar("fake.jar", List.of(
                new ClassEntry("com/example/FakeMod", classBytes)
        ));
        List<String> result = AnnotationScanner.scanModEntrypoints(List.of(jar), "");
        assertTrue(result.isEmpty());
    }

    // ── Helpers ─────────────────────────────────────────────────

    private record ClassEntry(String name, byte[] bytes) {}

    private static byte[] classBytes(String className, boolean annotated, String annotationValue) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, className.replace('.', '/'),
                null, "java/lang/Object", null);
        if (annotated) {
            AnnotationVisitor av = cw.visitAnnotation(APRISM_MOD_DESC, true);
            if (annotationValue != null) {
                av.visit("value", annotationValue);
            }
            av.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] buildClassWithForeignAnnotation(String className) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, className.replace('.', '/'),
                null, "java/lang/Object", null);
        cw.visitAnnotation("Lcom/example/FakeAnnotation;", true).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private Path buildJar(String name, List<ClassEntry> entries) throws IOException {
        Path jarPath = tempDir.resolve(name);
        try (JarOutputStream jos = new JarOutputStream(
                Files.newOutputStream(jarPath), new Manifest())) {
            for (ClassEntry entry : entries) {
                jos.putNextEntry(new JarEntry(entry.name() + ".class"));
                jos.write(entry.bytes());
                jos.closeEntry();
            }
        }
        return jarPath;
    }
}

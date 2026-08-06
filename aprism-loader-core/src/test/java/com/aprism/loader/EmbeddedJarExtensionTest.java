package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.aprism.api.IAprismExtension;

/**
 * Integration test verifying that a real {@code .aep} archive containing an
 * embedded jar with a compiled entrypoint class is correctly loaded by the
 * Aprism runtime. Unlike the classpath-resident test fixtures, this test
 * generates a unique class via ASM, packages it inside a jar embedded in the
 * {@code .aep}, and verifies that the runtime extracts the jar, loads the
 * class from it, and invokes {@code onInitialize}.
 *
 * @author BlockConnect@StarsailsClover
 */
class EmbeddedJarExtensionTest {

    /** The fully-qualified class name of the generated extension. */
    private static final String JAR_ONLY_CLASS = "com.aprism.loader.testexts.JarOnlyExtension";
    private static final String JAR_ONLY_INTERNAL = "com/aprism/loader/testexts/JarOnlyExtension";

    @TempDir
    Path gameRoot;

    /**
     * Generates a class implementing {@link IAprismExtension} via ASM. The
     * generated class has a static boolean field {@code initialized} that is
     * set to {@code true} when {@code onInitialize} is invoked. The class is
     * NOT on the test classpath; it exists only inside the embedded jar.
     *
     * @return the generated class bytecode
     */
    private static byte[] generateJarOnlyExtensionClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, JAR_ONLY_INTERNAL, null,
                "java/lang/Object", new String[]{"com/aprism/api/IAprismExtension"});

        // static boolean initialized = false
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "initialized", "Z", null, Boolean.FALSE).visitEnd();

        // <init>()V
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        // onInitialize(ExtensionContext)V
        MethodVisitor onInit = cw.visitMethod(Opcodes.ACC_PUBLIC,
                "onInitialize", "(Lcom/aprism/api/ExtensionContext;)V", null, null);
        onInit.visitCode();
        onInit.visitInsn(Opcodes.ICONST_1);
        onInit.visitFieldInsn(Opcodes.PUTSTATIC, JAR_ONLY_INTERNAL, "initialized", "Z");
        onInit.visitInsn(Opcodes.RETURN);
        onInit.visitMaxs(1, 1);
        onInit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Writes a jar (zip) containing a single binary entry.
     *
     * @param jarFile  the destination jar file
     * @param entry    the entry path inside the jar (e.g. "com/Test.class")
     * @param bytes    the binary content
     * @throws IOException if the jar cannot be written
     */
    private static void writeBinaryJar(Path jarFile, String entry, byte[] bytes) throws IOException {
        Files.createDirectories(jarFile.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(bytes);
            zos.closeEntry();
        }
    }

    /**
     * Writes a {@code .aep} archive containing the manifest and an embedded
     * jar at the root level.
     *
     * @param aepFile   the destination .aep file
     * @param manifest  the aprism.extension.json content
     * @param jarName   the name of the embedded jar entry
     * @param jarBytes  the embedded jar's binary content
     * @throws IOException if the archive cannot be written
     */
    private static void writeAepWithEmbeddedJar(Path aepFile, String manifest,
            String jarName, byte[] jarBytes) throws IOException {
        Files.createDirectories(aepFile.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(aepFile))) {
            zos.putNextEntry(new ZipEntry("aprism.extension.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(jarName));
            zos.write(jarBytes);
            zos.closeEntry();
        }
    }

    @BeforeEach
    void setUp() {
        AprismRuntime.instance().initialize(null, "26.0.0", "JE", "1.21.4");
    }

    @AfterEach
    void tearDown() {
        AprismRuntime.instance().shutdown();
    }

    @Test
    void extensionLoadedFromEmbeddedJar() throws Exception {
        // Generate the class bytecode
        byte[] classBytes = generateJarOnlyExtensionClass();

        // Package the class into a jar
        Path tempJar = gameRoot.resolve("temp").resolve("JarOnlyExtension.jar");
        writeBinaryJar(tempJar, JAR_ONLY_INTERNAL + ".class", classBytes);
        byte[] jarBytes = Files.readAllBytes(tempJar);

        // Build the .aep manifest
        String manifest = """
                {
                  "extensionId": "jar-only-ext",
                  "type": "api-extension",
                  "aprismRange": "[26.0.0,27.0.0)",
                  "loaderKey": null,
                  "loaderRange": null,
                  "mcEdit": null,
                  "mcVersion": null,
                  "entrypoint": "%s",
                  "provides": [],
                  "depends": {}
                }
                """.formatted(JAR_ONLY_CLASS);

        // Write the .aep with the embedded jar
        Path aepFile = gameRoot.resolve("aprism-extensions").resolve("JarOnly.aep");
        writeAepWithEmbeddedJar(aepFile, manifest, "JarOnlyExtension.jar", jarBytes);

        // Load extensions
        AprismRuntime.instance().loadExtensions(gameRoot.resolve("aprism-extensions"));

        // Verify the extension container was created
        LoadedExtensionContainer container = AprismRuntime.instance().getExtension("jar-only-ext");
        assertThat(container)
                .as("Extension container should be loaded")
                .isNotNull();

        // Verify the entrypoint instance was created
        assertThat(container.getInstance())
                .as("Entrypoint instance should be instantiated from the embedded jar")
                .isNotNull();

        // Verify the instance implements IAprismExtension
        assertThat(container.getInstance())
                .as("Entrypoint should implement IAprismExtension")
                .isInstanceOf(IAprismExtension.class);

        // Reflectively read the static 'initialized' field to verify onInitialize was called
        Class<?> loadedClass = container.getInstance().getClass();
        assertThat(loadedClass.getName())
                .as("The loaded class should be the JarOnlyExtension (not a classpath class)")
                .isEqualTo(JAR_ONLY_CLASS);

        java.lang.reflect.Field initializedField = loadedClass.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        boolean initialized = initializedField.getBoolean(null);
        assertThat(initialized)
                .as("onInitialize should have been invoked, setting 'initialized' to true")
                .isTrue();
    }

    @Test
    void classNotResolvableFromClasspathBeforeLoad() throws Exception {
        // Verify that the JarOnlyExtension class is NOT on the test classpath.
        // This proves that the previous test loaded it from the embedded jar,
        // not from the classpath.
        ClassLoader sysCl = ClassLoader.getSystemClassLoader();
        java.io.InputStream is = sysCl.getResourceAsStream(
                JAR_ONLY_INTERNAL + ".class");
        assertThat(is)
                .as("JarOnlyExtension.class should not be on the system classpath")
                .isNull();
    }
}

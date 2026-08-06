package com.aprism.packaging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that assembles a {@code .aje} archive: the manifest, the mod
 * jars, per-platform subdirectories, resources, and mixin configs.
 *
 * @author BlockConnect@StarsailsClover
 */
public class PackageAjeTask extends DefaultTask {

    /**
     * Assembles the {@code .aje} archive from the configured inputs.
     */
    @TaskAction
    public void packageAje() {
        AprismPackagingExtension ext = getProject().getExtensions()
                .findByType(AprismPackagingExtension.class);
        if (ext == null) {
            ext = new AprismPackagingExtension();
        }

        File outputDir = new File(ext.getOutputDir());
        outputDir.mkdirs();
        File output = new File(outputDir, getProject().getName() + ".aje");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            addManifest(zos, ext);
            addJars(zos);
            addPlatforms(zos, ext);
            addMixins(zos, ext);
        } catch (IOException e) {
            throw new RuntimeException("Failed to package .aje", e);
        }
    }

    /**
     * Adds the manifest file to the archive root if it exists.
     *
     * @param zos  the zip output stream
     * @param ext  the packaging extension
     * @throws IOException if writing fails
     */
    private void addManifest(ZipOutputStream zos, AprismPackagingExtension ext) throws IOException {
        File manifest = getProject().file(ext.getManifestFile());
        if (manifest.isFile()) {
            zos.putNextEntry(new ZipEntry("aprism.manifest.json"));
            zos.write(Files.readAllBytes(manifest.toPath()));
            zos.closeEntry();
        }
    }

    /**
     * Adds each jar from {@code build/libs} into a {@code jars/} directory.
     *
     * @param zos the zip output stream
     * @throws IOException if writing fails
     */
    private void addJars(ZipOutputStream zos) throws IOException {
        File libs = new File(getProject().getBuildDir(), "libs");
        if (!libs.isDirectory()) {
            return;
        }
        File[] jars = libs.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            zos.putNextEntry(new ZipEntry("jars/" + jar.getName()));
            zos.write(Files.readAllBytes(jar.toPath()));
            zos.closeEntry();
        }
    }

    /**
     * Adds a placeholder entry for each included platform.
     *
     * @param zos  the zip output stream
     * @param ext  the packaging extension
     * @throws IOException if writing fails
     */
    private void addPlatforms(ZipOutputStream zos, AprismPackagingExtension ext) throws IOException {
        for (String platform : ext.getIncludePlatforms()) {
            zos.putNextEntry(new ZipEntry("platforms/" + platform + "/.keep"));
            zos.write(new byte[0]);
            zos.closeEntry();
        }
    }

    /**
     * Adds a {@code mixins/} directory placeholder.
     *
     * @param zos  the zip output stream
     * @param ext  the packaging extension
     * @throws IOException if writing fails
     */
    private void addMixins(ZipOutputStream zos, AprismPackagingExtension ext) throws IOException {
        zos.putNextEntry(new ZipEntry("mixins/.keep"));
        zos.write(new byte[0]);
        zos.closeEntry();
    }
}

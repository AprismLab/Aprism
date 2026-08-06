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
 * Gradle task that assembles a {@code .abe} archive for Bedrock Edition mods:
 * the manifest plus {@code behavior_pack/}, {@code resource_pack/},
 * {@code native/}, and {@code scripts/} directories.
 *
 * @author BlockConnect@StarsailsClover
 */
public class PackageAbeTask extends DefaultTask {

    /**
     * Assembles the {@code .abe} archive from the configured inputs.
     */
    @TaskAction
    public void packageAbe() {
        AprismPackagingExtension ext = getProject().getExtensions()
                .findByType(AprismPackagingExtension.class);
        if (ext == null) {
            ext = new AprismPackagingExtension();
        }

        File outputDir = new File(ext.getOutputDir());
        outputDir.mkdirs();
        File output = new File(outputDir, getProject().getName() + ".abe");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            addManifest(zos, ext);
            addEntry(zos, "behavior_pack/.keep");
            addEntry(zos, "resource_pack/.keep");
            addEntry(zos, "native/.keep");
            addEntry(zos, "scripts/.keep");
        } catch (IOException e) {
            throw new RuntimeException("Failed to package .abe", e);
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
     * Adds an empty placeholder entry.
     *
     * @param zos  the zip output stream
     * @param name the entry name
     * @throws IOException if writing fails
     */
    private void addEntry(ZipOutputStream zos, String name) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(new byte[0]);
        zos.closeEntry();
    }
}

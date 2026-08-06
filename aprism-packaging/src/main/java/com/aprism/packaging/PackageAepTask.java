package com.aprism.packaging;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gradle task that assembles a {@code .aep} (Aprism Extension) pack from the
 * extension jar and {@code aprism.extension.json}.
 *
 * @author BlockConnect@StarsailsClover
 */
public abstract class PackageAepTask extends DefaultTask {

    private FileCollection inputJars;
    private File manifestFile;
    private File outputFile;

    @InputFiles
    public FileCollection getInputJars() {
        return inputJars;
    }

    public void setInputJars(FileCollection inputJars) {
        this.inputJars = inputJars;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    @TaskAction
    public void packageAep() throws IOException {
        getProject().getLogger().lifecycle("Packaging .aep extension to {}", outputFile);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            // Add the extension manifest at the root
            if (manifestFile != null && manifestFile.exists()) {
                addToZip(zos, manifestFile.toPath(), "aprism.extension.json");
            }
            // Add all input jars at the root
            for (File jar : inputJars) {
                addToZip(zos, jar.toPath(), jar.getName());
            }
        }
    }

    private void addToZip(ZipOutputStream zos, Path source, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(java.nio.file.Files.readAllBytes(source));
        zos.closeEntry();
    }
}

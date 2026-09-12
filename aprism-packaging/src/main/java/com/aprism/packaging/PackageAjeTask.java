package com.aprism.packaging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that assembles a {@code .aje} archive following the canonical
 * Aprism-native structure (see Doc 07 / FACT.md 9.13):
 *
 * <pre>
 * &lt;modid&gt;-&lt;version&gt;.aje
 * +-- aprism.manifest.json
 * +-- &lt;modid&gt;.jar          (the Aprism-native main jar)
 * +-- resources/            (optional)
 * +-- mixins/               (optional)
 * +-- lib/                  (optional embedded dependencies)
 * </pre>
 *
 * <p>Per-loader subdirectories and loader-specific jars are NOT allowed in a
 * {@code .aje}; it is consumed exclusively by the Aprism native loader. A
 * {@code checksums.txt} (archive SHA-256 plus per-entry SHA-256) is written
 * next to the archive.
 *
 * <p>All inputs are typed properties so the task is configuration-cache
 * compatible.
 *
 * @author BlockConnect@StarsailsClover
 */
public abstract class PackageAjeTask extends DefaultTask {

    /**
     * @return the Aprism manifest file (required input)
     */
    @InputFile
    public abstract RegularFileProperty getManifestFile();

    /**
     * @return the main jar to embed; convention-wired to the {@code jar}
     *         task output by the plugin
     */
    @InputFile
    @Optional
    public abstract RegularFileProperty getMainJar();

    /**
     * @return files copied into {@code resources/}
     */
    @InputFiles
    public abstract ConfigurableFileCollection getResources();

    /**
     * @return files copied into {@code mixins/}
     */
    @InputFiles
    public abstract ConfigurableFileCollection getMixins();

    /**
     * @return files copied into {@code lib/}
     */
    @InputFiles
    public abstract ConfigurableFileCollection getLib();

    /**
     * @return the root directory used to relativize {@code resources/} entries
     */
    @Internal
    public abstract Property<String> getResourcesRoot();

    /**
     * @return the root directory used to relativize {@code mixins/} entries
     */
    @Internal
    public abstract Property<String> getMixinsRoot();

    /**
     * @return the root directory used to relativize {@code lib/} entries
     */
    @Internal
    public abstract Property<String> getLibRoot();

    /**
     * @return the directory the archive is written to
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /**
     * @return the loader version the mod is compiled against; the manifest
     *         floor must not be below it (v26.9-Alpha.8)
     */
    @Input
    @Optional
    public abstract Property<String> getAprismBaseline();

    /**
     * @return whether the manifest baseline check runs (default true)
     */
    @Input
    public abstract Property<Boolean> getBaselineCheck();

    /**
     * Assembles the {@code .aje} archive from the configured inputs.
     *
     * @throws IOException if the archive cannot be written
     */
    @TaskAction
    public void packageAje() throws IOException {
        File manifestFile = getManifestFile().getAsFile().get();
        JsonObject manifest = parseManifest(manifestFile);
        String modId = requireField(manifest, "id");
        String version = requireField(manifest, "version");
        enforceBaselineFloor(manifest);

        File mainJar = getMainJar().getAsFile().getOrNull();
        if (mainJar == null || !mainJar.isFile()) {
            throw new GradleException("No main jar available for .aje packaging; "
                    + "run the jar task first or configure aprismPackaging.mainJar");
        }

        File outputDir = getOutputDir().get().getAsFile();
        outputDir.mkdirs();
        File output = new File(outputDir, modId + "-" + version + ".aje");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            addFile(zos, manifestFile.toPath(), "aprism.manifest.json");
            addFile(zos, mainJar.toPath(), modId + ".jar");
            addCollection(zos, getResources(), getResourcesRoot(), "resources");
            addCollection(zos, getMixins(), getMixinsRoot(), "mixins");
            addCollection(zos, getLib(), getLibRoot(), "lib");
        }

        try {
            writeChecksums(output);
        } catch (NoSuchAlgorithmException e) {
            throw new GradleException("SHA-256 unavailable", e);
        }

        getLogger().lifecycle("Packaged {} (mod: {} {})", output, modId, version);
    }

    /**
     * Refuses to package an archive whose {@code depends.aprism} floor is
     * below the declared compile baseline (v26.9-Alpha.8). This is the guard
     * for the class of defect where an artifact compiled against a newer
     * loader advertises an old floor, letting an old loader attempt the load
     * and fail inside the agent.
     *
     * @param manifest the parsed manifest
     */
    private void enforceBaselineFloor(JsonObject manifest) {
        if (!getBaselineCheck().getOrElse(Boolean.TRUE)) {
            getLogger().info("Manifest baseline check disabled for {}",
                    getManifestFile().getAsFile().get().getName());
            return;
        }
        String baseline = getAprismBaseline().getOrNull();
        String dependsAprism = null;
        JsonElement depends = manifest.get("depends");
        if (depends != null && depends.isJsonObject()) {
            JsonElement aprism = depends.getAsJsonObject().get("aprism");
            if (aprism != null && aprism.isJsonPrimitive()) {
                dependsAprism = aprism.getAsString();
            }
        }
        ManifestBaselineCheck.Result result =
                ManifestBaselineCheck.check(dependsAprism, baseline);
        if (!result.ok()) {
            StringBuilder message = new StringBuilder(
                    "Aprism packaging refused: manifest baseline check failed for ")
                    .append(getManifestFile().getAsFile().get().getName()).append('.');
            for (String problem : result.problems()) {
                message.append(System.lineSeparator()).append("  - ").append(problem);
            }
            message.append(System.lineSeparator())
                    .append("  Set aprismPackaging.aprismBaseline (or fix "
                            + "depends.aprism), or disable with "
                            + "aprismPackaging.baselineCheck = false.");
            throw new GradleException(message.toString());
        }
        getLogger().info("Manifest baseline check passed (baseline {}, floor {})",
                baseline == null ? "<unset>" : baseline, dependsAprism);
    }

    /**
     * Adds a single file entry to the archive.
     *
     * @param zos       the zip output stream
     * @param source    the source file
     * @param entryName the zip entry name
     * @throws IOException if writing fails
     */
    private void addFile(ZipOutputStream zos, Path source, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(Files.readAllBytes(source));
        zos.closeEntry();
    }

    /**
     * Adds every file of a collection under the given prefix, relativized
     * against the collection's root directory.
     *
     * @param zos    the zip output stream
     * @param col    the file collection
     * @param root   the root directory property (unset = skip)
     * @param prefix the zip entry prefix
     * @throws IOException if writing fails
     */
    private void addCollection(ZipOutputStream zos, ConfigurableFileCollection col,
            Property<String> root, String prefix) throws IOException {
        if (!root.isPresent()) {
            return;
        }
        Path rootPath = Path.of(root.get());
        for (File f : col.getFiles()) {
            String entryName = prefix + "/"
                    + rootPath.relativize(f.toPath()).toString().replace('\\', '/');
            addFile(zos, f.toPath(), entryName);
        }
    }

    /**
     * Parses the manifest JSON and returns the root object.
     *
     * @param manifestFile the manifest file
     * @return the parsed root object
     */
    private JsonObject parseManifest(File manifestFile) {
        try {
            String json = Files.readString(manifestFile.toPath());
            return new Gson().fromJson(json, JsonObject.class);
        } catch (IOException | RuntimeException e) {
            throw new GradleException("Failed to parse manifest: " + manifestFile, e);
        }
    }

    /**
     * Reads a required string field from the manifest.
     *
     * @param manifest the manifest root object
     * @param field    the field name
     * @return the field value
     */
    private static String requireField(JsonObject manifest, String field) {
        if (manifest == null || !manifest.has(field) || manifest.get(field).isJsonNull()) {
            throw new GradleException("Manifest is missing required field: " + field);
        }
        String value = manifest.get(field).getAsString();
        if (value == null || value.isBlank()) {
            throw new GradleException("Manifest field is blank: " + field);
        }
        return value;
    }

    /**
     * Writes {@code checksums.txt} next to the archive: the archive SHA-256
     * followed by the SHA-256 of every entry.
     *
     * @param archive the assembled archive
     * @throws IOException              if reading or writing fails
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     */
    private void writeChecksums(File archive) throws IOException, NoSuchAlgorithmException {
        StringBuilder sb = new StringBuilder();
        MessageDigest archiveDigest = MessageDigest.getInstance("SHA-256");
        try (ZipFile zf = new ZipFile(archive)) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                MessageDigest entryDigest = MessageDigest.getInstance("SHA-256");
                try (InputStream is = zf.getInputStream(entry)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        entryDigest.update(buf, 0, n);
                        archiveDigest.update(buf, 0, n);
                    }
                }
                sb.append(HexFormat.of().formatHex(entryDigest.digest()))
                        .append("  ").append(entry.getName()).append('\n');
            }
        }
        File checksums = new File(archive.getParentFile(), "checksums.txt");
        Files.writeString(checksums.toPath(),
                HexFormat.of().formatHex(archiveDigest.digest()) + "  " + archive.getName() + "\n" + sb);
    }
}

package com.aprism.packaging;

import java.io.File;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin that registers the Aprism packaging tasks ({@code packageAje},
 * {@code packageAbe}, and {@code packageAep}) and the
 * {@link AprismPackagingExtension} extension.
 *
 * <p>Fat-jar assembly (shadow) is applied by the consuming project, not by
 * this plugin, so that mods that do not need shading are not forced to carry
 * the shadow plugin.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismPackagingPlugin implements Plugin<Project> {

    /** The name of the Aprism packaging extension. */
    public static final String EXTENSION_NAME = "aprismPackaging";

    @Override
    public void apply(Project project) {
        AprismPackagingExtension ext =
                project.getExtensions().create(EXTENSION_NAME, AprismPackagingExtension.class);

        project.getTasks().register("packageAje", PackageAjeTask.class, task -> {
            task.setDescription("Assembles a .aje Aprism-native mod archive.");
            task.setGroup("aprism");
            // packageAje embeds the project's main jar, so it must run after it.
            // Referenced by name so the plugin can be applied before the java
            // plugin; the dependency resolves lazily at execution time.
            task.dependsOn("jar");

            // Wire typed inputs from the extension. The build script's
            // aprismPackaging block has already executed by the time this
            // task is realized, so the extension values are final.
            task.getManifestFile().set(project.file(ext.getManifestFile()));
            if (ext.getMainJar() != null && !ext.getMainJar().isBlank()) {
                task.getMainJar().set(project.file(ext.getMainJar()));
            } else {
                task.getMainJar().fileProvider(project.provider(() -> {
                    var jarTask = project.getTasks().findByName("jar");
                    if (jarTask instanceof org.gradle.api.tasks.bundling.Jar jar) {
                        return jar.getArchiveFile().get().getAsFile();
                    }
                    return null;
                }));
            }
            File resourcesRoot = project.file(ext.getExtraResources());
            if (resourcesRoot.isDirectory()) {
                task.getResourcesRoot().set(resourcesRoot.getAbsolutePath());
                task.getResources().from(project.fileTree(resourcesRoot));
            }
            File mixinsRoot = project.file(ext.getMixinConfigs());
            if (mixinsRoot.isDirectory()) {
                task.getMixinsRoot().set(mixinsRoot.getAbsolutePath());
                task.getMixins().from(project.fileTree(mixinsRoot));
            }
            File libRoot = project.file(ext.getLibDir());
            if (libRoot.isDirectory()) {
                task.getLibRoot().set(libRoot.getAbsolutePath());
                task.getLib().from(project.fileTree(libRoot));
            }
            task.getOutputDir().set(project.file(ext.getOutputDir()));
        });

        project.getTasks().register("packageAbe", PackageAbeTask.class, task -> {
            task.setDescription("Assembles a .abe Bedrock mod archive.");
            task.setGroup("aprism");
        });
        project.getTasks().register("packageAep", PackageAepTask.class, task -> {
            task.setDescription("Assembles a .aep Aprism extension archive.");
            task.setGroup("aprism");
        });
    }
}

package com.aprism.packaging;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Gradle plugin that registers the Aprism packaging tasks ({@code packageAje},
 * {@code packageAbe}, and {@code packageAep}), the {@link AprismPackagingExtension}
 * extension, and applies the shadow plugin for fat-jar assembly.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismPackagingPlugin implements Plugin<Project> {

    /** The id of the shadow plugin applied for fat-jar assembly. */
    private static final String SHADOW_PLUGIN_ID = "com.github.johnrengelman.shadow";

    /** The name of the Aprism packaging extension. */
    public static final String EXTENSION_NAME = "aprismPackaging";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SHADOW_PLUGIN_ID);
        project.getExtensions().create(EXTENSION_NAME, AprismPackagingExtension.class);
        project.getTasks().register("packageAje", PackageAjeTask.class);
        project.getTasks().register("packageAbe", PackageAbeTask.class);
        project.getTasks().register("packageAep", PackageAepTask.class);
    }
}

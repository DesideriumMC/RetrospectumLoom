package net.fabricmc.loom;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;

/** Supported Gradle 8.14.6 entry point. */
public class RetrospectumPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (!project.getGradle().getGradleVersion().equals("8.14.6"))
            throw new GradleException("Retrospectum Loom currently requires Gradle 8.14.6; use the template wrapper.");
        project.getPluginManager().apply(LoomGradlePlugin.class);
        LegacyJavaConvention.configure(project);
        Configuration bootstrap = DevelopmentEnvironment.configure(project);
        ReleaseArtifacts.configure(project);
        DevelopmentLaunches.register(project, bootstrap);
        ModPublishing.configure(project);
    }
}

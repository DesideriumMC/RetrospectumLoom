package net.fabricmc.loom;

import java.io.File;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

/** Keeps compiler, API and game-runtime versions separate. */
final class LegacyJavaConvention {
    static void configure(Project project) {
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        extension.defaultRunToolchainVersion = JavaVersion.VERSION_1_7;
        extension.defaultRunToolchainVendor = "ANY";
        project.getExtensions().getByType(JavaPluginExtension.class).getToolchain()
            .getLanguageVersion().set(JavaLanguageVersion.of(8));
        Provider<File> runtimeApi = project.provider(() -> Java7Support.runtimeJar(project));
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().setBootstrapClasspath(project.files(runtimeApi));
            task.setSourceCompatibility("1.6");
            task.setTargetCompatibility("1.6");
        });
    }
}

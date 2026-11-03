package net.fabricmc.loom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

/** Java 7 API boundary, independent of the JDK running Gradle or javac. */
final class Java7Support {
    static File runtimeJar(Project project) {
        Object configured = project.findProperty("java7Home");
        File directory;
        if (configured != null && !configured.toString().trim().isEmpty()) {
            directory = project.getRootProject().file(configured.toString());
        } else if (project.getRootProject().file(".jdks/7").isDirectory()) {
            directory = project.getRootProject().file(".jdks/7");
        } else {
            project.getPluginManager().apply("java");
            org.gradle.jvm.toolchain.JavaToolchainService toolchains = project.getExtensions()
                .getByType(org.gradle.jvm.toolchain.JavaToolchainService.class);
            try {
                directory = toolchains.launcherFor(spec -> spec.getLanguageVersion()
                    .set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(7)))
                    .get().getMetadata().getInstallationPath().getAsFile();
            } catch (GradleException e) {
                throw new GradleException("Java 7 JDK not found. Run setup.ps1 or setup.sh, or set java7Home.", e);
            }
        }
        File release = new File(directory, "release");
        File runtime = new File(directory, "jre/lib/rt.jar");
        if (!release.isFile() || !runtime.isFile())
            throw new GradleException("java7Home must contain release and jre/lib/rt.jar: " + directory);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(release.toPath())) {
            properties.load(input);
        } catch (IOException e) {
            throw new GradleException("Cannot read Java 7 JDK metadata: " + release, e);
        }
        if (!properties.getProperty("JAVA_VERSION", "").replace("\"", "").startsWith("1.7."))
            throw new GradleException("java7Home must point to Java 7, found " + properties.getProperty("JAVA_VERSION"));
        return runtime;
    }
}

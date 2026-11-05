package net.fabricmc.loom;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyJavaConventionTest {
    @Test public void java7ApiIsResolvedAfterConventionConfiguration() throws Exception {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        LoomGradleExtension extension = project.getExtensions().create("retrospectum", LoomGradleExtension.class, project);
        // Invalid at configuration time, replaced before the compile inputs are queried.
        project.getExtensions().getExtraProperties().set("java7Home", "does-not-exist");
        LegacyJavaConvention.configure(project);
        JavaCompile compile = (JavaCompile) project.getTasks().getByName("compileJava");
        assertEquals("1.6", compile.getSourceCompatibility());
        assertEquals("1.6", compile.getTargetCompatibility());
        assertEquals(8, project.getExtensions().getByType(JavaPluginExtension.class)
            .getToolchain().getLanguageVersion().get().asInt());
        assertEquals(org.gradle.api.JavaVersion.VERSION_1_7, extension.defaultRunToolchainVersion);
        Path jdk = project.getProjectDir().toPath().resolve("selected java7");
        Files.createDirectories(jdk.resolve("jre/lib"));
        Files.write(jdk.resolve("release"), "JAVA_VERSION=\"1.7.0_352\"\n".getBytes(StandardCharsets.UTF_8));
        Path runtime = Files.createFile(jdk.resolve("jre/lib/rt.jar"));
        project.getExtensions().getExtraProperties().set("java7Home", jdk.toString());
        assertEquals(runtime.toFile(), compile.getOptions().getBootstrapClasspath().getSingleFile());
    }
}

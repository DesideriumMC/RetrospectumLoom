package net.fabricmc.loom;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class Java7SupportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Project project() throws Exception {
        return ProjectBuilder.builder().withProjectDir(temporary.newFolder()).build();
    }

    private File fixture(Project project, String version, boolean withRuntime) throws Exception {
        File directory = new File(project.getProjectDir(), "jdk with spaces");
        Files.createDirectories(new File(directory, "jre/lib").toPath());
        Files.write(new File(directory, "release").toPath(), ("JAVA_VERSION=\"" + version + "\"\n").getBytes(StandardCharsets.UTF_8));
        if (withRuntime) Files.write(new File(directory, "jre/lib/rt.jar").toPath(), new byte[0]);
        project.getExtensions().getExtraProperties().set("java7Home", "jdk with spaces");
        return directory;
    }

    @Test public void acceptsRelativeJava7HomeWithSpaces() throws Exception {
        Project project = project();
        File home = fixture(project, "1.7.0_352", true);
        assertEquals(new File(home, "jre/lib/rt.jar"), Java7Support.runtimeJar(project));
    }

    @Test public void rejectsJava8ApiArchive() throws Exception {
        Project project = project();
        fixture(project, "1.8.0_492", true);
        try { Java7Support.runtimeJar(project); fail("Java 8 API boundary accepted"); }
        catch (GradleException error) { assertTrue(error.getMessage().contains("must point to Java 7")); }
    }

    @Test public void rejectsMissingApiArchive() throws Exception {
        Project project = project();
        fixture(project, "1.7.0_352", false);
        try { Java7Support.runtimeJar(project); fail("Missing runtime API accepted"); }
        catch (GradleException error) { assertTrue(error.getMessage().contains("jre/lib/rt.jar")); }
    }

    @Test public void usesProjectLocalJdkWithoutManualConfiguration() throws Exception {
        Project project = project();
        File home = project.file(".jdks/7");
        Files.createDirectories(new File(home, "jre/lib").toPath());
        Files.write(new File(home, "release").toPath(), "JAVA_VERSION=\"1.7.0_352\"\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(home, "jre/lib/rt.jar").toPath(), new byte[0]);
        assertEquals(new File(home, "jre/lib/rt.jar"), Java7Support.runtimeJar(project));
    }
}

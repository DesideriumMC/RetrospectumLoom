package net.fabricmc.loom.task;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReobfJarTaskTest {
    @Test public void failedRemappingKeepsThePreviousOutputAndRemovesStaging() throws Exception {
        Project project = ProjectBuilder.builder().build();
        ReobfJarTask task = project.getTasks().create("remapProbe", ReobfJarTask.class);
        Path directory = project.getProjectDir().toPath();
        Path input = directory.resolve("invalid.jar");
        Path output = directory.resolve("previous.jar");
        Path mappings = directory.resolve("mappings.srg");
        Files.write(input, new byte[]{1, 2, 3});
        byte[] previous = new byte[]{4, 5, 6};
        Files.write(output, previous);
        Files.write(mappings, "CL: before after\n".getBytes(StandardCharsets.UTF_8));
        task.getInput().set(input.toFile());
        task.getMappingsFile().set(mappings.toFile());
        task.getAsm4Compatibility().set(false);
        task.getDestinationDirectory().set(directory.toFile());
        task.getArchiveFileName().set(output.getFileName().toString());
        try { task.copy(); fail("Accepted an invalid input archive"); }
        catch (GradleException expected) { assertNotNull(expected.getCause()); }
        assertArrayEquals(previous, Files.readAllBytes(output));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".artifact-")));
        }
    }
}

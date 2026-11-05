package net.fabricmc.loom.newprovider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import net.fabricmc.loom.LoomGradleExtension;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProviderRefreshTest {
    private static class Probe extends NewProvider<Probe> {
        Probe(Project project, LoomGradleExtension extension) { super(project, extension); }
        Path write(Path target, ThrowyConsumer<Path> writer) throws Exception { return getOrCreate(target, writer); }
    }

    @Test public void failedFileAndDirectoryRefreshKeepPreviousArtifacts() throws Exception {
        Project project = ProjectBuilder.builder().build();
        LoomGradleExtension extension = project.getExtensions().create("retrospectum", LoomGradleExtension.class, project);
        Probe probe = new Probe(project, extension);
        Path file = project.getProjectDir().toPath().resolve("artifact.jar");
        Path directory = project.getProjectDir().toPath().resolve("natives");
        probe.write(file, output -> Files.write(output, new byte[]{1, 2, 3}));
        probe.write(directory, output -> { Files.createDirectories(output); Files.write(output.resolve("old"), new byte[]{4}); });
        extension.refreshDependencies = true;
        for (Path target : new Path[]{file, directory}) {
            try {
                probe.write(target, output -> {
                    if (target.equals(directory)) { Files.createDirectories(output); Files.write(output.resolve("partial"), new byte[]{9}); }
                    else Files.write(output, new byte[]{9});
                    throw new IOException("failed refresh");
                });
                fail("Accepted failed refresh");
            } catch (IOException expected) { assertEquals("failed refresh", expected.getMessage()); }
        }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
        assertTrue(Files.exists(directory.resolve("old")));
        assertFalse(Files.exists(directory.resolve("partial")));
        probe.write(directory, output -> { Files.createDirectories(output); Files.write(output.resolve("new"), new byte[]{5}); });
        assertFalse(Files.exists(directory.resolve("old")));
        assertTrue(Files.exists(directory.resolve("new")));
        try (java.util.stream.Stream<Path> files = Files.list(project.getProjectDir().toPath())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".artifact-")));
        }
    }
}

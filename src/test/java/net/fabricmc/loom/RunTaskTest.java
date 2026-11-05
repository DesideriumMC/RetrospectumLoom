package net.fabricmc.loom;

import net.fabricmc.loom.task.RunTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class RunTaskTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void runTasksKeepRuntimeClasspathLazyAndRetainProducers() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build();
        project.getPluginManager().apply("java");
        LoomGradleExtension extension = project.getExtensions().create("loom", LoomGradleExtension.class, project);
        extension.forgeCapabilities.minecraftRealPath(path -> path);
        Configuration runtime = project.getConfigurations().getByName("runtimeClasspath");
        AtomicBoolean resolved = new AtomicBoolean();
        runtime.getIncoming().beforeResolve(ignored -> resolved.set(true));
        String[] producers = {"remapModImplementationDependencies", "remapModLocalRuntimeDependencies", "remapModRuntimeOnlyDependencies"};
        for (String name : producers) {
            TaskProvider<Jar> producer = project.getTasks().register(name, Jar.class, jar -> jar.getArchiveFileName().set(name + ".jar"));
            project.getDependencies().add("runtimeOnly", project.files(producer.flatMap(Jar::getArchiveFile)));
        }

        for (String side : new String[]{"client", "server"}) {
            // Isolate classpath wiring from Minecraft downloads and Forge detection.
            RunConfig config = new RunConfig(project, side) {
                @Override public RunConfig cook(LoomGradleExtension ignored) { return this; }
            };
            config.setAutoConfigureToolchains(false);
            config.setMainClass("example.Main");
            RunTask run = project.getTasks().create("run" + side, RunTask.class, config);
            assertFalse("Creating a run task must not resolve runtimeClasspath", resolved.get());
            java.util.Set<? extends org.gradle.api.Task> dependencies = run.getTaskDependencies().getDependencies(run);
            for (String producer : producers) {
                assertTrue("Missing producer " + producer, dependencies.contains(project.getTasks().getByName(producer)));
            }
            assertTrue("The dev JAR must also be built", dependencies.contains(project.getTasks().getByName("jar")));
        }
    }
}

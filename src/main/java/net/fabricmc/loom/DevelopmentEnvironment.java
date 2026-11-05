package net.fabricmc.loom;

import net.fabricmc.loom.task.GenSourcesTask;
import net.fabricmc.loom.task.RunTask;
import net.fabricmc.loom.task.runs.GenIdeaRunConfigsTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

/** Development classpaths, launch checks and IDE defaults. */
final class DevelopmentEnvironment {
    static Configuration configure(Project project) {
        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        extension.useIdeMinecraftModule = true;
        extension.useMixinBootstrap = true;
        LocalMinecraftModule.registerRepository(project);
        project.getTasks().named("genIdeaRuns", GenIdeaRunConfigsTask.class)
            .configure(task -> task.setGradleLaunchers(true));
        Configuration bootstrap = project.getConfigurations().create("mixinBootstrap");
        bootstrap.setCanBeConsumed(false);
        project.getConfigurations().getByName("compileOnly").extendsFrom(bootstrap);
        project.getConfigurations().getByName("runtimeClasspath").extendsFrom(bootstrap);
        TaskProvider<Jar> developmentJar = project.getTasks().named("jar", Jar.class);
        project.getTasks().withType(RunTask.class).configureEach(run -> {
            run.classpath(bootstrap);
            run.dependsOn(bootstrap);
            run.doFirst(task -> {
                ModValidation.archive(developmentJar.get().getArchiveFile().get().getAsFile());
                if (!extension.mixinRefmaps.isEmpty()) ModValidation.runtime(run.getClasspath());
            });
        });
        project.getTasks().named("genSources", GenSourcesTask.class).configure(task -> {
            task.setNumThreads(1);
            task.setSaferBytecodeProvider(true);
        });
        return bootstrap;
    }
}

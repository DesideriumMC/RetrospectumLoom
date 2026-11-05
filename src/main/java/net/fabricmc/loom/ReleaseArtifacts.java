package net.fabricmc.loom;

import net.fabricmc.loom.task.ReobfJarTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

/** Release variant selection and final archive checks, wired without afterEvaluate. */
final class ReleaseArtifacts {
    static void configure(Project project) {
        project.getTasks().withType(Jar.class).configureEach(task -> {
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
        });
        project.getTasks().named("jar", Jar.class).configure(task -> task.getArchiveClassifier().convention("dev"));
        TaskProvider<ReobfJarTask> release = project.getTasks().named("remapJarForRelease", ReobfJarTask.class);
        for (String name : new String[]{"apiElements", "runtimeElements"}) {
            project.getConfigurations().getByName(name).getOutgoing().getArtifacts().clear();
            project.getArtifacts().add(name, release);
        }
        project.getTasks().named("assemble").configure(task -> task.dependsOn(release));
        release.configure(task -> {
            task.getNormalizeArchive().set(true);
            task.getValidateModArchive().set(true);
        });
        for (String name : new String[]{"apiElements", "runtimeElements"})
            project.getConfigurations().getByName(name).getAttributes().attribute(
                org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 7);
    }
}

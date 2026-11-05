package net.fabricmc.loom;

import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.jvm.tasks.Jar;

/** Publication guards; publishing itself is enabled by the consuming project. */
final class ModPublishing {
    static void configure(Project project) {
        // Publish original distribution coordinates, never the remapped local files.
        for (String name : new String[]{"modImplementation", "modRuntimeOnly", "coremodImplementation", "coremodRuntimeOnly"}) {
            project.getConfigurations().getByName("runtimeElements")
                .extendsFrom(project.getConfigurations().getByName(name));
        }
        project.getPluginManager().withPlugin("maven-publish", applied -> {
            project.getTasks().withType(PublishToMavenRepository.class)
                .configureEach(t -> t.doFirst(x -> validatePublication(project, t.getPublication())));
            project.getTasks().withType(PublishToMavenLocal.class)
                .configureEach(t -> t.doFirst(x -> validatePublication(project, t.getPublication())));
        });
    }

    private static void validatePublication(Project project, MavenPublication pub) {
        java.io.File dev = ((Jar) project.getTasks().getByName("jar")).getArchiveFile().get().getAsFile();
        pub.getArtifacts().forEach(a -> {
            if (a.getFile().equals(dev) || "dev".equals(a.getClassifier()))
                throw new GradleException("Refusing to publish dev JAR in publication '" + pub.getName() + "'. Publish remapJarForRelease instead.");
        });
    }
}

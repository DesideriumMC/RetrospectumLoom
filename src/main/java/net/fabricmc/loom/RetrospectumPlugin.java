package net.fabricmc.loom;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.jvm.tasks.Jar;
import net.fabricmc.loom.task.ReobfJarTask;

/** Supported Gradle 8.14.6 entry point. Legacy volde builds retain their existing behavior. */
public class RetrospectumPlugin implements Plugin<Project> {
    public void apply(Project project) {
        if (!project.getGradle().getGradleVersion().equals("8.14.6"))
            throw new GradleException("Retrospectum Loom currently requires Gradle 8.14.6; use the template wrapper.");
        java.io.File runtimeApi = Java7Support.runtimeJar(project);
        project.getPluginManager().apply(LoomGradlePlugin.class);
        LoomGradleExtension defaults = project.getExtensions().getByType(LoomGradleExtension.class);
        defaults.defaultRunToolchainVersion = org.gradle.api.JavaVersion.VERSION_1_7;
        defaults.defaultRunToolchainVendor = "ANY";
        project.getExtensions().getByType(org.gradle.api.plugins.JavaPluginExtension.class)
            .getToolchain().getLanguageVersion().set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(8));
        project.getTasks().withType(org.gradle.api.tasks.compile.JavaCompile.class).configureEach(task -> {
            task.getOptions().setBootstrapClasspath(project.files(runtimeApi));
            task.setSourceCompatibility("1.6");
            task.setTargetCompatibility("1.6");
        });
        project.getTasks().withType(Jar.class).configureEach(t -> {
            t.setPreserveFileTimestamps(false);
            t.setReproducibleFileOrder(true);
        });
        project.getExtensions().getByType(LoomGradleExtension.class).useIdeMinecraftModule = true;
        LocalMinecraftModule.registerRepository(project);
        project.getExtensions().add("retrospectum", project.getExtensions().getByType(LoomGradleExtension.class));
        project.getPluginManager().apply("maven-publish");
        org.gradle.api.artifacts.Configuration bootstrap = project.getConfigurations().create("mixinBootstrap");
        bootstrap.setCanBeConsumed(false);
        project.getConfigurations().getByName("compileOnly").extendsFrom(bootstrap);
        project.getTasks().withType(net.fabricmc.loom.task.RunTask.class).configureEach(run -> {
            run.classpath(bootstrap);
            run.dependsOn(bootstrap);
            run.args("--tweakClass=dev.desiderium.retro.mixin.bootstrap.RetroMixinTweaker");
        });

        project.getTasks().named("genSources", net.fabricmc.loom.task.GenSourcesTask.class).configure(t -> {
            t.setNumThreads(1);
            t.setSaferBytecodeProvider(true);
        });
        project.getTasks().named("genIdeaRuns").configure(task -> task.doLast(t -> {
            // Gradle launchers preserve task dependencies, validation and the selected Java 7 toolchain.
            java.nio.file.Path directory = project.file(".idea/runConfigurations").toPath();
            try {
                java.nio.file.Files.createDirectories(directory);
                for (String side : new String[]{"Client", "Server"}) {
                    String xml = "<component name=\"ProjectRunConfigurationManager\"><configuration default=\"false\" name=\"RetroMixin " + side + "\" type=\"GradleRunConfiguration\" factoryName=\"Gradle\"><ExternalSystemSettings><option name=\"externalProjectPath\" value=\"$PROJECT_DIR$\"/><option name=\"externalSystemIdString\" value=\"GRADLE\"/><option name=\"taskNames\"><list><option value=\"run" + side + "\"/></list></option></ExternalSystemSettings><method v=\"2\"/></configuration></component>";
                    java.nio.file.Files.write(directory.resolve(side.toLowerCase(java.util.Locale.ROOT) + ".xml"), xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                String remote = "<component name=\"ProjectRunConfigurationManager\"><configuration default=\"false\" name=\"RetroMixin Remote 5005\" type=\"Remote\" factoryName=\"Remote JVM Debug\"><option name=\"USE_SOCKET_TRANSPORT\" value=\"true\"/><option name=\"SERVER_MODE\" value=\"false\"/><option name=\"HOST\" value=\"localhost\"/><option name=\"PORT\" value=\"5005\"/><method v=\"2\"/></configuration></component>";
                java.nio.file.Files.write(directory.resolve("retromixin-remote.xml"), remote.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) { throw new GradleException("Cannot generate IntelliJ configurations", e); }
        }));
        // Update outgoing Java variants as well as Maven publications: consumers must get SRG.
        project.afterEvaluate(p -> {
            LoomGradleExtension extension = p.getExtensions().getByType(LoomGradleExtension.class);
            if (!extension.mixinConfigs.isEmpty()) extension.runConfigs.all(run -> run.property("retromixin.mixin.configs", String.join(",", extension.mixinConfigs)));
            ReobfJarTask release = (ReobfJarTask) p.getTasks().getByName("remapJarForRelease");
            for (String name : new String[]{"apiElements", "runtimeElements"}) {
                p.getConfigurations().getByName(name).getOutgoing().getArtifacts().clear();
                p.getArtifacts().add(name, release);
            }
            p.getTasks().named("assemble").configure(t -> t.dependsOn(release));
            release.doFirst(t -> ModValidation.archive(((Jar) p.getTasks().getByName("jar")).getArchiveFile().get().getAsFile()));
            release.doLast(t -> {
                java.io.File archive = release.getArchiveFile().get().getAsFile();
                normalizeArchive(archive.toPath());
                ModValidation.archive(archive);
            });
            p.getTasks().withType(net.fabricmc.loom.task.RunTask.class).configureEach(t -> t.doFirst(x -> {
                ModValidation.archive(((Jar) p.getTasks().getByName("jar")).getArchiveFile().get().getAsFile());
                if (!p.getExtensions().getByType(LoomGradleExtension.class).mixinRefmaps.isEmpty()) ModValidation.runtime(t.getClasspath());
            }));
        });
        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        project.getTasks().register("exportDevelopmentLaunches", task -> {
            task.dependsOn("jar", "shimForgeLibraries", "remappedConfigEntryFolderCopy", bootstrap);
            task.doLast(t -> {
                java.util.Map<String, Object> launches = new java.util.LinkedHashMap<>();
                for (String side : new String[]{"Client", "Server"}) {
                    net.fabricmc.loom.task.RunTask run = (net.fabricmc.loom.task.RunTask) project.getTasks().getByName("run" + side);
                    java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
                    data.put("executable", run.getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
                    data.put("directory", run.getWorkingDir().getAbsolutePath());
                    data.put("classpath", run.getClasspath().getAsPath());
                    data.put("mainClass", run.getMainClass().get());
                    data.put("jvmArgs", run.getJvmArgs());
                    data.put("args", run.getArgs());
                    launches.put(side.toLowerCase(java.util.Locale.ROOT), data);
                }
                java.io.File output = new java.io.File(project.getBuildDir(), "retromixin/launches.json");
                output.getParentFile().mkdirs();
                try { java.nio.file.Files.write(output.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(launches).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                catch (java.io.IOException e) { throw new GradleException("Cannot export development launches", e); }
            });
        });
        publishing.getPublications().create("mod", MavenPublication.class, pub -> {
            pub.setArtifacts(java.util.Collections.singletonList(project.getTasks().named("remapJarForRelease")));
            pub.pom(pom -> pom.withXml(xml -> {
                groovy.util.Node deps = net.fabricmc.loom.util.GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
                for (String configuration : new String[]{"api", "implementation", "runtimeOnly"}) {
                    org.gradle.api.artifacts.Configuration cfg = project.getConfigurations().findByName(configuration);
                    if (cfg == null) continue;
                    for (org.gradle.api.artifacts.Dependency dep : cfg.getDependencies()) {
                        if (dep.getGroup() == null || dep.getVersion() == null) continue;
                        groovy.util.Node node = deps.appendNode("dependency");
                        node.appendNode("groupId", dep.getGroup()); node.appendNode("artifactId", dep.getName());
                        node.appendNode("version", dep.getVersion());
                        node.appendNode("scope", configuration.equals("runtimeOnly") ? "runtime" : "compile");
                    }
                }
            }));
        });
        project.getTasks().withType(PublishToMavenRepository.class).configureEach(t -> t.doFirst(x -> validatePublication(project, t.getPublication())));
        project.getTasks().withType(PublishToMavenLocal.class).configureEach(t -> t.doFirst(x -> validatePublication(project, t.getPublication())));
    }

    private static void validatePublication(Project project, MavenPublication pub) {
        java.io.File dev = ((Jar) project.getTasks().getByName("jar")).getArchiveFile().get().getAsFile();
        pub.getArtifacts().forEach(a -> {
            if (a.getFile().equals(dev) || "dev".equals(a.getClassifier()))
                throw new GradleException("Refusing to publish dev JAR in publication '" + pub.getName() + "'. Publish remapJarForRelease instead.");
        });
    }

    private static void normalizeArchive(java.nio.file.Path path) {
        java.nio.file.Path temp = null;
        try {
            temp = java.nio.file.Files.createTempFile(path.getParent(), "release-", ".tmp");
            try (java.util.zip.ZipFile input = new java.util.zip.ZipFile(path.toFile());
                 java.util.zip.ZipOutputStream output = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(temp))) {
                java.util.List<String> names = new java.util.ArrayList<>();
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = input.entries();
                while (entries.hasMoreElements()) names.add(entries.nextElement().getName());
                java.util.Collections.sort(names);
                java.util.Calendar timestamp = java.util.Calendar.getInstance();
                timestamp.clear(); timestamp.set(2000, 0, 1, 0, 0, 0);
                for (String name : names) {
                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(name);
                    entry.setTime(timestamp.getTimeInMillis());
                    output.putNextEntry(entry);
                    try (java.io.InputStream in = input.getInputStream(input.getEntry(name))) {
                        byte[] bytes = new byte[65536]; int count;
                        while ((count = in.read(bytes)) != -1) output.write(bytes, 0, count);
                    }
                    output.closeEntry();
                }
            }
            java.nio.file.Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new GradleException("Cannot normalize release archive " + path, e);
        } finally {
            if (temp != null) try { java.nio.file.Files.deleteIfExists(temp); } catch (java.io.IOException ignored) {}
        }
    }
}

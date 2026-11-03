package net.fabricmc.loom;

import java.io.File;
import java.nio.file.Files;
import net.fabricmc.loom.mcp.MixinMappings;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.task.ReobfJarTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;

/** Experimental P2 wiring. Dependencies remain explicit in the consuming build. */
final class MixinIntegration {
    static void configure(Project project, LoomGradleExtension extension, String sourceSetName, String refmapName) {
        if (!refmapName.matches("[A-Za-z0-9_.-]+\\.json")) throw new GradleException("Mixin refmap must be a plain JSON filename");
        if (extension.mixinRefmaps.containsKey(sourceSetName)) throw new GradleException("Mixin SourceSet already registered: " + sourceSetName);
        if (extension.mixinRefmaps.containsValue(refmapName)) throw new GradleException("Mixin refmap filename already registered: " + refmapName);
        SourceSet source = project.getExtensions().getByType(SourceSetContainer.class).getByName(sourceSetName);
        extension.mixinRefmaps.put(sourceSetName, refmapName);
        String suffix = sourceSetName.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + sourceSetName.substring(1);
        File directory = new File(project.getBuildDir(), "retromixin/mixin/" + sourceSetName);
        File forward = new File(directory, "mcp-srg.srg");
        File reverse = new File(directory, "srg-mcp.srg");
        File extra = new File(directory, "extra.srg");
        File refmap = new File(directory, refmapName);
        TaskProvider<org.gradle.api.Task> export = project.getTasks().register("export" + suffix + "MixinMappings", task -> {
            task.getOutputs().files(forward, reverse);
            // The provider graph still runs during configuration; P4 will model its inputs.
            task.getOutputs().upToDateWhen(t -> false);
            task.doLast(t -> {
                Srg mappings = extension.getProviderGraph().reobfSrg;
                if (mappings == null || !extension.forgeCapabilities.distributionNamingScheme.get().equals(Constants.INTERMEDIATE_NAMING_SCHEME)) {
                    throw new GradleException("Mixin integration requires joined MCP -> SRG release mappings");
                }
                try {
                    Files.createDirectories(directory.toPath());
                    mappings.writeTo(forward.toPath());
                    MixinMappings.inverse(mappings).writeTo(reverse.toPath());
                } catch (Exception e) { throw new GradleException("Cannot export Mixin mappings", e); }
            });
        });
        TaskProvider<JavaCompile> compile = project.getTasks().named(source.getCompileJavaTaskName(), JavaCompile.class);
        compile.configure(task -> {
            // AP outputs are cleared before compilation. A classpath-only change may
            // otherwise let incremental javac skip every source and never run the AP.
            task.getOptions().setIncremental(false);
            task.dependsOn(export);
            task.getInputs().file(forward);
            task.getOutputs().files(refmap, extra);
            task.getOptions().getCompilerArgs().addAll(java.util.Arrays.asList(
                "-AreobfSrgFile=" + forward, "-AoutSrgFile=" + extra,
                "-AoutRefMapFile=" + refmap, "-AdefaultObfuscationEnv=searge"));
            task.doFirst(t -> {
                if (task.getOptions().getCompilerArgs().contains("-proc:none")) throw new GradleException("Mixin AP is disabled by -proc:none");
                try { Files.deleteIfExists(extra.toPath()); Files.deleteIfExists(refmap.toPath()); }
                catch (Exception e) { throw new GradleException("Cannot clear stale Mixin AP outputs", e); }
            });
            task.doLast(t -> {
                if (!refmap.isFile() || !extra.isFile()) throw new GradleException("Mixin AP did not generate refmap/mappings for " + sourceSetName);
            });
        });
        project.getTasks().named("jar", Jar.class).configure(task -> {
            task.dependsOn(compile);
            task.from(refmap);
        });
        project.getTasks().named("remapJarForRelease", ReobfJarTask.class).configure(task -> {
            task.dependsOn(compile);
            task.getMixinMappings().from(extra);
        });
        extension.runConfigs.all(run -> {
            run.property("mixin.env.remapRefMap", "true");
            run.property("mixin.env.refMapRemappingFile", reverse.getAbsolutePath());
            run.property("mixin.env.refMapRemappingEnv", "searge");
        });
        project.getTasks().matching(t -> t.getName().equals("runClient") || t.getName().equals("runServer"))
            .configureEach(t -> t.dependsOn(export));
    }
}

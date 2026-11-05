package net.fabricmc.loom;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.task.RunTask;

import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;

/** Exports fully configured Gradle launches for isolated runtime verification. */
final class DevelopmentLaunches {
    static void register(Project project, Configuration bootstrap) {
        project.getTasks().register("exportDevelopmentLaunches", task -> {
            task.dependsOn("jar", "shimForgeLibraries", "remappedConfigEntryFolderCopy", bootstrap);
            task.doLast(t -> {
                Map<String, Object> launches = new LinkedHashMap<>();
                for (String side : new String[]{"Client", "Server"}) {
                    RunTask run = (RunTask) project.getTasks().getByName("run" + side);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("executable", run.getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
                    data.put("directory", run.getWorkingDir().getAbsolutePath());
                    data.put("classpath", run.getClasspath().getAsPath());
                    data.put("mainClass", run.getMainClass().get());
                    data.put("jvmArgs", run.getJvmArgs());
                    data.put("args", run.getArgs());
                    launches.put(side.toLowerCase(Locale.ROOT), data);
                }
                File output = new File(project.getBuildDir(), "retromixin/launches.json");
                output.getParentFile().mkdirs();
                try { Files.write(output.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(launches).getBytes(StandardCharsets.UTF_8)); }
                catch (IOException e) { throw new GradleException("Cannot export development launches", e); }
            });
        });
    }
}

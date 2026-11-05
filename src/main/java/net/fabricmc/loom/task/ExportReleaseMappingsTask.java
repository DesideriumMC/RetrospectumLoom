package net.fabricmc.loom.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Bridges the eager provider graph to an explicit, incremental file dependency. */
@DisableCachingByDefault(because = "Small local mapping export; remote build caching is not yet verified")
public abstract class ExportReleaseMappingsTask extends DefaultTask {
    @Input
    public abstract ListProperty<String> getMappingLines();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void export() throws IOException {
        Path output = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), "mappings-", ".tmp");
        try {
            // Write LF explicitly; Files.write(Iterable) uses platform line separators.
            Files.write(temporary, (String.join("\n", getMappingLines().get()) + "\n").getBytes(StandardCharsets.UTF_8));
            net.fabricmc.loom.util.AtomicFiles.replace(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

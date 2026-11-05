package net.fabricmc.loom.task;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.loom.mcp.MixinMappings;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.mcp.SrgSnapshot;
import net.fabricmc.loom.util.AtomicFiles;
import net.fabricmc.loom.util.StringInterner;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** One shared reverse mapping for all Mixin source sets. */
@DisableCachingByDefault(because = "Small local mapping export")
public abstract class ExportMixinMappingsTask extends DefaultTask {
    @InputFile @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getMappingsFile();
    @OutputFile
    public abstract RegularFileProperty getReverseFile();

    @TaskAction
    public void export() throws Exception {
        Srg mappings;
        try (StringInterner strings = new StringInterner()) {
            mappings = new Srg().read(getMappingsFile().get().getAsFile().toPath(), strings);
        }
        AtomicFiles.write(getReverseFile().get().getAsFile().toPath(),
            (String.join("\n", SrgSnapshot.lines(MixinMappings.inverse(mappings))) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}

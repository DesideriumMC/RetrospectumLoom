package net.fabricmc.loom.task;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.mcp.Srg;
import net.fabricmc.loom.newprovider.NaiveRenamer;
import net.fabricmc.loom.newprovider.RemapperMcp;
import net.fabricmc.loom.util.ArtifactTransaction;
import net.fabricmc.loom.util.StringInterner;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;

/** Resolve producer artifacts through Gradle task dependencies before remapping them. */
@org.gradle.work.DisableCachingByDefault(because = "Dependency remapping build cache portability is not verified")
public abstract class RemapModDependenciesTask extends DefaultTask {
    @InputFiles @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getArchives();
    @Classpath public abstract ConfigurableFileCollection getRemapClasspath();
    @Input public abstract ListProperty<String> getSrgLines();
    @Input public abstract MapProperty<String, String> getFieldNames();
    @Input public abstract MapProperty<String, String> getMethodNames();
    @Input public abstract Property<String> getDistributionNamespace();
    @Input public abstract Property<Boolean> getAsm4Compatibility();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

    public static String outputName(File input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                input.toPath().toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) key.append(String.format("%02x", value & 255));
            return key + "-" + input.getName();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    @TaskAction public void remap() throws Exception {
        Members fields = members(getFieldNames().get()), methods = members(getMethodNames().get());
        Set<Path> classpath = getRemapClasspath().getFiles().stream().map(File::toPath)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        ArtifactTransaction.write(getOutputDirectory().get().getAsFile().toPath(), output -> {
            Files.createDirectories(output);
            Path mappings = output.resolve("input.srg");
            Files.write(mappings, getSrgLines().get(), StandardCharsets.UTF_8);
            Srg srg;
            try (StringInterner strings = new StringInterner()) { srg = new Srg().read(mappings, strings); }
            Files.delete(mappings);
            for (File archive : getArchives().getFiles()) {
                Path input = archive.toPath();
                Path intermediate = null;
                if (Constants.PROGUARDED_NAMING_SCHEME.equals(getDistributionNamespace().get())) {
                    intermediate = output.resolve("intermediate.jar");
                    Set<Path> references = new LinkedHashSet<>(classpath);
                    references.remove(input);
                    RemapperMcp.doIt(input, intermediate, srg, getLogger(), null, references, getAsm4Compatibility().get());
                    input = intermediate;
                } else if (!Constants.INTERMEDIATE_NAMING_SCHEME.equals(getDistributionNamespace().get())) {
                    throw new IllegalArgumentException("Unknown distribution namespace: " + getDistributionNamespace().get());
                }
                Path mapped = output.resolve(outputName(archive));
                NaiveRenamer.doIt(input, mapped, getLogger(), fields, methods);
                DependencyRefmaps.remap(mapped, fields, methods);
                if (intermediate != null) Files.delete(intermediate);
            }
        });
    }

    private static Members members(Map<String, String> names) {
        Members result = new Members();
        names.forEach((from, to) -> result.members.put(from, new Members.Entry(to, 2, null)));
        return result;
    }
}

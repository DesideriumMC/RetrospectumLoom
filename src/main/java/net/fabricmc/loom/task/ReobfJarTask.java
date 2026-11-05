/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.newprovider.RemapperMcp;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Remaps the development archive using explicitly declared mappings and classpath. */
@org.gradle.work.DisableCachingByDefault(because = "Remote build caching is not verified for the remapper output")
public class ReobfJarTask extends Jar {
    public ReobfJarTask() {
        setGroup(Constants.TASK_GROUP_PLUMBING);
        setDescription("Remaps the mod into SRG distribution names, ready for publishing.");
    }

    private final org.gradle.api.provider.Property<Boolean> validateModArchive = getProject().getObjects().property(Boolean.class).convention(false);
    private final org.gradle.api.provider.Property<Boolean> normalizeArchive = getProject().getObjects().property(Boolean.class).convention(false);

    @org.gradle.api.tasks.Input
    public org.gradle.api.provider.Property<Boolean> getValidateModArchive() { return validateModArchive; }

    @org.gradle.api.tasks.Input
    public org.gradle.api.provider.Property<Boolean> getNormalizeArchive() { return normalizeArchive; }

    private final RegularFileProperty input = getProject().getObjects().fileProperty();
    private final RegularFileProperty mappingsFile = getProject().getObjects().fileProperty();
    private final org.gradle.api.provider.Property<Boolean> asm4Compatibility = getProject().getObjects().property(Boolean.class);
    private final org.gradle.api.file.ConfigurableFileCollection mixinMappings = getProject().files();
    private final org.gradle.api.file.ConfigurableFileCollection remapClasspath = getProject().files();

    @org.gradle.api.tasks.InputFiles
    @org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public org.gradle.api.file.ConfigurableFileCollection getMixinMappings() { return mixinMappings; }

    @org.gradle.api.tasks.Classpath
    public org.gradle.api.file.ConfigurableFileCollection getRemapClasspath() { return remapClasspath; }

    @InputFile
    @org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public RegularFileProperty getMappingsFile() { return mappingsFile; }

    @org.gradle.api.tasks.Input
    public org.gradle.api.provider.Property<Boolean> getAsm4Compatibility() { return asm4Compatibility; }

    // Preserve the public Jar task/archive properties while replacing its writer.
    @Override
    @TaskAction
    protected void copy() {
        try {
            Path source = input.get().getAsFile().toPath();
            Path output = getArchiveFile().get().getAsFile().toPath();
            if (source.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
                throw new org.gradle.api.GradleException("Release remapping requires different input and output archives");
            if (validateModArchive.get()) net.fabricmc.loom.ModValidation.archive(source.toFile());
            Set<Path> classpath = remapClasspath.getFiles().stream().map(File::toPath)
                .filter(path -> !path.equals(source) && Files.exists(path))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            net.fabricmc.loom.mcp.Srg mappings;
            try (net.fabricmc.loom.util.StringInterner strings = new net.fabricmc.loom.util.StringInterner()) {
                mappings = new net.fabricmc.loom.mcp.Srg().read(mappingsFile.get().getAsFile().toPath(), strings);
            }
            for (File extra : mixinMappings.getFiles())
                net.fabricmc.loom.mcp.MixinMappings.merge(mappings, extra.toPath());
            net.fabricmc.loom.util.ArtifactTransaction.write(output, remapped -> {
                RemapperMcp.doIt(source, remapped, mappings, getLogger(), null, classpath, asm4Compatibility.get());
                if (normalizeArchive.get()) net.fabricmc.loom.ReleaseArchive.normalize(remapped);
                if (validateModArchive.get()) net.fabricmc.loom.ModValidation.archive(remapped.toFile());
            });
        } catch (Exception failure) {
            throw new org.gradle.api.GradleException("Cannot remap the release JAR: " + failure.getMessage(), failure);
        }
    }

    @InputFile
    @org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    public RegularFileProperty getInput() { return input; }
}

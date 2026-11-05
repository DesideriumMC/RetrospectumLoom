package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RemappedConfigurationEntry;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.mcp.Srg;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DependencyRemapperMcp extends NewProvider<DependencyRemapperMcp> {
	public DependencyRemapperMcp(Project project, LoomGradleExtension extension) {
		super(project, extension);
        this.project = project;
	}

    private final Project project;
	
	private String mappingsDepString;
    private String side = "joined";
	private Srg srg;
	private Members fields, methods;
	private Iterable<RemappedConfigurationEntry> remappedConfigurationEntries;
	private String distributionNamingScheme; //TODO weird
	private final Set<Path> remapClasspath = new LinkedHashSet<>();
	private boolean needsAsm4;
	
	public DependencyRemapperMcp mappingsDepString(String mappingsDepString) {
		this.mappingsDepString = mappingsDepString;
		return this;
	}

    public DependencyRemapperMcp side(String side) {
        this.side = side;
        return this;
    }
	
	public DependencyRemapperMcp srg(Srg srg) {
		this.srg = srg;
		return this;
	}
	
	public DependencyRemapperMcp fields(Members fields) {
		this.fields = fields;
		return this;
	}
	
	public DependencyRemapperMcp methods(Members methods) {
		this.methods = methods;
		return this;
	}
	
	public DependencyRemapperMcp remappedConfigurationEntries(Iterable<RemappedConfigurationEntry> remappedConfigurationEntries) {
		this.remappedConfigurationEntries = remappedConfigurationEntries;
		return this;
	}
	
	public DependencyRemapperMcp distributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	public DependencyRemapperMcp addToRemapClasspath(Collection<Path> remapClasspath) {
		this.remapClasspath.addAll(remapClasspath);
		return this;
	}
	
	public DependencyRemapperMcp addToRemapClasspath(Path... paths) {
		return addToRemapClasspath(Arrays.asList(paths));
	}
	
	public DependencyRemapperMcp needsAsm4(boolean needsAsm4) {
		this.needsAsm4 = needsAsm4;
		return this;
	}
	
    public DependencyRemapperMcp doIt(DependencyHandler deps) {
        for (RemappedConfigurationEntry entry : remappedConfigurationEntries) {
            Configuration input = entry.getInputConfig();
            String name = "remap" + Character.toUpperCase(entry.getName().charAt(0)) + entry.getName().substring(1) + "Dependencies";
            if (!"joined".equals(side)) name += Character.toUpperCase(side.charAt(0)) + side.substring(1);
            String outputPath = "retromixin/dependencies/" + ("joined".equals(side) ? "" : side + "/") + entry.getName();
            org.gradle.api.tasks.TaskProvider<net.fabricmc.loom.task.RemapModDependenciesTask> task = project.getTasks().register(
                name, net.fabricmc.loom.task.RemapModDependenciesTask.class, t -> {
                    t.setGroup(Constants.TASK_GROUP_PLUMBING);
                    t.getArchives().from(input);
                    t.getRemapClasspath().from(remapClasspath, getConfigurationByName(Constants.EVERY_UNMAPPED_MOD));
                    t.getSrgLines().set(net.fabricmc.loom.mcp.SrgSnapshot.lines(srg));
                    java.util.Map<String, String> fieldNames = new java.util.TreeMap<>(), methodNames = new java.util.TreeMap<>();
                    fields.members.forEach((from, to) -> fieldNames.put(from, to.remappedName));
                    methods.members.forEach((from, to) -> methodNames.put(from, to.remappedName));
                    t.getFieldNames().set(fieldNames);
                    t.getMethodNames().set(methodNames);
                    t.getDistributionNamespace().set(distributionNamingScheme);
                    t.getAsm4Compatibility().set(needsAsm4);
                    t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(outputPath));
                });
            // Derive paths without reading producer bytes. builtBy preserves the producer -> remap -> compile graph.
            org.gradle.api.file.ConfigurableFileCollection outputs = project.files(task.flatMap(
                net.fabricmc.loom.task.RemapModDependenciesTask::getOutputDirectory).map(directory -> {
                    java.util.List<File> files = new java.util.ArrayList<>();
                    for (File archive : input.getFiles()) files.add(directory.file(
                        net.fabricmc.loom.task.RemapModDependenciesTask.outputName(archive)).getAsFile());
                    return files;
                })).builtBy(task);
            deps.add(entry.getOutputConfig().getName(), outputs);
            project.getTasks().named("remappedConfigEntryFolderCopy").configure(t -> t.dependsOn(outputs));
        }
        return this;
    }
}

package net.fabricmc.loom.task.runs;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.RunConfig;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gradle task that generates IDEA @{code runConfigurations/*.xml} files.
 */
public class GenIdeaRunConfigsTask extends DefaultTask implements LoomTaskExt {
    private boolean gradleLaunchers;

    @org.gradle.api.tasks.Input
    public boolean isGradleLaunchers() { return gradleLaunchers; }
    public void setGradleLaunchers(boolean value) { gradleLaunchers = value; }

    public static String gradleLauncherXml(String name, String task) {
        return "<component name=\"ProjectRunConfigurationManager\"><configuration default=\"false\" name=\""
            + escape(name) + "\" type=\"GradleRunConfiguration\" factoryName=\"Gradle\"><ExternalSystemSettings>"
            + "<option name=\"externalProjectPath\" value=\"$PROJECT_DIR$\"/>"
            + "<option name=\"externalSystemIdString\" value=\"GRADLE\"/>"
            + "<option name=\"taskNames\"><list><option value=\"" + escape(task)
            + "\"/></list></option></ExternalSystemSettings><method v=\"2\"/></configuration></component>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

	public GenIdeaRunConfigsTask() {
		setGroup(Constants.TASK_GROUP_IDE);
		setDescription("Generates IDEA runConfigurations xml files, which can be used in IDEA as an alternative to the runClient task.");
	}
	
	@TaskAction
	public void doIt() throws Exception {
		Project project = getProject();
		LoomGradleExtension extension = getLoomGradleExtension();
		
		if(project != project.getRootProject()) {
			project.getLogger().lifecycle("Project " + project + " != root project " + project.getRootProject());
			return;
		}
		
		Path runConfigsDir = project.file(".idea").toPath().resolve("runConfigurations");
		Files.createDirectories(runConfigsDir);
		
		for(RunConfig cfg : extension.runConfigs) {
			if(!cfg.isIdeConfigGenerated()) continue;
			
			RunConfig cooked = cfg.cook(extension);
			
			Path cfgFile = runConfigsDir.resolve(cooked.getBaseName() + ".xml");
			if(Files.notExists(cfgFile)) {
				String taskName = "run" + cooked.getBaseName().substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                    + cooked.getBaseName().substring(1);
                String xml = gradleLaunchers ? gradleLauncherXml("Retrospectum " + cooked.getName(), taskName)
                    : cooked.configureTemplate("idea_run_config_template.xml");
                Files.write(cfgFile, xml.getBytes(StandardCharsets.UTF_8));
			}
			Files.createDirectories(cooked.resolveRunDir());
		}
	}
}

package edu.wwu.eas.builders;

import java.util.LinkedHashMap;
import java.util.Map;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bmuschko.gradle.docker.DockerExtension;
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin;

public class ReposyncApplicationPlugin implements Plugin<Project> {

    private final Logger logger = LoggerFactory.getLogger(ReposyncApplicationPlugin.class);

    public void apply(Project project) {
        project.getPluginManager().apply("base");
        project.getPluginManager().apply(DockerRemoteApiPlugin.class);

        // Set up extensions
        ExtensionAware dockerExtension = (ExtensionAware) (project.getExtensions().getByType(DockerExtension.class));
        ReposyncApplication application = dockerExtension.getExtensions().create("reposyncApplication", ReposyncApplication.class, project);

        Directory dockerSourceDirectory = project.getLayout().getProjectDirectory().dir("src/main/docker");
        Directory dockerBuildDirectory = project.getLayout().getBuildDirectory().dir("docker").get();

        // Register tasks
        logger.info("registering tasks");
        TaskContainer tasks = project.getTasks();

        // Get installDist task
        TaskProvider<Task> installDist = tasks.named("installDist");

        tasks.register("syncBuildContext", Sync.class, spec -> {
            spec.setDescription("Synchronize docker build context");
            spec.setGroup("Build application image");
            spec.dependsOn(installDist);

            // Get TextResource for default templates
            TextResource entrypoint = project.getResources().getText().fromUri(getClass().getClassLoader().getResource("templates/reposync/entrypoint.sh"));

            // Get properties to expand
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("application", application);
            properties.putAll(project.getProperties());

            // EXCLUDE doesn't work as expected, so we use INCLUDE and copy templates first.
            // That way content from src/main/docker will overwrite them.
            spec.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);

            // Copy assembled application from installDist task
            spec.from(installDist.get().getOutputs().getFiles().getAsFileTree());

            // Copy default templates from plugin
            spec.from(entrypoint, fromSpec -> { fromSpec.rename(old -> "entrypoint.sh"); fromSpec.expand(properties); });

            // Copy from src/main/docker, do this last so it can overwrite templates
            spec.from(dockerSourceDirectory, fromSpec -> fromSpec.expand(properties));
            spec.into(dockerBuildDirectory);

            // Copy into build/docker
            spec.into(dockerBuildDirectory);
        });

        application.registerBuildTasks();
    }

}

package edu.wwu.eas.builders;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import com.bmuschko.gradle.docker.tasks.image.Dockerfile;

import edu.wwu.eas.docker.extensions.GradleApplication;

public class ReposyncApplication extends GradleApplication {

    public ReposyncApplication(Project project) {
        super(project);
        baseImage.convention(hostedRegistry.map(h -> String.format("%s/eclipse-temurin:21-jdk", h)));
    }

    protected void configure(Dockerfile spec) {
        configureBaseImage(spec);
        spec.copyFile("lib", "/app/lib/");
        spec.copyFile("bin", "/app/bin/");
        spec.copyFile("entrypoint.sh", "/app/bin/");
        spec.environmentVariable("PATH", "$PATH:/app/bin");
        String workingDirectory = getWorkingDirectory().get();
        configureAddUser(spec);
        spec.runCommand(new StringBuilder()
            .append("mkdir -p ").append(workingDirectory).append(" && ")
            .append("chown ")
                .append(userName.get())
                .append(":")
                .append(groupName.get())
                .append(" ")
                .append(workingDirectory).append(" && ")
            .append("chmod +x /app/bin/entrypoint.sh")
            .toString());
        configureUser(spec);
        spec.workingDir(workingDirectory);
        spec.volume(workingDirectory);
        spec.entryPoint("/app/bin/entrypoint.sh");
    }

    protected void configureBaseImage(Dockerfile spec) {
        super.configureBaseImage(spec);
        spec.runCommand("""
            set -xv; \
            apt-get update -y && \
            apt-get install -y git openssh-client && \
            mkdir -p /app/bin
        """);
    }
}

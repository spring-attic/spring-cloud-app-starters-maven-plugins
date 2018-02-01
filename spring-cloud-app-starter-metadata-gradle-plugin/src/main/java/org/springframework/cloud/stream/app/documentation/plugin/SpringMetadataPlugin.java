package org.springframework.cloud.stream.app.documentation.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;

public class SpringMetadataPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        SpringMetadataTask metadataTask = project.getTasks().create(SpringMetadataTask.NAME, SpringMetadataTask.class);



        project.afterEvaluate(p->{
            Task buildTask = p.getTasks().findByName("build");
            if(null!=buildTask){
                buildTask.dependsOn(metadataTask);
            }
            p.getArtifacts().add("archives",metadataTask);

            if(p.getPlugins().hasPlugin(JavaPlugin.class)){
                metadataTask.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME,JavaPlugin.PROCESS_RESOURCES_TASK_NAME);
            }
        });
    }
}

/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.stream.app.documentation.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;

/**
 * A gradle spring metadata plugin
 *
 * @author Furer Alexander
 */
public class SpringMetadataPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		SpringMetadataTask metadataTask = project.getTasks().create(SpringMetadataTask.NAME, SpringMetadataTask.class);

		project.afterEvaluate(p -> {
			Task buildTask = p.getTasks().findByName("build");
			if (null != buildTask) {
				buildTask.dependsOn(metadataTask);
			}
			p.getArtifacts().add("archives", metadataTask);

			if (p.getPlugins().hasPlugin(JavaPlugin.class)) {
				metadataTask.dependsOn(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaPlugin.PROCESS_RESOURCES_TASK_NAME);
			}
		});
	}
}

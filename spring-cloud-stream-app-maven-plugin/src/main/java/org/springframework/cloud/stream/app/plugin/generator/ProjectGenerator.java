/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.stream.app.plugin.generator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

/**
 * @author Christian Tzolov
 */
public class ProjectGenerator {

	public static void main(String[] args) throws IOException {

		// Bom
		AppBom appBom = new AppBom()
				.withSpringBootVersion("2.3.0.M1")
				.withStreamAppsParentVersion("3.0.0.BUILD-SNAPSHOT")
				.withSpringCloudStreamDependenciesVersion("Horsham.SR2")
				.withSpringCloudFunctionDependenciesVersion("3.0.2.RELEASE")
				.withSpringCloudDependenciesVersion("Hoxton.RELEASE");

		// App
		AppDefinition app = new AppDefinition();
		app.setName("log");
		app.setType(AppDefinition.AppType.sink);
		app.setVersion("3.0.0.BUILD-SNAPSHOT");
		app.setFunctionClass("io.pivotal.java.function.log.consumer.LogConsumerConfiguration.class");
		//app.setAdditionalProperties(Arrays.asList(
		//		"spring.main.web-application-type=reactive",
		//		"spring.cloud.streamapp.security.enabled=false",
		//		"spring.cloud.streamapp.security.csrf-enabled=false"));

		app.getAdditionalMavenDependencies().add("<dependency>\n" +
				"      <groupId>io.pivotal.java.function</groupId>\n" +
				"      <artifactId>log-consumer</artifactId>\n" +
				"      <version>1.0.0.BUILD-SNAPSHOT</version>\n" +
				"    </dependency>");

		app.setMetadataSourceTypeFilters(Arrays.asList(
				"io.pivotal.java.function.log.consumer.LogConsumerProperties",
				"io.pivotal.java.function.log.consumer.LogConsumerProperties"));

		//app.getAdditionalMavenPlugins().add("<plugin>\n" +
		//		"        <groupId>org.springframework.cloud</groupId>\n" +
		//		"        <artifactId>spring-configuration-metadata-encoder</artifactId>\n" +
		//		"        <version>0.0.3.BUILD-SNAPSHOT</version>\n" +
		//		"        <executions>\n" +
		//		"          <execution>\n" +
		//		"            <id>encode-metadata</id>\n" +
		//		"            <phase>process-classes</phase>\n" +
		//		"            <goals>\n" +
		//		"              <goal>encode</goal>\n" +
		//		"            </goals>\n" +
		//		"          </execution>\n" +
		//		"        </executions>\n" +
		//		"      </plugin>");

		// Generator Properties
		ProjectGeneratorProperties generatorProperties = new ProjectGeneratorProperties();
		generatorProperties.setOverrideAllowed(true);
		generatorProperties.setOutputFolder(new File("./target/apps"));
		generatorProperties.setAppBom(appBom);
		generatorProperties.setAppDefinition(app);

		new ProjectGenerator().generate(generatorProperties);
	}

	public void generate(ProjectGeneratorProperties generatorProperties) throws IOException {

		Map<String, Object> containerTemplateProperties = createContainerTemplateProperties();
		containerTemplateProperties.put("bom", generatorProperties.getAppBom());
		containerTemplateProperties.put("app", generatorProperties.getAppDefinition());

		// ---------------------------------
		// Generate apps container POM
		// ---------------------------------
		File appParentDir = generatorProperties.getOutputFolder();
		if (appParentDir.exists() && generatorProperties.isOverrideAllowed()) {
			removeRecursively(appParentDir);
		}
		appParentDir.mkdirs();
		copy(materialize("template/apps-container-pom.xml", containerTemplateProperties),
				file(appParentDir, "pom.xml"));

		// ---------------------------------
		// Generate App projects
		// ---------------------------------
		generateAppProject(appParentDir, containerTemplateProperties,
				generatorProperties.getAppDefinition(), "kafka");
		generateAppProject(appParentDir, containerTemplateProperties,
				generatorProperties.getAppDefinition(), "rabbit");
	}

	private Map<String, Object> createContainerTemplateProperties() {
		Map<String, Object> containerTemplateProperties = new HashMap<>();
		// register {{#capitalize}}...{{/capitalize}} function.
		containerTemplateProperties.put("capitalize",
				(Mustache.Lambda) (frag, out) -> out.write(capitalize(frag.execute().trim())));
		// register {{#camelCase}}...{{/camelCase}} function.
		containerTemplateProperties.put("camelCase",
				(Mustache.Lambda) (frag, out) -> out.write(camelCase(frag.execute().trim())));
		return containerTemplateProperties;
	}

	private void generateAppProject(File appRootDirectory, Map<String, Object> containerTemplateProperties,
			AppDefinition appDefinition, String binder) throws IOException {

		String appClassName = String.format("%s%s%sApplication",
				camelCase(appDefinition.getName()),
				capitalize(appDefinition.getType().name()),
				capitalize(binder));

		String appPackageName = String.format("org.springframework.cloud.stream.app.%s.%s.%s",
				toPkg(appDefinition.getName()),
				appDefinition.getType(),
				binder);

		Map<String, Object> appTemplateProperties = new HashMap<>(containerTemplateProperties);

		// Shortcut substitutions. Prevent complicated expressions in the templates.
		appTemplateProperties.put("app-class-name", appClassName);
		appTemplateProperties.put("app-package-name", appPackageName);
		appTemplateProperties.put("app-binder", binder);

		// app POM
		File appDir = file(appRootDirectory, appDefinition.getName() + "-" + appDefinition.getType() + "-" + binder);
		appDir.mkdir();

		copy(materialize("template/app-pom.xml", appTemplateProperties), file(appDir, "pom.xml"));


		File appMainSrcDir = pkgToDir(appDir, "src.main.java." + appPackageName);
		appMainSrcDir.mkdirs();

		File appMainResourceDir = pkgToDir(appDir, "src.main.resources");
		appMainResourceDir.mkdirs();

		File appMetaInfDir = pkgToDir(appDir, "src.main.resources.META-INF");
		appMetaInfDir.mkdirs();

		// application.properties
		copy(materialize("template/app.properties", appTemplateProperties),
				file(appMainResourceDir, "application.properties"));

		copy(materialize("template/App.java", appTemplateProperties),
				file(appMainSrcDir, appClassName + ".java"));

		// TESTS
		File appTestSrcDir = pkgToDir(appDir, "src.test.java." + appPackageName);
		appTestSrcDir.mkdirs();

		copy(materialize("template/AppTests.java", appTemplateProperties),
				file(appTestSrcDir, appClassName + "Tests.java"));

		// README
		copy(materialize("template/README.adoc", appTemplateProperties),
				file(appDir, "README.adoc"));

	}

	private String materialize(String templatePath, Map<String, Object> templateProperties) throws IOException {
		try (InputStreamReader resourcesTemplateReader = new InputStreamReader(
				Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(templatePath)))) {
			Template resourceTemplate = Mustache.compiler().escapeHTML(false).compile(resourcesTemplateReader);
			return resourceTemplate.execute(templateProperties);
		}
	}

	// Utils
	public static void copy(String content, File file) throws IOException {
		Files.copy(new ByteArrayInputStream(content.getBytes()), file.toPath());
	}

	public static String toPkg(String text) {
		return text.replace("-", ".");
	}

	public static String capitalize(String text) {
		return text.substring(0, 1).toUpperCase() + text.substring(1);
	}

	public static String camelCase(String text) {
		return Arrays.stream(text.split("-")).reduce("", (r, p) -> r + capitalize(p));
	}

	public static File pkgToDir(File parent, String packageName) {
		String[] names = packageName.split("\\.");
		File result = parent;
		for (String p : names) {
			result = file(result, p);
		}
		return result;
	}

	public static File file(File parent, String child) {
		return new File(parent, child);
	}

	/**
	 *
	 * Files.walk() returns a Stream of Path that we sort in reverse order.
	 * This places the paths denoting the contents of directories before
	 * directories itself. Thereafter it maps Path to File and deletes each File.
	 * @param dir directory to delete recursively.
	 * @throws IOException
	 */
	public static void removeRecursively(File dir) throws IOException {
		Files.walk(dir.toPath())
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}

}

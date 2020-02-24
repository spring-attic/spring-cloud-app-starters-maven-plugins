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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 */
public class ProjectGenerator {

	public void generate(ProjectGeneratorProperties generatorProperties) throws IOException {

		Map<String, Object> containerTemplateProperties = new HashMap<>();
		// register {{#capitalize}}...{{/capitalize}} function.
		containerTemplateProperties.put("capitalize", (Mustache.Lambda) (frag, out) -> out.write(capitalize(frag.execute().trim())));
		// register {{#camelCase}}...{{/camelCase}} function.
		containerTemplateProperties.put("camelCase", (Mustache.Lambda) (frag, out) -> out.write(camelCase(frag.execute().trim())));

		containerTemplateProperties.put("bom", generatorProperties.getAppBom());
		containerTemplateProperties.put("app", generatorProperties.getAppDefinition());
		containerTemplateProperties.put("binders", generatorProperties.getBinders());

		// ---------------------------------
		// Generate apps container POM
		// ---------------------------------
		File appParentDir = mkdirs(generatorProperties.getOutputFolder());
		copy(materialize("template/apps-container-pom.xml", containerTemplateProperties),
				file(appParentDir, "pom.xml"));
		// maven wrapper
		copyMavenWrapper(appParentDir);

		// ---------------------------------
		// Generate App projects
		// ---------------------------------
		Assert.notEmpty(generatorProperties.getBinders(), "At least one Binder must be provided");
		for (String binder : generatorProperties.getBinders()) {
			generateAppProject(appParentDir, containerTemplateProperties, generatorProperties.getAppDefinition(), binder);
		}
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
		File appDir =
				mkdirs(file(appRootDirectory, appDefinition.getName() + "-" + appDefinition.getType() + "-" + binder));

		copy(materialize("template/app-pom.xml", appTemplateProperties), file(appDir, "pom.xml"));

		File appMainSrcDir = mkdirs(pkgToDir(appDir, "src.main.java." + appPackageName));

		File appMainResourceDir = mkdirs(pkgToDir(appDir, "src.main.resources"));

		// application.properties
		copy(materialize("template/app.properties", appTemplateProperties),
				file(appMainResourceDir, "application.properties"));

		copy(materialize("template/App.java", appTemplateProperties),
				file(appMainSrcDir, appClassName + ".java"));

		// TESTS
		File appTestSrcDir = mkdirs(pkgToDir(appDir, "src.test.java." + appPackageName));

		copy(materialize("template/AppTests.java", appTemplateProperties),
				file(appTestSrcDir, appClassName + "Tests.java"));

		// README
		copy(materialize("template/README.adoc", appTemplateProperties),
				file(appDir, "README.adoc"));

		// maven wrapper
		copyMavenWrapper(appDir);
	}

	private void copyMavenWrapper(File appDir) throws IOException {

		// mvnw
		copyResource("template/mvnw", file(appDir, "mvnw"));
		file(appDir, "mvnw").setExecutable(true);

		copyResource("template/mvnw.cmd", file(appDir, "mvnw.cmd"));

		File dotMavenDir = mkdirs(new File(appDir, ".mvn"));

		// .mvn/jvm.config
		copyResource("template/.mvn/jvm.config", file(dotMavenDir, "jvm.config"));

		// .mvn/maven.config
		copyResource("template/.mvn/maven.config", file(dotMavenDir, "maven.config"));

		File dotMavenWrapper = mkdirs(file(appDir, ".mvn/wrapper"));

		// .mvn/wrapper/maven-wrapper.jar
		copyResource("template/.mvn/wrapper/maven-wrapper.jar",
				file(dotMavenWrapper, "maven-wrapper.jar"));

		// .mvn/wrapper/maven-wrapper.properties
		copyResource("template/.mvn/wrapper/maven-wrapper.properties",
				file(dotMavenWrapper, "maven-wrapper.properties"));

		// .mvn/wrapper/MavenWrapperDownloader.java
		copyResource("template/.mvn/wrapper/MavenWrapperDownloader.java",
				file(dotMavenWrapper, "MavenWrapperDownloader.java"));
	}

	private String materialize(String templatePath, Map<String, Object> templateProperties) throws IOException {
		try (InputStreamReader resourcesTemplateReader = new InputStreamReader(
				Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(templatePath)))) {
			Template resourceTemplate = Mustache.compiler().escapeHTML(false).compile(resourcesTemplateReader);
			return resourceTemplate.execute(templateProperties);
		}
	}

	private void copyResource(String resourcePath, File toFile) throws IOException {
		try (InputStream resourcesStream =
					 Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(resourcePath))) {
			Files.copy(resourcesStream, toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// Utils
	public static File mkdirs(File dir) {
		if (!dir.exists()) {
			dir.mkdirs();
		}
		if (!dir.isDirectory()) {
			throw new IllegalStateException("Not a directory: " + dir);
		}
		return dir;
	}

	public static void copy(String content, File file) throws IOException {
		Files.copy(new ByteArrayInputStream(content.getBytes()), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
}

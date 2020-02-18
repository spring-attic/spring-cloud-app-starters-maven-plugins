/*
 * Copyright 2020-2020 the original author or authors.
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
package org.springframework.cloud.stream.app.plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.util.Pair;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.springframework.cloud.stream.app.plugin.generator.AppBom;
import org.springframework.cloud.stream.app.plugin.generator.AppDefinition;
import org.springframework.cloud.stream.app.plugin.generator.ProjectGenerator;
import org.springframework.cloud.stream.app.plugin.generator.ProjectGeneratorProperties;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
@Mojo(name = "generate-app")
public class SpringCloudStreamAppGeneratorMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "Docker", required = true)
	private AppDefinition.ContainerImageFormat containerImageFormat;

	@Parameter(defaultValue = "false")
	private boolean enableContainerImageMetadata;

	@Parameter(defaultValue = "./apps", required = true)
	private String appsFolder;

	@Parameter(required = true)
	private String generatedProjectVersion; // "3.0.0.BUILD-SNAPSHOT"

	@Parameter(required = true)
	private Map<String, String> generatedApps; // TODO remove GeneratableApp and make it a list

	@Parameter(required = true)
	String configClass;

	@Parameter
	List<String> additionalAppProperties;

	@Parameter
	List<String> metadataSourceTypeFilters;

	@Parameter
	List<String> metadataNameFilters;

	@Parameter
	List<Dependency> dependencies = new ArrayList<>();

	@Parameter
	List<Plugin> additionalPlugins = new ArrayList<>();

	// Versions
	@Parameter(defaultValue = "2.2.4.RELEASE", required = true)
	private String bootVersion;

	@Parameter(required = true)
	private String streamAppsParentVersion; //core 3.0.0.BUILD-SNAPSHOT

	@Parameter(defaultValue = "${spring-cloud-stream-dependencies.version}", required = true)
	private String springCloudStreamDependenciesVersion; // Horsham.SR2

	@Parameter(defaultValue = "${spring-cloud-function-dependencies.version}", required = true)
	private String springCloudFunctionDependenciesVersion; // 3.0.2.RELEASE

	@Parameter(defaultValue = "${spring-cloud-dependencies.version}", required = true)
	private String springCloudDependenciesVersion; // Hoxton.RELEASE

	@Override
	public void execute() throws MojoFailureException {
		// Bom
		AppBom appBom = new AppBom()
				.withSpringBootVersion(this.bootVersion)
				.withStreamAppsParentVersion(this.streamAppsParentVersion) // SCSt apps Core version
				.withSpringCloudStreamDependenciesVersion(this.springCloudStreamDependenciesVersion)
				.withSpringCloudFunctionDependenciesVersion(this.springCloudFunctionDependenciesVersion)
				.withSpringCloudDependenciesVersion(this.springCloudDependenciesVersion);

		// Application project definition
		// FIXME assumes a single appNameType definition!!! is this correct? If so shouldn't we simplify the configuration format?
		String appNameAndTypeCombined = generatedApps.entrySet().iterator().next().getKey();
		Pair<String, AppDefinition.AppType> appNameType = this.extractApplicationNameAndType(appNameAndTypeCombined);

		AppDefinition app = new AppDefinition();
		app.setName(appNameType.getKey());
		app.setType(appNameType.getValue());
		app.setVersion(this.generatedProjectVersion);
		app.setFunctionClass(this.configClass);
		app.setContainerImageFormat(this.containerImageFormat);
		app.setEnableContainerImageMetadata(this.enableContainerImageMetadata);

		app.setMetadataSourceTypeFilters(this.metadataSourceTypeFilters);
		app.setMetadataNameFilters(this.metadataNameFilters);

		app.setAdditionalProperties(this.additionalAppProperties);

		List<String> stringDependencies = this.dependencies.stream().map(MavenXmlWriter::toXml).collect(Collectors.toList());
		app.setAdditionalMavenDependencies(stringDependencies);

		List<String> plugins = this.additionalPlugins.stream().map(MavenXmlWriter::toXml).collect(Collectors.toList());
		app.setAdditionalMavenPlugins(plugins);


		// Generator Properties
		ProjectGeneratorProperties generatorProperties = new ProjectGeneratorProperties();
		generatorProperties.setOverrideAllowed(true);
		generatorProperties.setOutputFolder(new File(this.appsFolder));
		generatorProperties.setAppBom(appBom);
		generatorProperties.setAppDefinition(app);

		try {
			new ProjectGenerator().generate(generatorProperties);
		}
		catch (IOException e) {
			throw new MojoFailureException("Project generation failure");
		}
	}

	/**
	 * Converts 'app-name-type' into 'app-name' as key
	 * and {@link org.springframework.cloud.stream.app.plugin.generator.AppDefinition.AppType}
	 * (e.g. source, processor, sink) as a value.
	 * @param applicationAndTypeName Application and Name expression defined in the POM.
	 */
	Pair<String, AppDefinition.AppType> extractApplicationNameAndType(String applicationAndTypeName) throws MojoFailureException {
		int index = applicationAndTypeName.lastIndexOf("-");
		if (index < 0) {
			throw new MojoFailureException("Invalid application <name>-<type> format:" + applicationAndTypeName);
		}
		String appNamePrefix = applicationAndTypeName.substring(0, index).trim();
		String appTypeSuffix = applicationAndTypeName.substring(index + 1).trim();
		try {
			AppDefinition.AppType appType = AppDefinition.AppType.valueOf(appTypeSuffix);
			return new Pair(appNamePrefix, appType);
		}
		catch (IllegalArgumentException iae) {
			throw new MojoFailureException(String.format("Invalid application type: %s . " +
					"Only source, processor, sink types are allowed!", appTypeSuffix));
		}
	}
}

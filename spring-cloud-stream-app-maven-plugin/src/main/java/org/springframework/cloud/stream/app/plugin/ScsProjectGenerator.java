/*
 * Copyright 2015-2016 the original author or authors.
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import io.spring.initializr.generator.ProjectGenerator;
import io.spring.initializr.generator.ProjectRequest;
import io.spring.initializr.metadata.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import org.springframework.cloud.stream.app.plugin.utils.MavenModelUtils;
import org.springframework.cloud.stream.app.plugin.utils.SpringCloudStreamPluginUtils;
import org.springframework.util.CollectionUtils;

/**
 *
 * @author Gunnar Hillert
 * @author Soby Chacko
 *
 */
public class ScsProjectGenerator extends ProjectGenerator {

	private String dockerHubOrg;

	private String bomsWithHigherPrecedence;

	private List<Bom> additionalBoms;

	private Properties properties;

	private List<Plugin> additionalPlugins;

	private List<Dependency> requiresUnpack;

	private String metadataPluginVersion = "2.0.0.BUILD-SNAPSHOT";

	private List<String> metadataNameFilters = new ArrayList<>();

	private List<String> metadataSourceTypesFilters = new ArrayList<>();

	private boolean enableContainerImageMetadata;

	@Override
	protected File doGenerateProjectStructure(ProjectRequest request) {
		return doGenerateProjectStructure(request, MavenModelUtils.ENTRYPOINT_TYPE_EXEC);
	}

	protected File doGenerateProjectStructure(ProjectRequest request, String entrypointType) {
		final File rootDir = super.doGenerateProjectStructure(request);

		final File dir = new File(rootDir, request.getBaseDir());

		// Override the maven version from 3.3.3 to 3.6.2
		try {
			File mavenWrapperProperties = new File(new File(new File(dir, ".mvn"), "wrapper"), "maven-wrapper.properties");
			FileWriter writter = new FileWriter(mavenWrapperProperties, false);
			writter.write("distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.6.2/apache-maven-3.6.2-bin.zip");
			writter.close();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}

		final File dockerDir = new File(dir, "src/main/docker");
		dockerDir.mkdirs();
		write(new File(dockerDir, "assembly.xml"), "assembly.xml", initializeModel(request));

		final File inputFile = new File(dir, "pom.xml");
		final File tempOutputFile0 = new File(dir, "pom_tmp0.xml");
		final File tempOutputFile1 = new File(dir, "pom_tmp1.xml");
		final File tempOutputFile2 = new File(dir, "pom_tmp2.xml");
		final File tempOutputFile3 = new File(dir, "pom_tmp3.xml");

		try {
			final InputStream is = new FileInputStream(inputFile);
			final OutputStream os = new FileOutputStream(tempOutputFile0);

			MavenModelUtils.addPropertiesMavenPlugin(is, os, enableContainerImageMetadata);

			FileInputStream is0 = new FileInputStream(tempOutputFile0);
			FileOutputStream os0 = new FileOutputStream(tempOutputFile1);

			MavenModelUtils.addDockerPlugin(request.getArtifactId(), request.getVersion(), dockerHubOrg, is0, os0, entrypointType, enableContainerImageMetadata);

			FileInputStream is1 = new FileInputStream(tempOutputFile1);
			FileOutputStream os1 = new FileOutputStream(tempOutputFile2);

			MavenModelUtils.addPMetadataMavenPlugin(is1, os1, metadataPluginVersion, metadataNameFilters, metadataSourceTypesFilters, enableContainerImageMetadata);

			FileInputStream is2 = new FileInputStream(tempOutputFile2);
			FileOutputStream os2 = new FileOutputStream(tempOutputFile3);

			Model pomModel = MavenModelUtils.getModel(is2);
			for (Plugin plugin : additionalPlugins) {
				pomModel.getBuild().addPlugin(plugin);
			}
			MavenModelUtils.addExtraPlugins(pomModel);
			MavenModelUtils.addPluginRepositories(pomModel);

			if (!CollectionUtils.isEmpty(requiresUnpack)) {
				Optional<Plugin> springBootPlugin = pomModel.getBuild().getPlugins().stream()
						.filter(plugin -> plugin.getArtifactId().equals("spring-boot-maven-plugin"))
						.findFirst();
				if (springBootPlugin.isPresent()) {
					Plugin plugin = springBootPlugin.get();
					pomModel.getBuild().removePlugin(plugin);

					final Xpp3Dom xpp3Dom = new Xpp3Dom("configuration");
					Xpp3Dom xpp3Dom1 = SpringCloudStreamPluginUtils.addElement(xpp3Dom, "requiresUnpack");
					for (Dependency dependency : requiresUnpack) {
						Xpp3Dom xpp3Dom2 = SpringCloudStreamPluginUtils.addElement(xpp3Dom1, "dependency");
						Xpp3Dom xpp3Dom3 = new Xpp3Dom("groupId");
						xpp3Dom3.setValue(dependency.getGroupId());
						Xpp3Dom xpp3Dom4 = new Xpp3Dom("artifactId");
						xpp3Dom4.setValue(dependency.getArtifactId());
						xpp3Dom2.addChild(xpp3Dom3);
						xpp3Dom2.addChild(xpp3Dom4);
					}
					plugin.setConfiguration(xpp3Dom);
					pomModel.getBuild().addPlugin(plugin);
				}
			}

			MavenModelUtils.addBomsWithHigherPrecedence(pomModel, bomsWithHigherPrecedence);
			if (!CollectionUtils.isEmpty(additionalBoms)) {
				MavenModelUtils.addAdditionalBoms(pomModel, additionalBoms);
			}
			MavenModelUtils.addExclusionsForKafka(pomModel);
			MavenModelUtils.addDistributionManagement(pomModel);
			MavenModelUtils.addProfiles(pomModel);
			MavenModelUtils.addProperties(pomModel, properties);
			MavenModelUtils.writeModelToFile(pomModel, os2);

			is.close();
			is0.close();
			is1.close();
			is2.close();
			os.close();
			os0.close();
			os1.close();
			os2.close();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}

		inputFile.delete();
		tempOutputFile3.renameTo(inputFile);
		tempOutputFile0.delete();
		tempOutputFile1.delete();
		tempOutputFile2.delete();
		tempOutputFile3.delete();

		return rootDir;
	}

	public void setDockerHubOrg(String dockerHubOrg) {
		this.dockerHubOrg = dockerHubOrg;
	}

	public void setBomsWithHigherPrecedence(String bomsWithHigherPrecedence) {
		this.bomsWithHigherPrecedence = bomsWithHigherPrecedence;
	}

	public void setAdditionalBoms(List<Bom> additionalBoms) {
		this.additionalBoms = additionalBoms;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	public void setAdditionalPlugins(List<Plugin> additionalPlugins) {
		this.additionalPlugins = additionalPlugins;
	}

	public void setRequiresUnpack(List<Dependency> requiresUnpack) {
		this.requiresUnpack = requiresUnpack;
	}

	public void setMetadataPluginVersion(String metadataPluginVersion) {
		this.metadataPluginVersion = metadataPluginVersion;
	}

	public void setMetadataNameFilters(List<String> metadataNameFilters) {
		this.metadataNameFilters = metadataNameFilters;
	}

	public void setMetadataSourceTypesFilters(List<String> metadataSourceTypesFilters) {
		this.metadataSourceTypesFilters = metadataSourceTypesFilters;
	}

	public void setEnableContainerImageMetadata(boolean enableContainerImageMetadata) {
		this.enableContainerImageMetadata = enableContainerImageMetadata;
	}
}

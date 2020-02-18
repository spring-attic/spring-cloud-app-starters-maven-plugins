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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.springframework.cloud.stream.app.plugin.generator.AppDefinition;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Christian Tzolov
 */
public class SpringCloudStreamAppGeneratorMojoTest {

	@Rule
	public TemporaryFolder projectHome = new TemporaryFolder();

	private SpringCloudStreamAppGeneratorMojo springCloudStreamAppMojo = new SpringCloudStreamAppGeneratorMojo();

	private Class<? extends SpringCloudStreamAppGeneratorMojo> mojoClazz = springCloudStreamAppMojo.getClass();

	@Before
	public void before() throws NoSuchFieldException {
		setMojoProperty("generatedProjectVersion", "3.0.0.BUILD-SNAPSHOT");
		setMojoProperty("configClass", "io.pivotal.java.function.log.consumer.LogConsumerConfiguration.class");

		Map<String, String> generatedApps = new HashMap<>();
		generatedApps.put("log-sink", "");
		setMojoProperty("generatedApps", generatedApps);

		setMojoProperty("metadataSourceTypeFilters", Arrays.asList("io.pivotal.java.function.log.consumer.LogConsumerProperties"));
		setMojoProperty("metadataNameFilters", Arrays.asList("server.port"));

		setMojoProperty("additionalAppProperties", Arrays.asList(
				"spring.cloud.streamapp.security.enabled=false",
				"spring.cloud.streamapp.security.csrf-enabled=false"));

		Dependency dep = new Dependency();
		dep.setGroupId("io.pivotal.java.function");
		dep.setArtifactId("log-consumer");
		dep.setVersion("1.0.0.BUILD-SNAPSHOT");
		setMojoProperty("dependencies", Arrays.asList(dep));

		setMojoProperty("containerImageFormat", AppDefinition.ContainerImageFormat.Docker);

		// BOM
		setMojoProperty("bootVersion", "2.3.0.M1");
		setMojoProperty("streamAppsParentVersion", "3.0.0.BUILD-SNAPSHOT");
		setMojoProperty("springCloudStreamDependenciesVersion", "Horsham.SR2");
		setMojoProperty("springCloudFunctionDependenciesVersion", "3.0.2.RELEASE");
		setMojoProperty("springCloudDependenciesVersion", "Hoxton.RELEASE");

		//setMojoProperty("appsFolder", "./target/apps");
		setMojoProperty("appsFolder", projectHome.getRoot().getAbsolutePath());
	}

	@Test
	public void testWithDisabledContainerMetadata() throws Exception {

		// disable metadata in container image (Default)
		setMojoProperty("enableContainerImageMetadata", false);

		this.springCloudStreamAppMojo.execute();

		Model pomModel = getModel(new File(projectHome.getRoot().getAbsolutePath()));
		List<Plugin> plugins = pomModel.getBuild().getPlugins();

		// The properties-maven-plugin should not be defined if the container metadata is not enabled.
		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("properties-maven-plugin")).count()).isEqualTo(0);

		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("jib-maven-plugin")).count()).isEqualTo(1);

		Plugin jibPlugin = plugins.stream().filter(p -> p.getArtifactId().equals("jib-maven-plugin")).findFirst().get();
		assertThat(jibPlugin.getConfiguration().toString())
				.doesNotContain("<spring.configuration.metadata>${spring.configuration.metadata.encoded}</spring.configuration.metadata>");
	}

	@Test
	public void testDefaultProjectCreationByPlugin() throws Exception {

		// Enable Metadata in Container Image!
		setMojoProperty("enableContainerImageMetadata", true);

		this.springCloudStreamAppMojo.execute();

		//assertGeneratedPomXml(new File("./target/apps"));
		assertGeneratedPomXml(new File(projectHome.getRoot().getAbsolutePath()));
	}

	private void assertGeneratedPomXml(File rootPath) throws Exception {

		Model pomModel = getModel(rootPath);

		List<Dependency> dependencies = pomModel.getDependencies();
		assertThat(dependencies.size()).isEqualTo(15);

		assertThat(dependencies.stream()
				.filter(d -> d.getArtifactId().equals("log-consumer")).count()).isEqualTo(1L);

		assertThat(dependencies.stream()
				.filter(d -> d.getArtifactId().equals("spring-cloud-starter-stream-kafka")).count()).isEqualTo(1L);

		Parent parent = pomModel.getParent();
		assertThat(parent.getArtifactId()).isEqualTo("spring-boot-starter-parent");
		assertThat(parent.getVersion()).isEqualTo("2.3.0.M1");

		assertThat(pomModel.getArtifactId()).isEqualTo("log-sink-kafka");
		assertThat(pomModel.getGroupId()).isEqualTo("org.springframework.cloud.stream.app");
		assertThat(pomModel.getName()).isEqualTo("log-sink-kafka");
		assertThat(pomModel.getVersion()).isEqualTo("3.0.0.BUILD-SNAPSHOT");
		assertThat(pomModel.getDescription()).isEqualTo("Spring Cloud Stream Log Sink Kafka Binder Application");

		List<Plugin> plugins = pomModel.getBuild().getPlugins();
		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("spring-boot-maven-plugin")).count()).isEqualTo(1);
		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("properties-maven-plugin")).count()).isEqualTo(1);
		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("jib-maven-plugin")).count()).isEqualTo(1);

		Plugin jibPlugin = plugins.stream().filter(p -> p.getArtifactId().equals("jib-maven-plugin")).findFirst().get();
		assertThat(jibPlugin.getConfiguration().toString())
				.contains("<spring.configuration.metadata>${spring.configuration.metadata.encoded}</spring.configuration.metadata>");

		assertThat(pomModel.getRepositories().size()).isEqualTo(2);
	}

	private Model getModel(File rootPath) {
		File pomXml = new File(new File(rootPath, "log-sink-kafka"), "pom.xml");
		try (InputStream is = new FileInputStream(pomXml)) {
			return new MavenXpp3Reader().read(is);
		}
		catch (IOException | XmlPullParserException e) {
			throw new IllegalStateException(e);
		}
	}

	private void setMojoProperty(String propertyName, Object value) throws NoSuchFieldException {
		Field mojoProperty = this.mojoClazz.getDeclaredField(propertyName);
		mojoProperty.setAccessible(true);
		ReflectionUtils.setField(mojoProperty, this.springCloudStreamAppMojo, value);
	}
}

/*
 * Copyright 2015-2018 the original author or authors.
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

package org.springframework.cloud.stream.app.plugin;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.springframework.util.ReflectionUtils;

/**
 * @author Soby Chacko
 * @author Glenn Renfro
 * @author Artem Bilan
 */
public class SpringCloudStreamAppMojoTest {

	@Rule
	public TemporaryFolder projectHome = new TemporaryFolder();

	private SpringCloudStreamAppMojo springCloudStreamAppMojo = new SpringCloudStreamAppMojo();

	private Class<? extends SpringCloudStreamAppMojo> mojoClazz = springCloudStreamAppMojo.getClass();

	private Set<String> appPropertyValues;

	@Before
	public void setup() throws Exception {

		Field applicationType = this.mojoClazz.getDeclaredField("applicationType");
		applicationType.setAccessible(true);
		ReflectionUtils.setField(applicationType, this.springCloudStreamAppMojo, "stream");

		Field bootVersion = this.mojoClazz.getDeclaredField("bootVersion");
		bootVersion.setAccessible(true);
		ReflectionUtils.setField(bootVersion, this.springCloudStreamAppMojo, "1.3.5.RELEASE");

		Field generatedProjectVersion = mojoClazz.getDeclaredField("generatedProjectVersion");
		generatedProjectVersion.setAccessible(true);
		ReflectionUtils.setField(generatedProjectVersion, this.springCloudStreamAppMojo, "1.0.0.BUILD-SNAPSHOT");

		Field binders = this.mojoClazz.getDeclaredField("binders");
		binders.setAccessible(true);
		Map<String, String> binders1 = new HashMap<>();
		binders1.put("kafka", null);
		ReflectionUtils.setField(binders, this.springCloudStreamAppMojo, binders1);

		Field generatedApps = mojoClazz.getDeclaredField("generatedApps");
		generatedApps.setAccessible(true);
		Map<String, GeneratableApp> generatableApps = new HashMap<>();
		generatableApps.put("foo-source", new GeneratableApp());
		ReflectionUtils.setField(generatedApps, this.springCloudStreamAppMojo, generatableApps);

		Bom bom = new Bom();
		bom.setArtifactId("spring-cloud-stream-app-dependencies");
		bom.setGroupId("org.springframework.cloud.stream.app");
		bom.setVersion("1.0.0.BUILD-SNAPSHOT");
		bom.setName("scs-bom");

		Field bomField = mojoClazz.getDeclaredField("bom");
		bomField.setAccessible(true);
		ReflectionUtils.setField(bomField, this.springCloudStreamAppMojo, bom);

		this.appPropertyValues = new HashSet<>(6);
		this.appPropertyValues.add("spring.application.name=${vcap.application.name:foo-source}");
		this.appPropertyValues.add("info.app.name=@project.artifactId@");
		this.appPropertyValues.add("info.app.description=@project.description@");
		this.appPropertyValues.add("info.app.version=@project.version@");
		this.appPropertyValues.add("management.endpoints.web.exposure.include=health,info,bindings");
	}

	@Test
	public void testDefaultProjectCreationByPlugin() throws Exception {
		Field generatedProjectHome = mojoClazz.getDeclaredField("generatedProjectHome");
		generatedProjectHome.setAccessible(true);
		ReflectionUtils.setField(generatedProjectHome, this.springCloudStreamAppMojo,
				this.projectHome.getRoot().getAbsolutePath());

		this.springCloudStreamAppMojo.execute();

		Stream<Path> pathStream =
				Files.find(this.projectHome.getRoot().toPath(), 3,
						(path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

		Path path = pathStream.findFirst().get();
		assertNotNull(path);

		assertGeneratedPomXml(path);
	}

	@Test
	public void testProjectCreatedIntoGeneratedProjectHome() throws Exception {
		Field generatedProjectHome = mojoClazz.getDeclaredField("generatedProjectHome");
		generatedProjectHome.setAccessible(true);
		ReflectionUtils.setField(generatedProjectHome, this.springCloudStreamAppMojo,
				this.projectHome.getRoot().getAbsolutePath());

		this.springCloudStreamAppMojo.execute();

		Stream<Path> pathStream =
				Files.find(this.projectHome.getRoot().toPath(), 3,
						(path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

		Path path = pathStream.findFirst().get();
		assertNotNull(path);

		assertGeneratedPomXml(path);

	}

	@Test
	public void testAppPropertiesNoAppProp() throws Exception {
		Field generatedProjectHome = this.mojoClazz.getDeclaredField("generatedProjectHome");
		generatedProjectHome.setAccessible(true);
		ReflectionUtils.setField(generatedProjectHome, this.springCloudStreamAppMojo,
				this.projectHome.getRoot().getAbsolutePath());
		this.springCloudStreamAppMojo.execute();
		validateApplicationProperties(5);
	}

	@Test
	public void testAppPropertiesWithAppProp() throws Exception {
		final String ENTRY_ONE = "hello=world";
		final String ENTRY_TWO = "foo=bar";
		Field generatedProjectHome = this.mojoClazz.getDeclaredField("generatedProjectHome");
		generatedProjectHome.setAccessible(true);
		ReflectionUtils.setField(generatedProjectHome, this.springCloudStreamAppMojo,
				this.projectHome.getRoot().getAbsolutePath());
		Field appPropertiesField = this.mojoClazz.getDeclaredField("additionalAppProperties");
		appPropertiesField.setAccessible(true);
		List<String> appProperties = new ArrayList<>();
		appProperties.add(ENTRY_ONE);
		appProperties.add(ENTRY_TWO);
		ReflectionUtils.setField(appPropertiesField, this.springCloudStreamAppMojo, appProperties);

		this.springCloudStreamAppMojo.execute();

		this.appPropertyValues.add(ENTRY_ONE);
		this.appPropertyValues.add(ENTRY_TWO);
		validateApplicationProperties(7);
	}


	@Test
	public void testAppPropertiesWithExistingProps() throws Exception {
		final String ENTRY_ONE = "spring.application.name =foo";
		final String ENTRY_TWO = "foo=bar";
		Field generatedProjectHome = this.mojoClazz.getDeclaredField("generatedProjectHome");
		generatedProjectHome.setAccessible(true);
		ReflectionUtils.setField(generatedProjectHome, this.springCloudStreamAppMojo,
				this.projectHome.getRoot().getAbsolutePath());
		Field appPropertiesField = this.mojoClazz.getDeclaredField("additionalAppProperties");
		appPropertiesField.setAccessible(true);
		List<String> appProperties = new ArrayList<>();
		appProperties.add(ENTRY_ONE);
		appProperties.add(ENTRY_TWO);
		ReflectionUtils.setField(appPropertiesField, this.springCloudStreamAppMojo, appProperties);

		this.springCloudStreamAppMojo.execute();

		this.appPropertyValues.add(ENTRY_TWO);
		validateApplicationProperties(6);
	}


	private void validateApplicationProperties(int expectedCount) throws Exception {
		Stream<Path> pathStream =
				Files.find(this.projectHome.getRoot().toPath(), 3,
						(path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

		Path path = pathStream.findFirst().get();
		File appPropsFile = new File(path.toFile(), "/src/main/resources/application.properties");

		BufferedReader br = new BufferedReader(new FileReader(appPropsFile));
		int propCount = 0;
		while (br.ready()) {
			String line = br.readLine();
			assertThat("The " + line + " is not contained!", this.appPropertyValues.contains(line), is(true));
			propCount++;
		}
		assertThat(propCount, equalTo(expectedCount));
		br.close();
	}

	private void assertGeneratedPomXml(Path path) throws Exception {
		File pomXml = new File(path.toFile(), "pom.xml");

		InputStream is = new FileInputStream(pomXml);
		final MavenXpp3Reader reader = new MavenXpp3Reader();

		Model pomModel;
		try {
			pomModel = reader.read(is);
		}
		catch (IOException | XmlPullParserException e) {
			throw new IllegalStateException(e);
		}

		List<org.apache.maven.model.Dependency> dependencies = pomModel.getDependencies();
		assertThat(dependencies.size(), equalTo(3));
		assertThat(dependencies.stream()
				.filter(d -> d.getArtifactId().equals("spring-cloud-starter-stream-source-foo")).count(), equalTo(1L));

		assertThat(dependencies.stream()
				.filter(d -> d.getArtifactId().equals("spring-cloud-starter-stream-kafka")).count(), equalTo(1L));

		Parent parent = pomModel.getParent();
		assertThat(parent.getArtifactId(), equalTo("spring-boot-starter-parent"));
		assertThat(parent.getVersion(), equalTo("1.3.5.RELEASE"));

		assertThat(pomModel.getArtifactId(), equalTo("foo-source-kafka"));
		assertThat(pomModel.getGroupId(), equalTo("org.springframework.cloud.stream.app"));
		assertThat(pomModel.getName(), equalTo("foo-source-kafka"));
		assertThat(pomModel.getVersion(), equalTo("1.0.0.BUILD-SNAPSHOT"));
		assertThat(pomModel.getDescription(), equalTo("Spring Cloud Stream Foo Source Kafka Binder Application"));

		List<Plugin> plugins = pomModel.getBuild().getPlugins();

		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("spring-boot-maven-plugin"))
				.count(), equalTo(1L));

		assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("docker-maven-plugin")).count(), equalTo(1L));

		DependencyManagement dependencyManagement = pomModel.getDependencyManagement();
		List<org.apache.maven.model.Dependency> dependencies1 = dependencyManagement.getDependencies();
		assertThat(dependencies1.stream().filter(d -> d.getArtifactId().equals("spring-cloud-stream-app-dependencies"))
						.count(),
				equalTo(1L));

		assertThat(pomModel.getRepositories().size(), equalTo(2));

		is.close();
	}

}

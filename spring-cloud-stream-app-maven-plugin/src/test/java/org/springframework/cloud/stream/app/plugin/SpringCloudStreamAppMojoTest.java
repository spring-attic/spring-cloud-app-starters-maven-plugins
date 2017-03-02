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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.util.ReflectionUtils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Soby Chacko
 */
public class SpringCloudStreamAppMojoTest {

    private SpringCloudStreamAppMojo springCloudStreamAppMojo = new SpringCloudStreamAppMojo();

    private Class<? extends SpringCloudStreamAppMojo> mojoClazz = springCloudStreamAppMojo.getClass();

    @Before
    public void setup() throws Exception {

        Field applicationType = mojoClazz.getDeclaredField("applicationType");
        applicationType.setAccessible(true);
        ReflectionUtils.setField(applicationType, springCloudStreamAppMojo, "stream");

        Field bootVersion = mojoClazz.getDeclaredField("bootVersion");
        bootVersion.setAccessible(true);
        ReflectionUtils.setField(bootVersion, springCloudStreamAppMojo, "1.3.5.RELEASE");

        Field generatedProjectVersion = mojoClazz.getDeclaredField("generatedProjectVersion");
        generatedProjectVersion.setAccessible(true);
        ReflectionUtils.setField(generatedProjectVersion, springCloudStreamAppMojo, "1.0.0.BUILD-SNAPSHOT");

        Field binders = mojoClazz.getDeclaredField("binders");
        binders.setAccessible(true);
        Map<String, String> binders1 = new HashMap<>();
        binders1.put("kafka", null);
        ReflectionUtils.setField(binders, springCloudStreamAppMojo, binders1);

        Field generatedApps = mojoClazz.getDeclaredField("generatedApps");
        generatedApps.setAccessible(true);
        Map<String, GeneratableApp> generatableApps = new HashMap<>();
        generatableApps.put("foo-source", new GeneratableApp());
        ReflectionUtils.setField(generatedApps, springCloudStreamAppMojo, generatableApps);

        Bom bom = new Bom();
        bom.setArtifactId("spring-cloud-stream-app-dependencies");
        bom.setGroupId("org.springframework.cloud.stream.app");
        bom.setVersion("1.0.0.BUILD-SNAPSHOT");
        bom.setName("scs-bom");

        Field bomField = mojoClazz.getDeclaredField("bom");
        bomField.setAccessible(true);
        ReflectionUtils.setField(bomField, springCloudStreamAppMojo, bom);
    }

    @Test
    @Ignore("Need to find out how to retrieve the default project home")
    public void testDefaultProjectCreationByPlugin() throws Exception {
        String tmpdir = System.getProperty("java.io.tmpdir");

        springCloudStreamAppMojo.execute();

        Stream<Path> pathStream =
                Files.find(Paths.get(tmpdir), 3, (path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

        Path path = pathStream.findFirst().get();
        System.out.println(path);
        assertNotNull(path);

        assertGeneratedPomXml(path);
    }

    @Test
    public void testProjectCreatedIntoGeneratedProjectHome() throws Exception {
        String projectHome = "./target/apps";
        Field generatedProjectHome = mojoClazz.getDeclaredField("generatedProjectHome");
        generatedProjectHome.setAccessible(true);
        ReflectionUtils.setField(generatedProjectHome, springCloudStreamAppMojo, projectHome);

        springCloudStreamAppMojo.execute();

        Stream<Path> pathStream =
                Files.find(Paths.get(projectHome), 3, (path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

        Path path = pathStream.findFirst().get();
        System.out.println(path);
        assertNotNull(path);

        assertGeneratedPomXml(path);

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

        assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("spring-boot-maven-plugin")).count(), equalTo(1L));

        assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("docker-maven-plugin")).count(), equalTo(1L));

        DependencyManagement dependencyManagement = pomModel.getDependencyManagement();
        List<org.apache.maven.model.Dependency> dependencies1 = dependencyManagement.getDependencies();
        assertThat(dependencies1.stream().filter(d -> d.getArtifactId().equals("spring-cloud-stream-app-dependencies")).count(),
                equalTo(1L));

        assertThat(pomModel.getRepositories().size(), equalTo(2));

        is.close();
    }
}

/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.stream.app.documentation.plugin;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import org.springframework.boot.configurationprocessor.metadata.ConfigurationMetadata;
import org.springframework.boot.configurationprocessor.metadata.JsonMarshaller;

/**
 * A maven plugin that will gather all Spring Boot metadata files from all transitive dependencies and will aggregate
 * them in one metadata-only artifact.
 *
 * @author Eric Bottard
 */
@Mojo(
	name = "aggregate-metadata",
	requiresDependencyResolution = ResolutionScope.RUNTIME,
	defaultPhase = LifecyclePhase.COMPILE
)
public class MetadataAggregationMojo extends AbstractMojo {

	static final String METADATA_PATH = "META-INF/spring-configuration-metadata.json";
	static final String WHITELIST_PATH = "META-INF/spring-configuration-metadata-whitelist.properties";

	@Parameter(defaultValue = "${project}")
	private MavenProject mavenProject;

	@Parameter(defaultValue = "metadata")
	private String classifier;

	@Component
	private MavenProjectHelper projectHelper;

	private final JsonMarshaller jsonMarshaller = new JsonMarshaller();

	public void execute() throws MojoExecutionException {
		Result result = gatherMetadata();

		produceArtifact(result);
	}

	/**
	 * A tuple holding both configuration metadata and the whitelist properties.
	 *
	 * @author Eric Bottard
	 */
	/*default*/ static class Result {
		private final ConfigurationMetadata metadata;

		private final Properties whitelist;

		private Result(ConfigurationMetadata metadata, Properties whitelist) {
			this.metadata = metadata;
			this.whitelist = whitelist;
		}
	}

	/**
	 * Read all existing metadata from this project runtime dependencies and merge them in a single object.
	 */
	/*default*/ Result gatherMetadata() throws MojoExecutionException {
		ConfigurationMetadata metadata = new ConfigurationMetadata();
		Properties whiteList = new Properties();
		try {
			for (String path : mavenProject.getRuntimeClasspathElements()) {
				File file = new File(path);
				if (file.isDirectory()) {
					File localMetadata = new File(file, METADATA_PATH);
					if (localMetadata.canRead()) {
						try (InputStream is = new FileInputStream(localMetadata)) {
							ConfigurationMetadata depMetadata = jsonMarshaller.read(is);
							getLog().debug("Merging metadata from " + path);
							metadata.merge(depMetadata);
						}
					}
					File localWhiteList = new File(file, WHITELIST_PATH);
					if (localWhiteList.canRead()) {
						try (InputStream is = new FileInputStream(localWhiteList)) {
							getLog().debug("Merging whitelist from " + path);
							whiteList.load(is);
						}
					}
				}
				else {
					try (ZipFile zipFile = new ZipFile(file)) {
						ZipEntry entry = zipFile.getEntry(METADATA_PATH);
						if (entry != null) {
							try (InputStream inputStream = zipFile.getInputStream(entry)) {
								ConfigurationMetadata depMetadata = jsonMarshaller.read(inputStream);
								getLog().debug("Merging metadata from " + path);
								metadata.merge(depMetadata);
							}
						}
						entry = zipFile.getEntry(WHITELIST_PATH);
						if (entry != null) {
							try (InputStream inputStream = zipFile.getInputStream(entry)) {
								getLog().debug("Merging whitelist from " + path);
								whiteList.load(inputStream);
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			throw new MojoExecutionException("Exception trying to read metadata from dependencies of project", e);
		}
		return new Result(metadata, whiteList);
	}

	/**
	 * Create a jar file with the given metadata and "attach" it to the current maven project.
	 */
	/*default*/ void produceArtifact(Result result) throws MojoExecutionException {
		String artifactLocation = String.format("target/%s-%s-%s.jar", mavenProject.getArtifactId(), mavenProject.getVersion(), classifier);
		File output = new File(mavenProject.getBasedir(), artifactLocation);
		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(output))) {
			ZipEntry entry = new ZipEntry(METADATA_PATH);
			jos.putNextEntry(entry);
			jsonMarshaller.write(result.metadata, jos);

			entry = new ZipEntry(WHITELIST_PATH);
			jos.putNextEntry(entry);
			result.whitelist.store(jos, "Describes whitelisted properties for this app");
			getLog().info(String.format("Attaching %s to current project", output.getCanonicalPath()));
			projectHelper.attachArtifact(mavenProject, output, classifier);
		}
		catch (IOException e) {
			throw new MojoExecutionException("Error writing to file", e);
		}
	}

}

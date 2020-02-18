/*
 * Copyright 2017-2020 the original author or authors.
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


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.boot.configurationprocessor.metadata.ItemHint;
import org.springframework.boot.configurationprocessor.metadata.ItemMetadata;
import org.springframework.boot.configurationprocessor.metadata.JsonMarshaller;
import org.springframework.util.Base64Utils;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.boot.configurationprocessor.metadata.ItemHint.ValueHint;
import static org.springframework.boot.configurationprocessor.metadata.ItemHint.ValueProvider;

/**
 * A maven plugin that will gather all Spring Boot metadata files from all transitive dependencies and will aggregate
 * them in one metadata-only artifact.
 *
 * @author Eric Bottard
 * @author David Turanski
 * @author Christian Tzolov
 */
@Mojo(
		name = "aggregate-metadata",
		requiresDependencyResolution = ResolutionScope.RUNTIME,
		defaultPhase = LifecyclePhase.COMPILE
)
public class MetadataAggregationMojo extends AbstractMojo {

	static final String METADATA_PATH = "META-INF/spring-configuration-metadata.json";
	static final String WHITELIST_PATH = "META-INF/dataflow-configuration-metadata-whitelist.properties";
	static final String BACKUP_WHITELIST_PATH = "META-INF/spring-configuration-metadata-whitelist.properties";
	static final String CONFIGURATION_PROPERTIES_CLASSES = "configuration-properties.classes";
	static final String CONFIGURATION_PROPERTIES_NAMES = "configuration-properties.names";

	@Parameter(defaultValue = "${project}")
	private MavenProject mavenProject;

	@Parameter(defaultValue = "metadata")
	private String classifier;

	@Component
	private MavenProjectHelper projectHelper;

	@Parameter
	private boolean storeFilteredMetadata;

	@Parameter
	private MetadataFilter metadataFilter;

	private final JsonMarshaller jsonMarshaller = new JsonMarshaller();

	public static class MetadataFilter {
		private List<String> names;
		private List<String> sourceTypes;

		public List<String> getNames() {
			return names;
		}

		public void setNames(List<String> names) {
			this.names = names;
		}

		public List<String> getSourceTypes() {
			return sourceTypes;
		}

		public void setSourceTypes(List<String> sourceTypes) {
			this.sourceTypes = sourceTypes;
		}

		@Override
		public String toString() {
			return "MetadataFilter{" +
					"name=" + names +
					", sourceType=" + sourceTypes +
					'}';
		}
	}

	public void execute() throws MojoExecutionException {
		Result result = new Result(gatherConfigurationMetadata(null), gatherWhiteListMetadata());
		produceArtifact(result);

		if (storeFilteredMetadata) {
			getLog().debug("propertyClassFilter: " + metadataFilter);
			storeFilteredMetadata();
		}
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
	 *
	 * Store pre-filtered and base64 encoded data into a property file.
	 */
	private void storeFilteredMetadata() throws MojoExecutionException {
		File targetFolder = new File(mavenProject.getBuild().getOutputDirectory(), "META-INF");
		if (!targetFolder.exists()) {
			targetFolder.mkdir();
		}
		try (FileWriter fileWriter = new FileWriter(new File(targetFolder, "spring-configuration-metadata-encoded.properties"))) {
			ConfigurationMetadata metadata = gatherConfigurationMetadata(metadataFilter);
			byte[] metadataBase64Encoded = Base64Utils.encode(toByteArray(metadata));
			fileWriter.write("spring.configuration.metadata.encoded=" + new String(metadataBase64Encoded));
		}
		catch (IOException e) {
			throw new MojoExecutionException("Error creating file ", e);
		}

	}

	private byte[] toByteArray(ConfigurationMetadata metadata) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		jsonMarshaller.write(metadata, baos);
		return baos.toByteArray();
	}

	/**
	 * Read all existing metadata from this project runtime dependencies and merge them in a single object.
	 */
	/*default*/ Properties gatherWhiteListMetadata() throws MojoExecutionException {
		Properties whiteList = new Properties();
		try {
			for (String path : mavenProject.getRuntimeClasspathElements()) {
				File file = new File(path);
				if (file.isDirectory()) {
					File localWhiteList = new File(file, WHITELIST_PATH);
					if (localWhiteList.canRead()) {
						whiteList = getWhitelistFromFile(whiteList, path, localWhiteList);
					}
					else {
						File backupWhitelist = new File(file, BACKUP_WHITELIST_PATH);
						if (backupWhitelist.canRead()) {
							whiteList = getWhitelistFromFile(whiteList, path, backupWhitelist);
						}
					}
				}
				else {
					try (ZipFile zipFile = new ZipFile(file)) {

						ZipEntry entry = zipFile.getEntry(WHITELIST_PATH);
						if (entry != null) {
							whiteList = getWhitelistFromZipFile(whiteList, path, zipFile, entry);
						}
						else {
							entry = zipFile.getEntry(BACKUP_WHITELIST_PATH);
							if (entry != null) {
								whiteList = getWhitelistFromZipFile(whiteList, path, zipFile, entry);
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			throw new MojoExecutionException("Exception trying to read metadata from dependencies of project", e);
		}
		return whiteList;
	}

	/*default*/ ConfigurationMetadata gatherConfigurationMetadata(MetadataFilter metadataFilters) throws MojoExecutionException {
		ConfigurationMetadata metadata = new ConfigurationMetadata();
		try {
			for (String path : mavenProject.getRuntimeClasspathElements()) {
				File file = new File(path);
				if (file.isDirectory()) {
					File localMetadata = new File(file, METADATA_PATH);
					if (localMetadata.canRead()) {
						try (InputStream is = new FileInputStream(localMetadata)) {
							ConfigurationMetadata depMetadata = jsonMarshaller.read(is);
							depMetadata = filterMetadata(depMetadata, metadataFilters);
							getLog().debug("Merging metadata from " + path);
							addEnumHints(depMetadata, getClassLoader(path));
							metadata.merge(depMetadata);
						}
					}
				}
				else {
					try (ZipFile zipFile = new ZipFile(file)) {
						ZipEntry entry = zipFile.getEntry(METADATA_PATH);
						if (entry != null) {
							try (InputStream inputStream = zipFile.getInputStream(entry)) {
								ConfigurationMetadata depMetadata = jsonMarshaller.read(inputStream);
								depMetadata = filterMetadata(depMetadata, metadataFilters);
								getLog().debug("Merging metadata from " + path);
								addEnumHints(depMetadata, getClassLoader(path));
								metadata.merge(depMetadata);
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			throw new MojoExecutionException("Exception trying to read metadata from dependencies of project", e);
		}
		return metadata;
	}

	@SuppressWarnings("unchecked")
	private ConfigurationMetadata filterMetadata(ConfigurationMetadata metadata, MetadataFilter metadataFilters) {
		if (metadataFilters == null
				|| (CollectionUtils.isEmpty(metadataFilters.getNames()) && CollectionUtils.isEmpty(metadataFilters.getSourceTypes()))) {
			return metadata; // nothing to filter by so take all;
		}

		List<String> sourceTypeFilters = CollectionUtils.isEmpty(metadataFilters.getSourceTypes()) ?
				Collections.EMPTY_LIST : metadataFilters.getSourceTypes();

		List<String> nameFilters = CollectionUtils.isEmpty(metadataFilters.getNames()) ?
				Collections.EMPTY_LIST : metadataFilters.getNames();

		ConfigurationMetadata filteredMetadata = new ConfigurationMetadata();
		List<String> whitelistedNames = new ArrayList<>();
		for (ItemMetadata itemMetadata : metadata.getItems()) {
			String metadataName = itemMetadata.getName();
			String metadataSourceType = itemMetadata.getSourceType();
			if (StringUtils.hasText(metadataSourceType) && sourceTypeFilters.contains(metadataSourceType.trim())) {
				filteredMetadata.add(itemMetadata);
				whitelistedNames.add(itemMetadata.getName());
			}
			if (StringUtils.hasText(metadataName) && nameFilters.contains(metadataName.trim())) {
				filteredMetadata.add(itemMetadata);
				whitelistedNames.add(itemMetadata.getName());
			}

		}

		// copy the hits only for the whitelisted metadata.
		for (ItemHint itemHint : metadata.getHints()) {
			if (itemHint != null && whitelistedNames.contains(itemHint.getName())) {
				filteredMetadata.add(itemHint);
			}
		}

		return filteredMetadata;
	}

	private Properties getWhitelistFromZipFile(Properties whiteList, String path, ZipFile zipFile, ZipEntry entry) throws IOException {
		try (InputStream inputStream = zipFile.getInputStream(entry)) {
			getLog().debug("Merging whitelist from " + path);
			whiteList = merge(whiteList, inputStream);
		}
		return whiteList;
	}

	private Properties getWhitelistFromFile(Properties whiteList, String path, File localWhiteList) throws IOException {
		try (InputStream is = new FileInputStream(localWhiteList)) {
			getLog().debug("!!!! Merging whitelist from " + path);
			whiteList = merge(whiteList, is);
		}
		return whiteList;
	}

	Properties merge(Properties whitelist, InputStream is) throws IOException {
		Properties mergedProperties = new Properties();
		mergedProperties.load(is);

		if (!mergedProperties.containsKey(CONFIGURATION_PROPERTIES_CLASSES) && !mergedProperties.containsKey(CONFIGURATION_PROPERTIES_NAMES)) {
			getLog().warn(String.format("Whitelist properties does not contain any required keys: %s",
					StringUtils.arrayToCommaDelimitedString(new String[] { CONFIGURATION_PROPERTIES_CLASSES,
							CONFIGURATION_PROPERTIES_NAMES })));
			return whitelist;
		}

		if (!CollectionUtils.isEmpty(whitelist)) {
			mergeCommaDelimitedValue(whitelist, mergedProperties, CONFIGURATION_PROPERTIES_CLASSES);
			mergeCommaDelimitedValue(whitelist, mergedProperties, CONFIGURATION_PROPERTIES_NAMES);
		}

		return mergedProperties;
	}

	private void mergeCommaDelimitedValue(Properties currentProperties, Properties newProperties, String key) {
		if (currentProperties.containsKey(key) || newProperties.containsKey(key)) {
			Collection<String> values = StringUtils.commaDelimitedListToSet(currentProperties.getProperty(key));
			values.addAll(StringUtils.commaDelimitedListToSet(newProperties.getProperty(key)));
			if (newProperties.containsKey(key)) {
				getLog().debug(String.format("Merging whitelist property %s=%s", key, newProperties.getProperty(key)));
			}
			newProperties.setProperty(key, StringUtils.collectionToCommaDelimitedString(values));

		}
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

			entry = new ZipEntry(BACKUP_WHITELIST_PATH);
			jos.putNextEntry(entry);
			result.whitelist.store(jos, "Describes whitelisted properties for this app");

			getLog().info(String.format("Attaching %s to current project", output.getCanonicalPath()));
			projectHelper.attachArtifact(mavenProject, output, classifier);
		}
		catch (IOException e) {
			throw new MojoExecutionException("Error writing to file", e);
		}
	}


	void addEnumHints(ConfigurationMetadata configurationMetadata, ClassLoader classLoader) {

		Map<String, List<ValueProvider>> providers = new HashMap<>();

		Map<String, ItemHint> itemHints = new HashMap<>();

		for (ItemMetadata property : configurationMetadata.getItems()) {

			if (property.isOfItemType(ItemMetadata.ItemType.PROPERTY)) {

				if (ClassUtils.isPresent(property.getType(), classLoader)) {
					Class<?> clazz = ClassUtils.resolveClassName(property.getType(), classLoader);
					if (clazz.isEnum()) {
						List<ValueHint> valueHints = new ArrayList<>();
						for (Object o : clazz.getEnumConstants()) {
							valueHints.add(new ValueHint(o, null));
						}

						if (!providers.containsKey(property.getType())) {
							providers.put(property.getType(), new ArrayList<ValueProvider>());
						}

						//Equals is not correct for ValueProvider

						boolean found = false;
						for (ValueProvider valueProvider : providers.get(property.getType())) {
							if (valueProvider.getName().equals(property.getType())) {
								found = true;
							}
						}

						if (!found) {
							providers.get(property.getType()).add(new ValueProvider(property.getType(), null));
						}

						itemHints.put(property.getType(), new ItemHint(property.getName(), valueHints,
								new ArrayList<>(providers.get(property.getType()))));

					}
				}
			}
		}
		if (!CollectionUtils.isEmpty(itemHints)) {
			for (ItemHint itemHint : itemHints.values())
				configurationMetadata.add(itemHint);
		}
	}

	private ClassLoader getClassLoader(String jarPath) {
		ClassLoader classLoader = null;
		try {
			classLoader = new URLClassLoader(new URL[] { new URL("file://" + jarPath) },
					this.getClass().getClassLoader());
		}
		catch (MalformedURLException e) {
			// pass through
		}
		return classLoader;
	}
}

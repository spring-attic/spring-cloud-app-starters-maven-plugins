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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.spring.initializr.generator.ProjectRequest;
import io.spring.initializr.metadata.Dependency;
import io.spring.initializr.metadata.InitializrMetadata;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import org.springframework.cloud.stream.app.plugin.utils.MavenModelUtils;
import org.springframework.cloud.stream.app.plugin.utils.SpringCloudStreamPluginUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;

import static java.util.stream.Collectors.toList;

/**
 * @author Soby Chacko
 */
@Mojo(name = "generate-app")
public class SpringCloudStreamAppMojo extends AbstractMojo {

	private static final String SPRING_CLOUD_STREAM_BINDER_GROUP_ID = "org.springframework.cloud";

	@Parameter(defaultValue="${project}", readonly=true, required=true)
	private MavenProject project;

	@Parameter
	private Map<String, GeneratableApp> generatedApps;

	@Parameter
	private Bom bom;

	@Parameter
	private String generatedProjectHome;

	@Parameter
	private String javaVersion;

	@Parameter
	private String bootVersion;

	@Parameter
	private String generatedProjectVersion;

	@Parameter
	private String applicationType;

	@Parameter
	private List<Repository> extraRepositories;

	@Parameter
	List<Dependency> additionalGlobalDependencies = new ArrayList<>();

	@Parameter
	private Map<String, BinderMetadata> binders = new HashMap<>();

	@Parameter
	private String bomsWithHigherPrecedence;

	@Parameter
	private List<Bom> additionalBoms;

	@Parameter
	List<Plugin> additionalPlugins = new ArrayList<>();

	private ScsProjectGenerator projectGenerator = new ScsProjectGenerator();

	public void execute() throws MojoExecutionException, MojoFailureException {

		projectGenerator.setDockerHubOrg("springcloud" + applicationType);
		projectGenerator.setBomsWithHigherPrecedence(bomsWithHigherPrecedence);
		projectGenerator.setAdditionalBoms(additionalBoms);
		projectGenerator.setAdditionalPlugins(additionalPlugins);
		if (project != null) {
			@SuppressWarnings("unchecked")
			List<MavenProject> collectedProjects = project.getParent().getCollectedProjects();

			Optional<MavenProject> dependencies = collectedProjects.stream()
					.filter(e -> e.getArtifactId().endsWith("dependencies")).findFirst();
			MavenProject mavenProject = dependencies.get();
			Properties  properties = new Properties();

			mavenProject.getProperties().keySet()
					.stream()
					.filter(p -> !mavenProject.getParent().getProperties().containsKey(p))
					.forEach(p -> properties.put(p, mavenProject.getProperties().get(p)));

			projectGenerator.setProperties(properties);
		}

		final InitializrDelegate initializrDelegate = new InitializrDelegate();

		initializrDelegate.prepareProjectGenerator();

		List<String> appTypes = getAppTypes();
		try {
			for (Map.Entry<String, GeneratableApp> entry : generatedApps.entrySet()) {
				for (String appType : appTypes) {
					generateApp(initializrDelegate, entry, appType);
				}
			}
		}
		catch (IOException | XmlPullParserException e) {
			throw new IllegalStateException(e);
		}
	}

	private void generateApp(InitializrDelegate initializrDelegate,
							 Map.Entry<String, GeneratableApp> entry, String appType) throws IOException, XmlPullParserException {

		GeneratableApp value = entry.getValue();
		final String generatedAppGroupId = getApplicationGroupId(applicationType);

		List<Dependency> deps = new ArrayList<>();
		List<String> artifactIds = new ArrayList<>();

		String appArtifactId = !appType.equals("default") ? entry.getKey() + "-" + appType : entry.getKey();
		String starterArtifactId = getStarterArtifactId(entry.getKey());
		Dependency starterDep = getDependency(starterArtifactId, generatedAppGroupId);
		deps.add(starterDep);
		artifactIds.add(starterArtifactId);
		if (!appType.equals("default")) {
			String binderArtifactId = constructBinderArtifact(appType);
			Dependency binderDep = getDependency(binderArtifactId, SPRING_CLOUD_STREAM_BINDER_GROUP_ID);
			deps.add(binderDep);
			artifactIds.add(binderArtifactId);
		}
		if (StringUtils.isNotEmpty(value.getExtraTestConfigClass())) {
			deps.add(getDependency("app-starters-test-support", generatedAppGroupId));
			artifactIds.add("app-starters-test-support");

			if (!value.isNoAppSpecificTestSupportArtifact()) {
				String[] tokens = entry.getKey().split("-");
				String starterType = Stream.of(tokens)
						.limit(tokens.length - 1)
						.collect(Collectors.joining("-"));

				deps.add(getDependency(starterType + "-" + "app-starters-test-support", generatedAppGroupId));
				artifactIds.add(starterType + "-" + "app-starters-test-support");
			}
		}

		for (Dependency globalDep : additionalGlobalDependencies) {
			Dependency dep = getDependency(globalDep.getArtifactId(),
					globalDep.getGroupId());
			deps.add(dep);
			artifactIds.add(dep.getArtifactId());
		}

		//Force a dependency independent of BOM
		for (Dependency forceDep : value.getForceDependencies()) {
			Dependency dependency = new Dependency();
			dependency.setId(forceDep.getArtifactId());
			dependency.setGroupId(forceDep.getGroupId());
			dependency.setArtifactId(forceDep.getArtifactId());
			if (StringUtils.isNotEmpty(forceDep.getVersion())) {
				dependency.setVersion(forceDep.getVersion());
			}
			else {
				dependency.setBom(bom.getName());
			}
			deps.add(dependency);

			artifactIds.add(dependency.getArtifactId());
		}

		BinderMetadata binderMetadata = binders.get(appType);
		if (binderMetadata != null && !binderMetadata.getForceDependencies().isEmpty()) {
			for (Dependency forceDep : binderMetadata.getForceDependencies()) {
				Dependency dependency = new Dependency();
				dependency.setId(forceDep.getArtifactId());
				dependency.setGroupId(forceDep.getGroupId());
				dependency.setArtifactId(forceDep.getArtifactId());
				if (StringUtils.isNotEmpty(forceDep.getVersion())) {
					dependency.setVersion(forceDep.getVersion());
				}
				else {
					dependency.setBom(bom.getName());
				}
				deps.add(dependency);

				artifactIds.add(dependency.getArtifactId());
			}
		}

		Dependency[] depArray = deps.toArray(new Dependency[deps.size()]);
		String[] artifactNames = artifactIds.toArray(new String[artifactIds.size()]);
		List<Repository> extraReposToAdd = new ArrayList<>();
		Set<String> extraRepoIds = value.getExtraRepositories().keySet();
		if (!CollectionUtils.isEmpty(extraRepositories) && !CollectionUtils.isEmpty(extraRepoIds)) {
			extraReposToAdd = extraRepositories.stream().filter(e -> extraRepoIds.contains(e.getId()))
					.collect(Collectors.toList());
		}
		String[] repoIds = new String[]{};
		if (!CollectionUtils.isEmpty(extraRepoIds)) {
			repoIds = new String[extraRepoIds.size()];
			repoIds = extraRepoIds.toArray(repoIds);
		}
		InitializrMetadata metadata = SpringCloudStreamAppMetadataBuilder.withDefaults()
				.addRepositories(extraReposToAdd)
				.addBom(bom.getName(), bom.getGroupId(), bom.getArtifactId(), bom.getVersion(), repoIds)
				.addJavaVersion(javaVersion)
				.addBootVersion(bootVersion)
				.addDependencyGroup(appArtifactId, depArray).build();
		initializrDelegate.applyMetadata(metadata);
		ProjectRequest projectRequest = initializrDelegate.getProjectRequest(appArtifactId, getApplicationGroupId(applicationType),
				getDescription(appArtifactId), getPackageName(appArtifactId),
				generatedProjectVersion, artifactNames);
		File project = projectGenerator.doGenerateProjectStructure(projectRequest);

		File generatedProjectHome = StringUtils.isNotEmpty(this.generatedProjectHome) ?
				new File(this.generatedProjectHome) :
				StringUtils.isNotEmpty(value.getGeneratedProjectHome()) ? new File(value.generatedProjectHome) :
						null;

		postProcessGeneratedProject(value, appArtifactId, project, generatedProjectHome, entry.getKey(), generatedProjectVersion);
	}

	private List<String> getAppTypes() {
		List<String> appTypes = new ArrayList<>();

		if (!CollectionUtils.isEmpty(binders)) {
			appTypes.addAll(binders.keySet());
		}
		else {
			appTypes.add("default");
		}
		return appTypes;
	}

	private void postProcessGeneratedProject(GeneratableApp value, String appArtifactId, File project,
											 File generatedProjectHome, String origKey, String generatedProjectVersion) throws IOException, XmlPullParserException {
		if (generatedProjectHome != null && project != null) {
			String generatedAppHome = moveProjectWithMavenModelsUpdated(appArtifactId, project, generatedProjectHome,
					value.isTestsIgnored(), generatedProjectVersion);

			try {
				String[] tokens = appArtifactId.split("-");
				List<String> orderedStarterArtifactTokens = new LinkedList<>();
				orderedStarterArtifactTokens.addAll(Stream.of(tokens)
						.limit(tokens.length - 1)
						.collect(toList()));

				final File applicationProperties = new File(generatedAppHome, "src/main/resources/application.properties");

				String applicationPropertiesContents = "info.app.name=" + "@project.artifactId@" + "\n" +
						"info.app.description=" + "@project.description@" + "\n" +
						"info.app.version=" + "@project.version@" + "\n";

				Files.write(applicationProperties.toPath(), applicationPropertiesContents.getBytes());
			}
			catch (IOException e) {
				throw new IllegalStateException(e);
			}

			if (StringUtils.isNotEmpty(value.getExtraTestConfigClass())) {
				String s = StringUtils.removeAndHump(appArtifactId, "-");
				String s1 = StringUtils.capitalizeFirstLetter(s);
				String clazzInfo = "classes = {\n" +
						"\t\t" + value.getExtraTestConfigClass() + ",\n" +
						"\t\t" + s1 + "Application.class" + " }";
				SpringCloudStreamPluginUtils.addExtraTestConfig(generatedAppHome, clazzInfo);
			}
			String s = StringUtils.removeAndHump(origKey, "-");
			String s1 = StringUtils.capitalizeFirstLetter(s);

			String[] tokens = origKey.split("-");
			List<String> orderedStarterArtifactTokens = new LinkedList<>();
			int toLimit = applicationType.equals("task") ? tokens.length - 1 : tokens.length;
			orderedStarterArtifactTokens.addAll(Stream.of(tokens)
					.limit(toLimit)
					.collect(toList()));
			String subPackage = orderedStarterArtifactTokens.stream().collect(Collectors.joining("."));

			String toBeImported = StringUtils.isEmpty(value.getAutoConfigClass()) ?
					String.format("%s.%s.%s.%s.%sConfiguration.class", "org.springframework.cloud", applicationType, "app", subPackage, s1)
					: value.getAutoConfigClass();
			SpringCloudStreamPluginUtils.addAutoConfigImport(generatedAppHome, toBeImported);
			addCopyrightToJavaFiles(generatedAppHome);
		}
		else if (project != null) {
			//no user provided generated project home, fall back to the default used by the Initializr
			getLog().info("Project is generated at " + project.toString());
		}
	}

	private void addCopyrightToJavaFiles(String generatedAppHome) throws IOException {
		Stream<Path> javaStream =
				Files.find(Paths.get(generatedAppHome), 20,
						(path, attr) -> String.valueOf(path).endsWith(".java"));

		javaStream.forEach(p -> {
			try {
				SpringCloudStreamPluginUtils.addCopyRight(p);
			}
			catch (IOException e) {
				getLog().warn("Issues adding copyright", e);
			}
		});
	}

	private String getStarterArtifactId(String key) {
		//time-source -> source-time
		//groovy-filter-processor -> processor-groovy-filter
		//scriptable-transform-processor -> processor-scriptable-transform
		//timestamp-task -> task-timestamp

		String[] tokens = key.split("-");
		List<String> orderedStarterArtifactTokens = new LinkedList<>();
		if (!applicationType.equals("task")) {
			orderedStarterArtifactTokens.add(tokens[tokens.length - 1]);
		}
		orderedStarterArtifactTokens.addAll(Stream.of(tokens)
				.limit(tokens.length - 1)
				.collect(toList()));
		String collect = orderedStarterArtifactTokens.stream().collect(Collectors.joining("-"));
		return String.format("%s-%s-%s", "spring-cloud-starter", applicationType, collect);
	}

	private static String getApplicationGroupId(String applicationType) {
		return String.format("%s.%s.%s", "org.springframework.cloud", applicationType, "app");
	}

	private String getPackageName(String artifactId) {
		String[] strings = applicationType.equals("stream") ? Stream.of(artifactId.split("-"))
				.toArray(String[]::new) :
				Stream.of(artifactId.split("-"))
						.limit(StringUtils.countMatches(artifactId, "-"))
						.toArray(String[]::new);

		//handle possible numeric versions at the end of the package name
		if (StringUtils.isNumeric(strings[strings.length - 1])) {
			Object[] versionDropped = Stream.of(strings).limit(strings.length - 1)
					.toArray();
			String join = StringUtils.join(versionDropped, ".");
			return String.format("%s.%s", getApplicationGroupId(applicationType), join);
		}


		String join = StringUtils.join(strings, ".");
		return String.format("%s.%s", getApplicationGroupId(applicationType), join);
	}

	private String getDescription(String artifactId) {
		String[] strings = Stream.of(artifactId.split("-"))
				.map(StringUtils::capitalizeFirstLetter)
				.toArray(String[]::new);
		String join = StringUtils.join(strings, " ");
		String appSuffix = applicationType.equals("stream") ? "Binder Application" : "Application";
		return String.format("%s %s %s %s", "Spring Cloud", StringUtils.capitalizeFirstLetter(applicationType),
				join, appSuffix);
	}

	private Dependency getDependency(String s, String groupId) {
		Dependency dependency = new Dependency();
		dependency.setId(s);
		dependency.setGroupId(groupId);
		dependency.setArtifactId(s);
		dependency.setBom(bom.getName());
		return dependency;
	}

	private String constructBinderArtifact(String binder) {
		if (binder.contains("-")){
			binder = org.springframework.util.StringUtils.split(binder, "-")[0];
		}
		return String.format("%s-%s", "spring-cloud-starter-stream", binder);
	}

	private String moveProjectWithMavenModelsUpdated(String key, File project,
													 File generatedProjectHome, boolean testIgnored,
													 String generatedProjectVersion) throws IOException, XmlPullParserException {

		Model model = isNewDir(generatedProjectHome) ? MavenModelUtils.populateModel(generatedProjectHome.getName(),
				getApplicationGroupId(applicationType), generatedProjectVersion)
				: MavenModelUtils.getModelFromContainerPom(generatedProjectHome, getApplicationGroupId(applicationType), generatedProjectVersion);

		if (model != null && MavenModelUtils.addModuleIntoModel(model, key)) {
			MavenModelUtils.writeModelToFile(model, new FileOutputStream(new File(generatedProjectHome, "pom.xml")));
		}

		try {
			File generatedAppHome = new File(generatedProjectHome, key);
			removeExistingContent(generatedAppHome.toPath());

			FileUtils.copyDirectory(new File(project, key), generatedAppHome);

			File mvnw = new File(generatedAppHome, "mvnw");
			if (mvnw.exists()) {
				mvnw.setExecutable(true);
			}

			if (testIgnored) {
				SpringCloudStreamPluginUtils.ignoreUnitTestGeneratedByInitializer(generatedAppHome.getAbsolutePath());
			}
			//MavenModelUtils.addModuleInfoToContainerPom(generatedProjectHome);
			return generatedAppHome.getAbsolutePath();
		}
		catch (IOException e) {
			getLog().error("Error during plugin execution", e);
			throw new IllegalStateException(e);
		}
	}

	private boolean isNewDir(File genProjecthome) {
		return (!genProjecthome.exists() && genProjecthome.mkdir());
	}

	private void removeExistingContent(Path path) {
		if (path.toFile().exists()) {
			try {
				SpringCloudStreamPluginUtils.cleanupGenProjHome(path.toFile());
			}
			catch (IOException e) {
				getLog().error("Error", e);
				throw new IllegalStateException(e);
			}
		}
	}

	private class InitializrDelegate {

		private void applyMetadata(final InitializrMetadata metadata) {
			projectGenerator.setMetadataProvider(() -> metadata);
		}

		private ProjectRequest getProjectRequest(String generatedArtifactId, String generatedAppGroupId,
												 String description, String packageName, String version,
												 String... artifactNames) {
			ProjectRequest projectRequest = createProjectRequest(artifactNames);
			projectRequest.setBaseDir(generatedArtifactId);

			projectRequest.setGroupId(generatedAppGroupId);
			projectRequest.setArtifactId(generatedArtifactId);
			projectRequest.setName(generatedArtifactId);
			projectRequest.setDescription(description);
			projectRequest.setPackageName(packageName);
			projectRequest.setVersion(version);
			return projectRequest;
		}

		private ProjectRequest createProjectRequest(String... styles) {
			ProjectRequest request = new ProjectRequest();
			request.initialize(projectGenerator.getMetadataProvider().get());
			request.getStyle().addAll(Arrays.asList(styles));
			return request;
		}

		private void prepareProjectGenerator() {
			String tmpdir = System.getProperty("java.io.tmpdir");
			projectGenerator.setTmpdir(tmpdir);
			projectGenerator.setEventPublisher(new ApplicationEventPublisher() {
				public void publishEvent(ApplicationEvent applicationEvent) {
					getLog().debug("Generated project : " + applicationEvent.toString());
				}

				public void publishEvent(Object o) {
					getLog().debug("Generated project : " + o.toString());
				}
			});
		}
	}
}

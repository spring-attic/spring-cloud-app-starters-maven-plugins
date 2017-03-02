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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.spring.initializr.metadata.BillOfMaterials;
import io.spring.initializr.metadata.DefaultMetadataElement;
import io.spring.initializr.metadata.DependencyGroup;
import io.spring.initializr.metadata.InitializrMetadata;
import io.spring.initializr.metadata.InitializrMetadataBuilder;

import org.springframework.util.StringUtils;

/**
 * @author Soby Chacko
 */
public class SpringCloudStreamAppMetadataBuilder {

    private final InitializrMetadataBuilder builder;

    public InitializrMetadata build() {
        return builder.build();
    }

    private SpringCloudStreamAppMetadataBuilder(){
        builder = InitializrMetadataBuilder.create();
    }

    public static SpringCloudStreamAppMetadataBuilder withDefaults() {
        return new SpringCloudStreamAppMetadataBuilder().addDefaults();
    }

    public SpringCloudStreamAppMetadataBuilder addDefaults() {
        return addDefaultPackagings().
            addDefaultLanguages();
    }

    private SpringCloudStreamAppMetadataBuilder addDefaultPackagings() {
        DefaultMetadataElement packaging = getMetadataElement("jar", true);
        builder.withCustomizer(initializerMetadata ->
                initializerMetadata.getPackagings().getContent().add(packaging));
        return this;
    }

    public SpringCloudStreamAppMetadataBuilder addBootVersion(String bootVersion) {
        if (StringUtils.isEmpty(bootVersion)) {
            bootVersion = "1.3.3.RELEASE";
        }
        DefaultMetadataElement bootVer = getMetadataElement(bootVersion, true);
        builder.withCustomizer(initializerMetadata ->
                initializerMetadata.getBootVersions().getContent().add(bootVer));
        return this;
    }

    public SpringCloudStreamAppMetadataBuilder addJavaVersion(String javaVersion) {
        if (StringUtils.isEmpty(javaVersion)){
            javaVersion = "1.8";
        }
        DefaultMetadataElement javaVer = getMetadataElement(javaVersion, true);
        builder.withCustomizer(initializerMetadata ->
                initializerMetadata.getJavaVersions().getContent().add(javaVer));
        return this;
    }

    private SpringCloudStreamAppMetadataBuilder addDefaultLanguages() {
        DefaultMetadataElement language = getMetadataElement("java", true);
        builder.withCustomizer(initializrMetadata ->
                initializrMetadata.getLanguages().getContent().add(language));
        return this;
    }

    private DefaultMetadataElement getMetadataElement(String id, boolean defaultValue) {
        DefaultMetadataElement metadataElement = new DefaultMetadataElement();
        metadataElement.setId(id);
        metadataElement.setName(id);
        metadataElement.setDefault(defaultValue);
        return metadataElement;
    }

    SpringCloudStreamAppMetadataBuilder addRepositories(List<Repository> repositories) throws MalformedURLException {

        for (Repository repository : repositories) {

            io.spring.initializr.metadata.Repository repo = new io.spring.initializr.metadata.Repository();
            repo.setName(repository.getName());
            repo.setUrl(new URL(repository.getUrl()));
            repo.setSnapshotsEnabled(repository.isSnapshotEnabled());

            builder.withCustomizer(initializrMetadata ->
                    initializrMetadata.getConfiguration().getEnv().getRepositories().put(repository.getId(), repo));
        }
        return this;
    }

    public SpringCloudStreamAppMetadataBuilder addBom(String id, String groupId, String artifactId, String version, String... repoIds) {
        BillOfMaterials billOfMaterials = new BillOfMaterials();
        billOfMaterials.setGroupId(groupId);
        billOfMaterials.setArtifactId(artifactId);
        billOfMaterials.setVersion(version);
        List<String> repos = new ArrayList<>();
        repos.add("spring-snapshots");
        repos.add("spring-milestones");
        for (String repoId : repoIds) {
            repos.add(repoId);
        }
        billOfMaterials.setRepositories(repos);
        return addBom(id, billOfMaterials);
    }

    private SpringCloudStreamAppMetadataBuilder addBom(final String id, final BillOfMaterials bom) {
        builder.withCustomizer(initializrMetadata ->
                initializrMetadata.getConfiguration().getEnv().getBoms().put(id, bom));
        return this;
    }

    public SpringCloudStreamAppMetadataBuilder addDependencyGroup(final String name, final io.spring.initializr.metadata.Dependency... dependencies) {
        builder.withCustomizer(initializrMetadata ->  {
            DependencyGroup group = new DependencyGroup();
            group.setName(name);
            group.getContent().addAll(Arrays.asList(dependencies));
            initializrMetadata.getDependencies().getContent().add(group);
        });
        return this;
    }
}

/*
 * Copyright 2015-2016 the original author or authors.
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

import io.spring.initializr.metadata.Dependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Soby Chacko
 */
public class GeneratableApp {

    private Map<String, String> extraRepositories = new HashMap<>();

    String groupId;

    String description;
    String packageName;
    String generatedProjectHome;

    boolean testsIgnored;

    String extraTestConfigClass;
    boolean noAppSpecificTestSupportArtifact;

    String autoConfigClass;

    List<Dependency> forceDependencies = new ArrayList<>();
    private String starterGroupId;
    private String starterArtifactId;

    public String getAutoConfigClass() {
        return autoConfigClass;
    }

    public void setAutoConfigClass(String autoConfigClass) {
        this.autoConfigClass = autoConfigClass;
    }

    public String getExtraTestConfigClass() {
        return extraTestConfigClass;
    }

    public void setExtraTestConfigClass(String extraTestConfigClass) {
        this.extraTestConfigClass = extraTestConfigClass;
    }

    public boolean isTestsIgnored() {
        return testsIgnored;
    }

    public void setTestsIgnored(boolean testsIgnored) {
        this.testsIgnored = testsIgnored;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    public Map<String, String> getExtraRepositories() {
        return extraRepositories;
    }

    public void setExtraRepositories(Map<String, String> extraRepositories) {
        this.extraRepositories = extraRepositories;
    }

    public List<Dependency> getForceDependencies() {
        return forceDependencies;
    }

    public void setForceDependencies(List<Dependency> forceDependencies) {
        this.forceDependencies = forceDependencies;
    }

    @Override
    public String toString() {
        return "GeneratableApp{" +
                "groupId='" + groupId + '\'' +
                ", description='" + description + '\'' +
                ", packageName='" + packageName + '\'' +
                '}';
    }

    public void setGeneratedProjectHome(String generatedProjectHome) {
        this.generatedProjectHome = generatedProjectHome;
    }

    public String getGeneratedProjectHome() {
        return generatedProjectHome;
    }

    public boolean isNoAppSpecificTestSupportArtifact() {
        return noAppSpecificTestSupportArtifact;
    }

    public void setNoAppSpecificTestSupportArtifact(boolean noAppSpecificTestSupportArtifact) {
        this.noAppSpecificTestSupportArtifact = noAppSpecificTestSupportArtifact;
    }

    public String getStarterGroupId() {
        return starterGroupId;
    }

    public void setStarterGroupId(String starterGroupId) {
        this.starterGroupId = starterGroupId;
    }

    public String getStarterArtifactId() {
        return starterArtifactId;
    }

    public void setStarterArtifactId(String starterArtifactId) {
        this.starterArtifactId = starterArtifactId;
    }
}

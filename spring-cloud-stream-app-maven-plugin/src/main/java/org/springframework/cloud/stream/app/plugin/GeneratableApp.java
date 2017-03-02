package org.springframework.cloud.stream.app.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.spring.initializr.metadata.Dependency;

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
}

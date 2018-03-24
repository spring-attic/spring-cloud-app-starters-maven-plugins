package org.springframework.cloud.stream.app.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    @Override
    protected File doGenerateProjectStructure(ProjectRequest request) {
        return doGenerateProjectStructure(request, MavenModelUtils.ENTRYPOINT_TYPE_EXEC);
    }

    protected File doGenerateProjectStructure(ProjectRequest request, String entrypointType) {
        final File rootDir = super.doGenerateProjectStructure(request);

        final File dir = new File(rootDir, request.getBaseDir());

        final File dockerDir = new File(dir, "src/main/docker");
        dockerDir.mkdirs();
        write(new File(dockerDir, "assembly.xml"), "assembly.xml", initializeModel(request));

        final File inputFile = new File(dir, "pom.xml");
        final File tempOutputFile1 = new File(dir, "pom_tmp1.xml");
        final File tempOutputFile2 = new File(dir, "pom_tmp2.xml");

        try {
            final InputStream is = new FileInputStream(inputFile);
            final OutputStream os = new FileOutputStream(tempOutputFile1);
            MavenModelUtils.addDockerPlugin(request.getArtifactId(), request.getVersion(), dockerHubOrg, is, os, entrypointType);

            FileInputStream is1 = new FileInputStream(tempOutputFile1);
            FileOutputStream os1 = new FileOutputStream(tempOutputFile2);

            Model pomModel = MavenModelUtils.getModel(is1);
            for (Plugin plugin : additionalPlugins) {
                pomModel.getBuild().addPlugin(plugin);
            }
            MavenModelUtils.addExtraPlugins(pomModel);
            MavenModelUtils.addPluginRepositories(pomModel);

            if(!CollectionUtils.isEmpty(requiresUnpack)) {
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
            MavenModelUtils.writeModelToFile(pomModel, os1);

            is.close();
            is1.close();
            os.close();
            os1.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        inputFile.delete();
        tempOutputFile2.renameTo(inputFile);
        tempOutputFile1.delete();
        tempOutputFile2.delete();

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
}

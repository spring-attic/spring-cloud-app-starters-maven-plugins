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

import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.springframework.boot.configurationprocessor.metadata.ConfigurationMetadata;
import org.springframework.boot.configurationprocessor.metadata.JsonMarshaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
/**
 * A gradle task that will gather all Spring Boot metadata files from all transitive dependencies and will aggregate
 * them in one metadata-only artifact.
 *
 * @author Furer Alexander
 */
public class SpringMetadataTask extends Jar {

    public static final String NAME="metadataJar";

    private File configurationMdFile;
    private File propsFile;
    private File mdDir;

    public SpringMetadataTask() {
        setClassifier("metadata");



        mdDir = new File(getProject().getBuildDir(),"springMetadata");

        configurationMdFile = new File(mdDir,"spring-configuration-metadata.json");
        propsFile = new File(mdDir,"spring-configuration-metadata-whitelist.properties");
        getOutputs().upToDateWhen(t->false);
        getOutputs().dir(mdDir);

        from(mdDir,copySpec -> copySpec
                .into("META-INF")
                .include(propsFile.getName(),configurationMdFile.getName())
        );








    }

    @Override
    protected void copy() {
        try {
            boolean dirCreated = mdDir.mkdirs();
            getLogger().info(mdDir.getAbsolutePath() +" created :" + dirCreated);
            aggregateConfigurationMetadata();
            aggregateWhiteListedProperties();
        } catch (Exception e) {
            throw  new RuntimeException(e);
        }

        super.copy();
    }


    private void aggregateConfigurationMetadata() throws Exception {

        ConfigurationMetadata metadata = new ConfigurationMetadata();
        JsonMarshaller jsonMarshaller = new JsonMarshaller();

        Set<File> ownedFiles =findOwnedFiles("META-INF/spring-configuration-metadata.json");
        for(File f : ownedFiles){
            try(FileInputStream fis = new FileInputStream(f)) {
                metadata.merge(jsonMarshaller.read(fis));
            }
        }


        withDependencies("META-INF/spring-configuration-metadata.json",is-> {
            try {
                metadata.merge(jsonMarshaller.read(is));
            } catch (Exception e) {
                throw  new RuntimeException(e);
            }
        });


        try(FileOutputStream fos = new FileOutputStream(configurationMdFile)) {
            jsonMarshaller.write(metadata,fos);
        }

    }


    private void aggregateWhiteListedProperties() throws Exception {
        Properties aggregated = new Properties();


        Set<File> ownedFiles = findOwnedFiles("META-INF/spring-configuration-metadata-whitelist.properties");
        for(File f : ownedFiles){
            try(FileInputStream fis = new FileInputStream(f)) {
                mergeProperties(aggregated,fis);
            }
        }


        withDependencies("META-INF/spring-configuration-metadata-whitelist.properties", is-> {
            try {
                mergeProperties(aggregated,is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });



        try(FileOutputStream fos = new FileOutputStream(propsFile)){
            aggregated.store(fos,null);
        }
    }

    private void mergeProperties(Properties seed, InputStream is) throws IOException {

        Properties properties = new Properties();
        properties.load(is);

        properties.forEach((k,v) ->
            seed.merge(k,v, (v1, v2) ->
                            Stream.concat(Stream.of(((String)v1).split(",")),Stream.of(((String)v2).split(",")))
                                    .map(String::trim)
                                    .distinct()
                                    .collect(Collectors.joining(","))
            )
        );
    }

    private Set<File> findOwnedFiles(String pattern){
        return getProject().getTasks().getAt(JavaPlugin.COMPILE_JAVA_TASK_NAME).getOutputs().getFiles()
                .plus(getProject().getTasks().getAt(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).getOutputs().getFiles())
                .getAsFileTree()
                .matching(p -> p.include(pattern))
                .getFiles();
    }

    private void withDependencies(String pattern, Consumer<InputStream> consumer) throws Exception {
        Set<File> zipFiles = getProject().getConfigurations().getByName("runtime").resolve();
        for(File f : zipFiles){
            ZipFile zipFile = new ZipFile(f);
            ZipEntry mdEntry = zipFile.getEntry(pattern);
            if(null!=mdEntry) {
                try ( InputStream is = zipFile.getInputStream(mdEntry)) {
                    consumer.accept(is);
                }
            }
        }
    }

}

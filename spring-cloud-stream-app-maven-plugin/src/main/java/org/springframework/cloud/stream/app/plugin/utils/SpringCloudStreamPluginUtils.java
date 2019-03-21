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

package org.springframework.cloud.stream.app.plugin.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * @author Soby Chacko
 */
public class SpringCloudStreamPluginUtils {

    public final static String COPY_RIGHT_STRING = "/*\n" +
            " * Copyright 2015-2016 the original author or authors.\n" +
            " *\n" +
            " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
            " * you may not use this file except in compliance with the License.\n" +
            " * You may obtain a copy of the License at\n" +
            " *\n" +
            " *      https://www.apache.org/licenses/LICENSE-2.0\n" +
            " *\n" +
            " * Unless required by applicable law or agreed to in writing, software\n" +
            " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            " * See the License for the specific language governing permissions and\n" +
            " * limitations under the License.\n" +
            " */\n\n";

    private SpringCloudStreamPluginUtils() {
        //prevents instantiation
    }

    public static void cleanupGenProjHome(File genProjecthome) throws IOException {
        FileUtils.cleanDirectory(genProjecthome);
        FileUtils.deleteDirectory(genProjecthome);
    }

    public static void ignoreUnitTestGeneratedByInitializer(String generatedAppHome) throws IOException {
        Collection<File> files = FileUtils.listFiles(new File(generatedAppHome, "src/test/java"), null, true);
        Optional<File> first = files.stream()
                .filter(f -> f.getName().endsWith("ApplicationTests.java"))
                .findFirst();

        if (first.isPresent()){
            StringBuilder sb = new StringBuilder();
            File f1 = first.get();
            Files.readAllLines(f1.toPath()).forEach(l -> {
                if (l.startsWith("import") && !sb.toString().contains("import org.junit.Ignore")) {
                    sb.append("import org.junit.Ignore;\n");
                }
                else if (l.startsWith("@RunWith") && !sb.toString().contains("@Ignore")) {
                    sb.append("@Ignore\n");
                }
                sb.append(l);
                sb.append("\n");
            });
            Files.write(f1.toPath(), sb.toString().getBytes());
        }
    }

    public static void addCopyRight(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(COPY_RIGHT_STRING);
        Files.readAllLines(p).forEach(l -> {
            sb.append(l);
            sb.append("\n");
        });
        Files.write(p, sb.toString().getBytes());
    }

    public static void addExtraTestConfig(String generatedAppHome, String clazzInfo) throws IOException {
        Collection<File> files = FileUtils.listFiles(new File(generatedAppHome, "src/test/java"), null, true);
        Optional<File> first = files.stream()
                .filter(f -> f.getName().endsWith("ApplicationTests.java"))
                .findFirst();

        if (first.isPresent()){
            StringBuilder sb = new StringBuilder();
            File f1 = first.get();
            Files.readAllLines(f1.toPath()).forEach(l -> {
                if (l.startsWith("@SpringApplicationConfiguration")) {
                    sb.append("@SpringApplicationConfiguration(").append(clazzInfo).append(")");
                }
                else if (l.startsWith("@SpringBootTest")) {
                    sb.append("@SpringBootTest(").append(clazzInfo).append(")");
                }
                else {
                    sb.append(l);
                }
                sb.append("\n");
            });
            Files.write(f1.toPath(), sb.toString().getBytes());
        }
    }

    public static void addAutoConfigImport(String generatedAppHome, String autoConfigClazz) throws IOException {
        Collection<File> files = FileUtils.listFiles(new File(generatedAppHome, "src/main/java"), null, true);
        Optional<File> first = files.stream()
                .filter(f -> f.getName().endsWith("Application.java"))
                .findFirst();

        if (first.isPresent()){
            StringBuilder sb = new StringBuilder();
            File f1 = first.get();
            Files.readAllLines(f1.toPath()).forEach(l -> {
                if (l.startsWith("import org.springframework.boot.autoconfigure.SpringBootApplication;")) {
                    sb.append(l).append("\n").append("import org.springframework.context.annotation.Import;\n");
                }
                else if (l.startsWith("@SpringBootApplication")) {
                    sb.append(l).append("\n").append("@Import(").append(autoConfigClazz).append(")");
                }
                else {
                    sb.append(l);
                }
                sb.append("\n");
            });
            Files.write(f1.toPath(), sb.toString().getBytes());
        }

    }

    public static Xpp3Dom addElement(Xpp3Dom parentElement, String elementName) {
        return addElement(parentElement, elementName, null);
    }

    public static Xpp3Dom addElement(Xpp3Dom parentElement, String elementName, String elementValue) {
        Xpp3Dom child = new Xpp3Dom(elementName);
        child.setValue(elementValue);
        parentElement.addChild(child);
        return child;
    }
}

/*
 * Copyright 2020-2020 the original author or authors.
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

import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;

/**
 * Uses the private MavenXpp3WriterEx write methods to convert Model elements into XML strings.
 *
 * @author Christian Tzolov
 */
public class MavenXmlWriter {

	/**
	 * Serializes an {@link Dependency} instance into XML text.
	 * Uses the private {@link MavenXpp3Writer#writeDependency(Dependency, String, XmlSerializer)} method.
	 */
	public static String toXml(Dependency dependency) {
		return write(serializer -> invokeMavenXppWriteMethod(
				dependency, "writeDependency", "dependency", serializer));
	}

	/**
	 * Serializes an {@link Dependency} instance into XML text.
	 * Uses the private {@link MavenXpp3Writer#writePlugin(Plugin, String, XmlSerializer)} method.
	 */
	public static String toXml(Plugin plugin) {
		return write(serializer -> invokeMavenXppWriteMethod(
				plugin, "writePlugin", "plugin", serializer));
	}

	public interface FragmentWriter {
		void write(XmlSerializer serializer);
	}

	public static String write(FragmentWriter fragmentWriter) {
		try {
			Writer writer = new StringWriter();

			XmlSerializer serializer = new MXSerializer();
			serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", "  ");
			serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-line-separator", "\n");
			serializer.setOutput(writer);
			serializer.startDocument("UTF-8", null);

			fragmentWriter.write(serializer);

			serializer.endDocument();

			String result = writer.toString();
			result = result.substring(result.indexOf('\n') + 1); // Remove first line
			result = result.replaceAll("\n", "\n    ");
			return result;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Uses the private MavenXpp3Writer methods to convert Model elements into XML strings.
	 *
	 * @param modelElementToWrite a parameterizable org.apache.maven.model.T instance to serialize.
	 * @param <T> The actual org.apache.maven.model's class type.
	 * @param writeMethodName Name of the private {@link MavenXpp3Writer} method to call.
	 * @param xmlTagName Name for the XML tag to surround the serialized model instance.
	 * @param serializer The XmlSerializer used to write the xml.
	 */
	public static <T> void invokeMavenXppWriteMethod(T modelElementToWrite, String writeMethodName,
			String xmlTagName, XmlSerializer serializer) {

		try {
			MavenXpp3Writer pomWriter = new MavenXpp3Writer();
			Method method = pomWriter.getClass().getDeclaredMethod(
					writeMethodName, modelElementToWrite.getClass(), String.class, XmlSerializer.class);
			method.setAccessible(true); // allow invoking private method.
			method.invoke(pomWriter, modelElementToWrite, xmlTagName, serializer);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}

/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.cloud.stream.app.plugin.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Christian Tzolov
 */
public class AppDefinition {

	public enum ContainerImageFormat {Docker, OCI}

	public enum AppType {source, processor, sink}

	private String name;

	private AppType type;

	private String version;

	private List<String> metadataSourceTypeFilters = new ArrayList<>();

	private List<String> metadataNameFilters = new ArrayList<>();

	private List<String> additionalProperties = new ArrayList<>();

	private List<String> additionalMavenDependencies = new ArrayList<>();

	private List<String> additionalMavenPlugins = new ArrayList<>();

	private String functionClass;

	private ContainerImageFormat containerImageFormat = ContainerImageFormat.Docker;

	/**
	 * True will attempt to inline the (white) filtered Spring Boot metadata as Base64 encoded property.
	 */
	private boolean enableContainerImageMetadata = false;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public AppType getType() {
		return type;
	}

	public void setType(AppType type) {
		this.type = type;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<String> getAdditionalProperties() {
		return additionalProperties;
	}

	public void setAdditionalProperties(List<String> additionalProperties) {
		this.additionalProperties = additionalProperties;
	}

	public List<String> getAdditionalMavenDependencies() {
		return additionalMavenDependencies;
	}

	public List<String> getAdditionalMavenPlugins() {
		return additionalMavenPlugins;
	}

	public void setAdditionalMavenPlugins(List<String> additionalMavenPlugins) {
		this.additionalMavenPlugins = additionalMavenPlugins;
	}

	public boolean isSupplier() {
		return type == AppType.source;
	}

	public boolean isConsumer() {
		return type == AppType.sink;
	}

	public boolean isFunction() {
		return type == AppType.processor;
	}

	public void setAdditionalMavenDependencies(List<String> additionalMavenDependencies) {
		this.additionalMavenDependencies = additionalMavenDependencies;
	}

	public String getFunctionClass() {
		return functionClass;
	}

	public void setFunctionClass(String functionClass) {
		this.functionClass = functionClass;
	}

	public List<String> getMetadataSourceTypeFilters() {
		return metadataSourceTypeFilters;
	}

	public void setMetadataSourceTypeFilters(List<String> metadataSourceTypeFilters) {
		this.metadataSourceTypeFilters = metadataSourceTypeFilters;
	}

	public List<String> getMetadataNameFilters() {
		return metadataNameFilters;
	}

	public void setMetadataNameFilters(List<String> metadataNameFilters) {
		this.metadataNameFilters = metadataNameFilters;
	}

	public ContainerImageFormat getContainerImageFormat() {
		return containerImageFormat;
	}

	public void setContainerImageFormat(ContainerImageFormat containerImageFormat) {
		this.containerImageFormat = containerImageFormat;
	}

	public boolean isEnableContainerImageMetadata() {
		return enableContainerImageMetadata;
	}

	public void setEnableContainerImageMetadata(boolean enableContainerImageMetadata) {
		this.enableContainerImageMetadata = enableContainerImageMetadata;
	}
}

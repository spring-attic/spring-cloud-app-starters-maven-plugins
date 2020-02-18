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

/**
 * @author Christian Tzolov
 */
public class AppBom {
	/**
	 * Spring Boot version to use.
	 */
	private String springBootVersion;

	/**
	 * Stream apps core version
	 */
	private String streamAppsParentVersion;

	/**
	 * Spring Cloud Stream Dependency version.
	 */
	private String springCloudStreamDependenciesVersion;

	/**
	 * Spring Cloud Function Dependencies Version
	 */
	private String springCloudFunctionDependenciesVersion;

	/**
	 * Spring Cloud Dependencies Version
	 */
	private String springCloudDependenciesVersion;

	public String getSpringBootVersion() {
		return springBootVersion;
	}

	public void setSpringBootVersion(String springBootVersion) {
		this.springBootVersion = springBootVersion;
	}

	public AppBom withSpringBootVersion(String springBootVersion) {
		this.springBootVersion = springBootVersion;
		return this;
	}

	public String getStreamAppsParentVersion() {
		return streamAppsParentVersion;
	}

	public void setStreamAppsParentVersion(String streamAppsParentVersion) {
		this.streamAppsParentVersion = streamAppsParentVersion;
	}

	public AppBom withStreamAppsParentVersion(String streamAppsParentVersion) {
		this.streamAppsParentVersion = streamAppsParentVersion;
		return this;
	}

	public String getSpringCloudStreamDependenciesVersion() {
		return springCloudStreamDependenciesVersion;
	}

	public void setSpringCloudStreamDependenciesVersion(String springCloudStreamDependenciesVersion) {
		this.springCloudStreamDependenciesVersion = springCloudStreamDependenciesVersion;
	}

	public AppBom withSpringCloudStreamDependenciesVersion(String springCloudStreamDependenciesVersion) {
		this.springCloudStreamDependenciesVersion = springCloudStreamDependenciesVersion;
		return this;
	}

	public String getSpringCloudFunctionDependenciesVersion() {
		return springCloudFunctionDependenciesVersion;
	}

	public void setSpringCloudFunctionDependenciesVersion(String springCloudFunctionDependenciesVersion) {
		this.springCloudFunctionDependenciesVersion = springCloudFunctionDependenciesVersion;
	}

	public AppBom withSpringCloudFunctionDependenciesVersion(String springCloudFunctionDependenciesVersion) {
		this.springCloudFunctionDependenciesVersion = springCloudFunctionDependenciesVersion;
		return this;
	}

	public String getSpringCloudDependenciesVersion() {
		return springCloudDependenciesVersion;
	}

	public void setSpringCloudDependenciesVersion(String springCloudDependenciesVersion) {
		this.springCloudDependenciesVersion = springCloudDependenciesVersion;
	}

	public AppBom withSpringCloudDependenciesVersion(String springCloudDependenciesVersion) {
		this.springCloudDependenciesVersion = springCloudDependenciesVersion;
		return this;
	}

}

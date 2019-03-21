/*
 * Copyright 2017-2018 the original author or authors.
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
import java.util.List;

/**
 * @author Soby Chacko
 */
public class BinderMetadata {

	List<Dependency> forceDependencies = new ArrayList<>();

	public List<Dependency> getForceDependencies() {
		return forceDependencies;
	}

	public void setForceDependencies(List<Dependency> forceDependencies) {
		this.forceDependencies = forceDependencies;
	}

}

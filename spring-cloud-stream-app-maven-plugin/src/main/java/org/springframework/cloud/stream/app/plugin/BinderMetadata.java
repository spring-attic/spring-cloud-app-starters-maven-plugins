package org.springframework.cloud.stream.app.plugin;

import java.util.ArrayList;
import java.util.List;

import io.spring.initializr.metadata.Dependency;

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

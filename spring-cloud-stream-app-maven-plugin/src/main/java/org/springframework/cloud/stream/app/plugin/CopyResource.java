package org.springframework.cloud.stream.app.plugin;

/**
 * @author Christian Tzolov
 */
public class CopyResource {

	private String groupId = "${project.groupId}";
	private String artifactId;
	private String version = "${project.version}";
	private String includes;

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getIncludes() {
		return includes;
	}

	public void setIncludes(String includes) {
		this.includes = includes;
	}

	@Override
	public String toString() {
		return "CopyResource{" +
				"groupId='" + groupId + '\'' +
				", artifactId='" + artifactId + '\'' +
				", version='" + version + '\'' +
				", includes='" + includes + '\'' +
				'}';
	}
}

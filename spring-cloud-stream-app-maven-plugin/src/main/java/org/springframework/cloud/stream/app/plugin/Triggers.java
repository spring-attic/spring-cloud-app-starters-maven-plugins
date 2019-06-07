package org.springframework.cloud.stream.app.plugin;

public class Triggers {

	private String triggerConfiguration;
	private String triggerProperties;

	public String getTriggerConfiguration() {
		return triggerConfiguration;
	}

	public void setTriggerConfiguration(String triggerConfiguration) {
		this.triggerConfiguration = triggerConfiguration;
	}

	public String getTriggerProperties() {
		return triggerProperties;
	}

	public void setTriggerProperties(String triggerProperties) {
		this.triggerProperties = triggerProperties;
	}
}

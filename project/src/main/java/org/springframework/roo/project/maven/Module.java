package org.springframework.roo.project.maven;

public class Module {

	private final String name;
	private final String pomPath;

	public Module(String name, String pomPath) {
		this.name = name;
		this.pomPath = pomPath;
	}

	public String getName() {
		return name;
	}

	public String getPomPath() {
		return pomPath;
	}
}

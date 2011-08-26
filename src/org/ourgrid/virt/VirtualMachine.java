package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

public class VirtualMachine {

	private String name;
	private String imagePath;
	private Map<String, String> configuration = new HashMap<String, String>();

	public VirtualMachine(String name, String imagePath) {
		this.name = name;
		this.imagePath = imagePath;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public Map<String, String> getConfiguration() {
		return new HashMap<String, String>(configuration);
	}

	public void setConfiguration(Map<String, String> configuration) {
		this.configuration.clear();
		this.configuration.putAll(configuration);
	}

}

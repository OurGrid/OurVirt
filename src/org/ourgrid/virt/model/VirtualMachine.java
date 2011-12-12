package org.ourgrid.virt.model;

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a virtual machine, containing its name and its configurations map.
 * It is used for dealing with virtual machine in the system with ease.
 */
public class VirtualMachine {

	private String name;
	private Map<String, String> configuration = new HashMap<String, String>();

	public VirtualMachine(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, String> getConfiguration() {
		return new HashMap<String, String>(configuration);
	}

	public void setConfiguration(Map<String, String> configuration) {
		this.configuration.clear();
		this.configuration.putAll(configuration);
	}

	public void setProperty(String property, String value) {
		this.configuration.put(property, value);
	}

	public String getProperty(String property) {
		return this.configuration.get(property);
	}
}

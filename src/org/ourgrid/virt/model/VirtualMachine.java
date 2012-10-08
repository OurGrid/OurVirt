package org.ourgrid.virt.model;

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a virtual machine, containing its name and its configurations map.
 * It is used for dealing with virtual machine in the system with ease.
 */
public class VirtualMachine {

	private String name;
	private Map<String, Object> configuration = new HashMap<String, Object>();

	public VirtualMachine(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, Object> getConfiguration() {
		return new HashMap<String, Object>(configuration);
	}

	public void setConfiguration(Map<String, ?> configuration) {
		this.configuration.clear();
		this.configuration.putAll(configuration);
	}

	public void setProperty(String property, Object value) {
		this.configuration.put(property, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T getProperty(String property) {
		return (T) this.configuration.get(property);
	}

}

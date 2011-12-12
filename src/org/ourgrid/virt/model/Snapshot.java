package org.ourgrid.virt.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This class represents a snapshot, containing its name, guestpath and hostpath.
 * It is used for dealing with shared folders in the system with ease.
 */
public class Snapshot {
	
	public static final String JSON_NAME = "name";
	private String name;

	public Snapshot(String name) {
		this.name = name;
	}


	public static Snapshot parse(JsonElement jsonElement) {
		
		JsonObject snapshotJson = jsonElement.getAsJsonObject();
		String name = snapshotJson.get(JSON_NAME).getAsString();
		
		return new Snapshot(name);
	}
	
	public String getName() {
		return name;
	}


	public JsonElement toJson() {
		
		JsonObject snapshotJson = new JsonObject();
		snapshotJson.addProperty(JSON_NAME, name);
		
		return snapshotJson;
	}
	
	

}

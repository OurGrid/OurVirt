package org.ourgrid.virt.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This class represents a shared folder, containing its name, guestpath and hostpath.
 * It is useful for dealing with shared folders in the system with ease.
 */
public class SharedFolder {

	public static final String JSON_GUESTPATH = "guestpath";
	public static final String JSON_HOSTPATH = "hostpath";
	public static final String JSON_NAME = "name";
	
	private final String name;
	private final String guestpath;
	private final String hostpath;

	public SharedFolder(String name, String hostpath, String guestpath) {
		this.name = name;
		this.guestpath = guestpath;
		this.hostpath = hostpath;
	}

	public String getName() {
		return name;
	}

	public String getGuestpath() {
		return guestpath;
	}

	public String getHostpath() {
		return hostpath;
	}
	
	public JsonElement toJson() {
		JsonObject sfObject = new JsonObject();
		sfObject.addProperty(JSON_NAME, name);
		sfObject.addProperty(JSON_HOSTPATH, hostpath);
		sfObject.addProperty(JSON_GUESTPATH, guestpath);
		
		return sfObject;
	}
	
	public static SharedFolder parse(JsonElement jsonElement) {
		
		JsonObject sharedFolderJson = jsonElement.getAsJsonObject();
		String name = sharedFolderJson.get(JSON_NAME).getAsString();
		String guestpath = sharedFolderJson.get(JSON_GUESTPATH).getAsString();
		String hostpath = sharedFolderJson.get(JSON_HOSTPATH).getAsString();
		
		return new SharedFolder(name, hostpath, guestpath);
	}

}

package org.ourgrid.virt.strategies;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.model.SharedFolder;
import org.ourgrid.virt.model.Snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


/**
 * This class is responsible for creating (if not exists yet) and manage
 * the OurVirt folder in the system and its files, which refer to the registered virtual machines.
 * It is used to read and write in these files according to the changes made in the virtual machines.
 */
public class HypervisorConfigurationFile {

	private static final String JSON_SHAREDFOLDERS = "sharedfolders";
	private static final String JSON_SNAPSHOTS = "snapshots";
	private static final String STORAGE_DIR = System.getProperty("user.home") + "/.ourvirt";
	
	private final String filePath;
	
	public HypervisorConfigurationFile(String vmName) throws IOException {
		
		File storageDir = new File(STORAGE_DIR);
		if (!storageDir.exists()) {
			storageDir.mkdirs();
		}
		
		this.filePath = STORAGE_DIR + "/" + vmName;
		File confFile = new File(filePath);
		if (! confFile.exists()) {
			confFile.createNewFile();
		}
		
	}

	/**
	 * Returns a list with the registered shared folders of the virtual machine.
	 * @return the shared folders list
	 * @throws IOException
	 */
	public List<SharedFolder> getSharedFolders() throws IOException {
		
		JsonObject vmJson = parse();
		
		JsonElement sharedFoldersJson = vmJson.get(JSON_SHAREDFOLDERS);
		
		if (sharedFoldersJson == null) {
			sharedFoldersJson = new JsonArray();
			vmJson.add(JSON_SHAREDFOLDERS, sharedFoldersJson);
		}
		
		JsonArray sharedFoldersJsonArray = sharedFoldersJson.getAsJsonArray();
		
		List<SharedFolder> sharedFolders = new LinkedList<SharedFolder>();
		
		for (int i = 0; i < sharedFoldersJsonArray.size(); i++) {
			sharedFolders.add(SharedFolder.parse(sharedFoldersJsonArray.get(i)));
		}
		
		return sharedFolders;
	}
	
	/**
	 * Adds a new shared folder to the virtual machine shared folders list
	 * @param sharedFolder the shared folder to be added
	 * @throws IOException
	 */
	public void addSharedFolder(SharedFolder sharedFolder) throws IOException {
		
		JsonObject vmJson = parse();


		JsonElement sharedFoldersJson = vmJson.get(JSON_SHAREDFOLDERS);
		
		if (sharedFoldersJson == null) {
			sharedFoldersJson = new JsonArray();
			vmJson.add(JSON_SHAREDFOLDERS, sharedFoldersJson);
		}
		
		JsonArray sharedFoldersJsonArray = sharedFoldersJson.getAsJsonArray();
		
		boolean isNew = true;
		
		for (int i = 0; i < sharedFoldersJsonArray.size(); i++) {
			JsonObject eachSharedFolderJson = sharedFoldersJsonArray.get(i).getAsJsonObject();
			
			if (sharedFolder.getName().equals(eachSharedFolderJson.get(SharedFolder.JSON_NAME).getAsString())) {
				
				eachSharedFolderJson.addProperty(SharedFolder.JSON_GUESTPATH, sharedFolder.getGuestpath());
				eachSharedFolderJson.addProperty(SharedFolder.JSON_HOSTPATH, sharedFolder.getHostpath());
				isNew = false;
				break;
			}
		}

		if (isNew) {
			sharedFoldersJsonArray.add(sharedFolder.toJson());
		}

		writeToFile(vmJson);
	}
	
	/**
	 * Removes the shared folder with the given name from the virtual machine shared folders list 
	 * @param shareName the name identifier of the shared folder to be removed 
	 * @throws IOException
	 */
	public void removeSharedFolder(String shareName) throws IOException {
		
		JsonObject vmJson = parse();
		
		JsonArray newSharedFoldersJson = new JsonArray();
		JsonElement sharedFolderElement = vmJson.get(JSON_SHAREDFOLDERS);
		if (sharedFolderElement == null) {
			return;
		}
		
		JsonArray sharedFoldersJson = sharedFolderElement.getAsJsonArray();
		
		for (int i = 0; i < sharedFoldersJson.size(); i++) {
			SharedFolder sharedFolder = SharedFolder.parse(sharedFoldersJson.get(i));
			if (!sharedFolder.getName().equals(shareName)) {
				newSharedFoldersJson.add(sharedFoldersJson.get(i));
			}
		}
		
		vmJson.add(JSON_SHAREDFOLDERS, newSharedFoldersJson);
		
		writeToFile(vmJson);
	}

	// This method overrides the previous content of the file.
	private void writeToFile(JsonObject vmJson) throws IOException,
			FileNotFoundException {
		IOUtils.write(vmJson.toString(), new FileOutputStream(filePath));
	}

	private JsonObject parse() throws IOException {
		JsonParser parser = new JsonParser();
		
		String jsonString = IOUtils.toString(new FileReader(filePath));
		
		if (jsonString.trim().isEmpty()) {
			return new JsonObject();
		}

		JsonElement jsonElement = parser.parse(jsonString);
		JsonObject vmJson = jsonElement.getAsJsonObject();
		
		return vmJson;
	}

	/**
	 * Returns the registered shared folder of the virtual machine with the given shareName. 
	 * @param shareName the name identifier of the shared folder
	 * @return the shared folder with the given name if it exists, <i><b>null</i></b> otherwise
	 * @throws IOException
	 */
	public SharedFolder getSharedFolder(String shareName) throws IOException {
		for (SharedFolder sharedFolder : getSharedFolders()) {
			if (sharedFolder.getName().equals(shareName)) {
				return sharedFolder;
			}
		}
		
		return null;
	}
	
	/**
	 * Returns the list of registered snapshots of the virtual machine
	 * @return the virtual machine snapshots list
	 * @throws IOException
	 */
	public List<Snapshot> getSnapshots() throws IOException {
		
		JsonObject vmJson = parse();
		
		JsonElement snapshotsJson = vmJson.get(JSON_SNAPSHOTS);
		
		if (snapshotsJson == null) {
			snapshotsJson = new JsonArray();
			vmJson.add(JSON_SHAREDFOLDERS, snapshotsJson);
		}
		
		JsonArray snapshotsJsonArray = snapshotsJson.getAsJsonArray();
		
		List<Snapshot> snapshots = new LinkedList<Snapshot>();
		
		for (int i = 0; i < snapshotsJsonArray.size(); i++) {
			snapshots.add(Snapshot.parse(snapshotsJsonArray.get(i)));
		}
		
		return snapshots;
	}
	
	/**
	 * Adds a snapshot to the virtual machine snapshots list
	 * @param snapshot the snapshot to be added
	 * @throws IOException
	 */
	public void addSnapshot(Snapshot snapshot) throws IOException {
		
		JsonObject vmJson = parse();

		JsonElement snapshotsJson = vmJson.get(JSON_SNAPSHOTS);
		
		if (snapshotsJson == null) {
			snapshotsJson = new JsonArray();
			vmJson.add(JSON_SNAPSHOTS, snapshotsJson);
		}
		
		JsonArray snapshotsJsonArray = snapshotsJson.getAsJsonArray();
		
		boolean isNew = true;
		
		for (int i = 0; i < snapshotsJsonArray.size(); i++) {
			JsonObject eachSnapshotJson = snapshotsJsonArray.get(i).getAsJsonObject();
			
			if (snapshot.getName().equals(eachSnapshotJson.get(SharedFolder.JSON_NAME).getAsString())) {
				isNew = false;
				break;
			}
		}

		if (isNew) {
			snapshotsJsonArray.add(snapshot.toJson());
		}
		writeToFile(vmJson);
	}
	
	/**
	 * Removes the snapshot with the given name from the virtual machine snapshots list 
	 * @param snapshotName the name identifier of the snapshot to be removed
	 * @throws IOException
	 */
	public void removeSnapshot(String snapshotName) throws IOException {
		
		JsonObject vmJson = parse();
		
		JsonArray newSnapshotsJson = new JsonArray();
		JsonArray snapshotsJson = vmJson.get(JSON_SNAPSHOTS).getAsJsonArray();
		for (int i = 0; i < snapshotsJson.size(); i++) {
			Snapshot snapshot = Snapshot.parse(snapshotsJson.get(i));
			if (!snapshot.getName().equals(snapshotName)) {
				newSnapshotsJson.add(snapshotsJson.get(i));
			}
		}
		
		vmJson.add(JSON_SNAPSHOTS, newSnapshotsJson);
		
		writeToFile(vmJson);
	}
	
	/**
	 * Returns the snapshot with the given name of the virtual machine
	 * @param snapshotName the name identifier of the snapshot
	 * @return the snapshot with the given name if it exists, <i><b>null</i></b> otherwise
	 * @throws IOException
	 */
	public Snapshot getSnapshot(String snapshotName) throws IOException {
		for (Snapshot snapshot : getSnapshots()) {
			if (snapshot.getName().equals(snapshotName)) {
				return snapshot;
			}
		}
		
		return null;
	}
	
	/**
	 * Deletes the virtual machine configuration file.
	 * @throws IOException
	 */
	public void deleteVM() throws IOException {
		File confFile = new File(filePath);
		if (! confFile.exists()) {
			confFile.delete();
		}
		
		File storageDir = new File(STORAGE_DIR);
		if (storageDir.exists() && storageDir.list().length == 0){
			storageDir.delete();
		}
		
	}
	
}

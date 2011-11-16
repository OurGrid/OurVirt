package org.ourgrid.virt.strategies.vserver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class VServerStrategy implements HypervisorStrategy {

	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {
		
		if (HypervisorUtils.isLinuxGuest(virtualMachine)) {
		
			boolean vmExists = checkWhetherMachineExists(virtualMachine);
			
			if (vmExists){
				return;
			}
			
			register(virtualMachine);
			
		} else {
			throw new Exception("Guest OS not supported");
		}
	}

private void register(VirtualMachine virtualMachine) throws Exception {
		
		String vmName = virtualMachine.getName();
		String imagePath = virtualMachine.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String osVersion = virtualMachine.getProperty(VirtualMachineConstants.OS_VERSION);
		
		ProcessBuilder buildVMBuilder = getVServerProcessBuilder(
				vmName+" build -m template -- -t "+imagePath+" -d "+osVersion.toLowerCase());
		HypervisorUtils.runAndCheckProcess(buildVMBuilder);
	}

private boolean checkWhetherMachineExists(VirtualMachine virtualMachine) {
	
		String vmName = virtualMachine.getName();
		return new File("/etc/vservers/"+vmName).exists();
	}

	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {
		
		startVirtualMachine(virtualMachine);
		checkOSStarted(virtualMachine);
	}

	private static JsonObject getSharedFolderPaths(VirtualMachine virtualMachine, String shareName) {
		String sharedFolders = virtualMachine.getProperty(
				VirtualMachineConstants.SHARED_FOLDERS);
		
		JsonArray sharedFoldersJson = (JsonArray) new JsonParser().parse(sharedFolders);
		
		for (int i = 0; i < sharedFoldersJson.size(); i++) {
			
			JsonObject sharedFolderJson = (JsonObject) sharedFoldersJson.get(i);
			String name = sharedFolderJson.get("name").getAsString();
			
			if (name.equals(shareName)) {
				return sharedFolderJson;
			}
		}
		
		return null;
	}
	
	@Override
	public void mountSharedFolder(VirtualMachine virtualMachine,
			String name)
			throws Exception {
		
		String vmName = virtualMachine.getName();
		JsonObject sharedFolderPaths =  getSharedFolderPaths(virtualMachine, name);
		
		String hostPath = sharedFolderPaths.get("hostpath").getAsString();
		String guestPath = sharedFolderPaths.get("guestpath").getAsString();
		
		if (guestPath == null || hostPath == null) {
			throw new IllegalArgumentException("Shared folder [" + name + "] does not exist.");
		}
		
		ExecutionResult mountProcess = HypervisorUtils.runProcess(
				getProcessBuilder("sudo mount --bind " + hostPath + " /etc/vservers/.defaults/vdirbase/" + vmName + "/" + guestPath ));
 		HypervisorUtils.checkReturnValue(mountProcess);
 		
 		ExecutionResult restartProcess = HypervisorUtils.runProcess(
				getVServerProcessBuilder( vmName + " restart"));
 		HypervisorUtils.checkReturnValue(restartProcess);
		
	}

	private void startVirtualMachine(VirtualMachine virtualMachine)
			throws IOException, Exception {
		
		ProcessBuilder startProcessBuilder = getProcessBuilder(virtualMachine, "start");
		HypervisorUtils.runAndCheckProcess(startProcessBuilder);
	}

	private void checkOSStarted(VirtualMachine virtualMachine)
			throws InterruptedException {
		while (true) {
			try {

				ExecutionResult executionResult = exec(virtualMachine,
						"/bin/echo check-started");
				HypervisorUtils.checkReturnValue(executionResult);

				break;
			} catch (Exception e) {
			}

			Thread.sleep(1000 * 10);
		}
	}

	@Override
	public void stop(VirtualMachine virtualMachine) throws Exception {
		
		ProcessBuilder stopProcessBuilder = getProcessBuilder(virtualMachine, "stop");
		HypervisorUtils.runAndCheckProcess(stopProcessBuilder);
	}
	
	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String command) throws Exception {
		
		ProcessBuilder stopProcessBuilder = getProcessBuilder(virtualMachine, "exec " + command);
		return HypervisorUtils.runProcess(stopProcessBuilder);
	}
	
	@Override
	public void takeSnapshot(String vMName, String snapshotName)
			throws Exception {
		//FIXME TODO
	}
	
	@Override
	public void restoreSnapshot(String vMName, String snapshotName)
			throws Exception {
		//FIXME TODO
	}
	
	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {
		
		ProcessBuilder destroyProcessBuilder = getProcessBuilder(virtualMachine, "delete");
		HypervisorUtils.runAndCheckProcess(destroyProcessBuilder);
	}
	
	private static ProcessBuilder getProcessBuilder(VirtualMachine virtualMachine, String cmd) throws Exception {
		return getVServerProcessBuilder(virtualMachine.getName() + " " + cmd);
	}
	
	private static ProcessBuilder getVServerProcessBuilder(String cmd) throws Exception {
		
		String vserverCmdLine = "/usr/bin/sudo vserver " + cmd;
		return getProcessBuilder(vserverCmdLine);
	}
	
private static ProcessBuilder getProcessBuilder(String cmd) throws Exception {
		
		ProcessBuilder processBuilder = null;
		
		if (HypervisorUtils.isLinuxHost()) {
			
			List<String> matchList = splitCmdLine(cmd); 
			processBuilder =  new ProcessBuilder(matchList.toArray(new String[]{}));
			
		} else {
			throw new Exception("Host OS not supported");
		}
		
		return processBuilder;
	}

	private static List<String> splitCmdLine(String vboxManageCmdLine) {
		List<String> matchList = new ArrayList<String>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(vboxManageCmdLine);
		while (regexMatcher.find()) {
		    if (regexMatcher.group(1) != null) {
		        // Add double-quoted string without the quotes
		        matchList.add(regexMatcher.group(1));
		    } else if (regexMatcher.group(2) != null) {
		        // Add single-quoted string without the quotes
		        matchList.add(regexMatcher.group(2));
		    } else {
		        // Add unquoted word
		        matchList.add(regexMatcher.group());
		    }
		}
		return matchList;
	}
	
	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath) throws Exception {

		String vmName = virtualMachine.getName();
		ExecutionResult createSharedFolderDir = HypervisorUtils.runProcess(
				getProcessBuilder("/usr/bin/sudo /bin/mkdir -p /etc/vservers/.defaults/vdirbase/" + vmName + "/" + guestPath));
		HypervisorUtils.checkReturnValue(createSharedFolderDir);		
		
		
		ExecutionResult fstabEntry = HypervisorUtils.runProcess(
				getProcessBuilder("echo " + hostPath + " " + guestPath + " none bind 0 0 >> /etc/vservers/" + vmName + "/fstab"));
		HypervisorUtils.checkReturnValue(fstabEntry);		
		
		String sharedFolders = virtualMachine.getProperty(
				VirtualMachineConstants.SHARED_FOLDERS);
		JsonArray sharedFoldersArray = null;
		
		if (sharedFolders == null) {
			sharedFoldersArray = new JsonArray();
		} else {
			sharedFoldersArray = (JsonArray) new JsonParser().parse(sharedFolders);
		}
		
		JsonObject sfObject = new JsonObject();
		sfObject.addProperty("name", shareName);
		sfObject.addProperty("hostpath", hostPath);
		sfObject.addProperty("guestpath", guestPath);
		
		sharedFoldersArray.add(sfObject);
		
		virtualMachine.setProperty(
				VirtualMachineConstants.SHARED_FOLDERS, 
				sharedFoldersArray.toString());
	}

	@Override
	public List<String> listVMs() throws Exception {
		ProcessBuilder listBuilder = getVServerProcessBuilder("ls /etc/vservers/");
		ExecutionResult listResult = HypervisorUtils.runProcess(listBuilder);
		
		HypervisorUtils.checkReturnValue(listResult);
		
		List<String> vmsOutput = listResult.getStdOut();
		String[] vms = vmsOutput.get(0).split(" {1,}+");
		
		return Arrays.asList(vms);
	}

	@Override
	public boolean isSupported() {
		try {
			ProcessBuilder versionProcessBuilder = getVServerProcessBuilder("--version");
			HypervisorUtils.runAndCheckProcess(versionProcessBuilder);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public VirtualMachineStatus status(VirtualMachine virtualMachine)
			throws Exception {
		
		ProcessBuilder statusProcess = getProcessBuilder(virtualMachine, "status");
		ExecutionResult statusInfo = HypervisorUtils.runProcess(statusProcess);
		
		HypervisorUtils.checkReturnValue(statusInfo);
		
		String status = statusInfo.getStdOut().get(0);
		if ( status.contains("running") ){
			return VirtualMachineStatus.RUNNING;
		}
		else if ( status.contains("stopped") ){
			return VirtualMachineStatus.POWERED_OFF;
		}
		return VirtualMachineStatus.NOT_REGISTERED;
	}

	@Override
	public List<String> listSnapshots(VirtualMachine virtualMachine) throws Exception {
		return new ArrayList<String>();
		//FIXME TODO
	}

	@Override
	public List<String> listSharedFolders(VirtualMachine virtualMachine) throws Exception {
		return new ArrayList<String>();
	}

}

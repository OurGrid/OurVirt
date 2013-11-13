package org.ourgrid.virt.strategies.vbox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.exception.SnapshotAlreadyExistsException;
import org.ourgrid.virt.model.CPUStats;
import org.ourgrid.virt.model.DiskStats;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.NetworkStats;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;
import org.ourgrid.virt.strategies.LinuxUtils;

public class VBoxStrategy implements HypervisorStrategy {

	private static final String FILE_ERROR = "VBOX_E_FILE_ERROR";
	private static final String OBJECT_IN_USE = "VBOX_E_OBJECT_IN_USE";
	private static final String OBJECT_NOT_FOUND = "VBOX_E_OBJECT_NOT_FOUND";
	private static final String INVALID_ARG = "E_INVALIDARG";
	private static final String NS_INVALID_ARG = "NS_ERROR_INVALID_ARG";
	private static final String COPY_ERROR = "VBOX_E_IPRT_ERROR";
	private static final String VIRTUALBOX_VMS = System.getProperty("user.home") + "/VirtualBox VMs";

	private static final String DISK_CONTROLLER_NAME = "Disk Controller";
	private final int START_RECHECK_DELAY = 10;

	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {

		VirtualMachineStatus status = status(virtualMachine);

		if ( status == VirtualMachineStatus.RUNNING ){
			return;
		}

		register(virtualMachine);
		boolean definedSata = define(virtualMachine);

		if (!definedSata) {
			return;
		}

		String imagePath = virtualMachine.getProperty(
				VirtualMachineConstants.DISK_IMAGE_PATH);
		attachDisk(virtualMachine, imagePath);
	}

	/**
	 * Throws an exception if the disk could not be attached
	 * for any reason.
	 * 
	 * @param virtualMachine
	 * @param diskPath
	 * @return
	 * @throws Exception
	 */
	private void attachDisk(VirtualMachine virtualMachine, String diskPath) 
			throws Exception {

		if (!new File(diskPath).exists()) {
			throw new FileNotFoundException("The image file does not exist: " + diskPath);
		}

		ProcessBuilder attachMediaBuilder = getProcessBuilder(
				"storageattach " + virtualMachine.getName() +  
				" --storagectl \"" + DISK_CONTROLLER_NAME + "\" --medium \"" + diskPath + 
				"\" --port 0 --device 0 --type hdd");

		HypervisorUtils.runAndCheckProcess(attachMediaBuilder);
	}

	/**
	 * Returns true if controller with name 'SATA Controller' was defined, 
	 * or if a controller with this name already exists. Returns false if
	 * a controller of the type SATA is already defined.
	 * 
	 * Throws an exception if the controller could not be defined 
	 * for any other reason.
	 * 
	 * @param virtualMachine
	 * @return
	 * @throws Exception
	 */
	private boolean define(VirtualMachine virtualMachine)
			throws Exception {

		String memory = virtualMachine.getProperty(
				VirtualMachineConstants.MEMORY);
		String diskType = virtualMachine.getProperty(
				VirtualMachineConstants.DISK_TYPE);

		ProcessBuilder modifyVMBuilder = getProcessBuilder(
				"modifyvm " + virtualMachine.getName() + " --memory " + memory + 
				" --acpi on --boot1 disk --vrde off --pae on");
		HypervisorUtils.runAndCheckProcess(modifyVMBuilder);

		ProcessBuilder createControllerBuilder = getProcessBuilder(
				"storagectl " + virtualMachine.getName() + " --name \"" + DISK_CONTROLLER_NAME + "\" --add " + diskType);
		ExecutionResult createControllerResult = HypervisorUtils.runProcess(createControllerBuilder);

		String stdErr = createControllerResult.getStdErr().toString();

		if (createControllerResult.getReturnValue() != ExecutionResult.OK) {

			// Controller with this name already exists
			if (stdErr.contains(OBJECT_IN_USE)) {
				return true;
			}

			// VM already has a SATA controller with different name
			if (stdErr.contains(INVALID_ARG) || stdErr.contains(NS_INVALID_ARG)) {
				return false;
			}

			throw new Exception(stdErr);
		}

		return true;
	}

	private void register(VirtualMachine virtualMachine)
			throws Exception {

		String os = virtualMachine.getProperty(
				VirtualMachineConstants.OS_VERSION);

		ProcessBuilder createProcessBuilder = getProcessBuilder(
				"createvm --name " + virtualMachine.getName() + " --ostype \"" + os + "\" --register");
		ExecutionResult createExecutionResult = HypervisorUtils.runProcess(createProcessBuilder);

		String stdErr = createExecutionResult.getStdErr().toString();

		if (createExecutionResult.getReturnValue() != ExecutionResult.OK
				&& !stdErr.contains(FILE_ERROR)) {
			throw new Exception(stdErr);
		}
	}

	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.RUNNING) {
			return;
		}

		startVirtualMachine(virtualMachine);
		checkOSStarted(virtualMachine);
	}

	@Override
	public void mountSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
					throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception("Unable to mount shared folder. Machine is not started.");
		}

		if (guestPath == null) {
			throw new IllegalArgumentException("Shared folder [" + shareName + "] does not exist.");
		}

		String password = virtualMachine.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
		String user = virtualMachine.getProperty(VirtualMachineConstants.GUEST_USER);

		if (HypervisorUtils.isWindowsGuest(virtualMachine)) {

			HypervisorUtils.checkReturnValue(
					exec(virtualMachine, 
							"net use " + guestPath + " \\\\vboxsvr\\" + shareName));

		} else if (HypervisorUtils.isLinuxGuest(virtualMachine)) {

			File mountFile = File.createTempFile("mount-", ".sh");
			String mountScriptPathOnGuest = "/tmp/" + mountFile.getName();
			
			FileWriter mountFileWriter = new FileWriter(mountFile);
			mountFileWriter.write(
					"/bin/mkdir -p " + guestPath + "; " +
							"sudo mount -t vboxsf -o uid=" + user + ",gid=" + user + 
							" " + shareName + " " + guestPath + "; " +
							"rm " + mountScriptPathOnGuest);
			mountFileWriter.close();

			try {

				ProcessBuilder copyMountScriptBuilder = getProcessBuilder(
						"guestcontrol " + virtualMachine.getName() +  
						" copyto \"" + mountFile.getCanonicalPath() + "\" /tmp/" + 
						" --username " + user + " --password " + password);
				ExecutionResult executionResult = HypervisorUtils.runProcess(
						copyMountScriptBuilder);

				if (executionResult.getReturnValue() != ExecutionResult.OK && 
						!executionResult.getStdErr().toString().contains(VBoxStrategy.COPY_ERROR)) {
					//We are checking if COPY_ERROR was sent because of a bug where the copy is done
					//properly although it still spawns this error message with exit value different than 0
					throw new Exception(executionResult.getStdErr().toString());
				}

				HypervisorUtils.checkReturnValue(
						exec(virtualMachine, "/bin/bash " + mountScriptPathOnGuest));

			} finally {
				mountFile.delete();
			}

		} else {
			throw new Exception("Guest OS not supported");
		}
	}

	private void startVirtualMachine(VirtualMachine virtualMachine)
			throws IOException, Exception {

		String vmName = virtualMachine.getName();
		
		if (HypervisorUtils.isWindowsHost()) {
			
			File vbsFile = File.createTempFile("ourvirt-vboxstart-", ".vbs");
			FileOutputStream vbsFOS = new FileOutputStream(vbsFile);
			IOUtils.write(createStartVBSCommand(vmName), vbsFOS);
			vbsFOS.close();
			
			ProcessBuilder vbsProcessBuilder = new ProcessBuilder("wscript", 
					vbsFile.getAbsolutePath(), vmName);
			vbsProcessBuilder.directory(new File(System.getenv().get("VBOX_INSTALL_PATH")));
			ExecutionResult vbsExecutionResult = HypervisorUtils.runProcess(vbsProcessBuilder);
			
			int returnValue = vbsExecutionResult.getReturnValue();
			
			vbsFile.delete();
			
			if (returnValue != ExecutionResult.OK) {
				throw new Exception("Could not start VM");
			}
			
		} else {
			
			ProcessBuilder startProcessBuilder = getProcessBuilder(
					"startvm " + vmName + " --type headless");
			HypervisorUtils.runAndCheckProcess(startProcessBuilder);
		}
	}
	
	private static String createStartVBSCommand(String vmName) {
		return "Set objShell = WScript.CreateObject(\"WScript.Shell\")\n" +
				"objShell.Run \"VBoxHeadless -startvm " + vmName + "\", 0\n" + 
				"Set objShell = Nothing";
	}

	private void checkOSStarted(VirtualMachine virtualMachine)
			throws Exception {
		
		String startTimeout = virtualMachine.getProperty(VirtualMachineConstants.START_TIMEOUT);
		boolean checkTimeout = startTimeout != null;
		
		int remainingTries = 0;
		if (checkTimeout) {
			remainingTries = Integer.parseInt(startTimeout) / START_RECHECK_DELAY;
		}
		
		
		while (true) {
			
			Exception ex = null;
			
			try {

				if (HypervisorUtils.isLinuxGuest(virtualMachine)) {
					ExecutionResult executionResult = exec(
							virtualMachine, "/bin/echo check-started");
					HypervisorUtils.checkReturnValue(executionResult);
					break;
				} else if (HypervisorUtils.isWindowsGuest(virtualMachine)) {
					ExecutionResult executionResult = exec(
							virtualMachine, "Echo check-started");
					HypervisorUtils.checkReturnValue(executionResult);
					break;
				} else {
					ex = new Exception("Guest OS not supported");
				}
				
			} catch (Exception e) {
				if (checkTimeout && remainingTries-- == 0) {
					ex = new Exception("Virtual Machine OS was not started. Please check you credentials.");
				}
			}

			if (ex != null) {
				throw ex;
			}
			
			Thread.sleep(1000 * START_RECHECK_DELAY);
		}
	}

	@Override
	public void stop(VirtualMachine virtualMachine) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			return;
		}

		ProcessBuilder acpiPowerProcessBuilder = getProcessBuilder(
				"controlvm " + virtualMachine.getName() + " acpipowerbutton");
		HypervisorUtils.runAndCheckProcess(acpiPowerProcessBuilder);

		ProcessBuilder stopProcessBuilder = getProcessBuilder(
				"controlvm " + virtualMachine.getName() + " poweroff");
		HypervisorUtils.runAndCheckProcess(stopProcessBuilder);
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String command) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception("Unable to execute command. Machine is not started.");
		}

		String user = virtualMachine.getProperty(VirtualMachineConstants.GUEST_USER);
		String password = virtualMachine.getProperty(VirtualMachineConstants.GUEST_PASSWORD);

		String[] splittedCommand = command.split(" ");

		StringBuilder cmdBuilder = new StringBuilder("guestcontrol ");
		cmdBuilder.append("\"").append(virtualMachine.getName()).append("\"");
		cmdBuilder.append(" exec --image ");
		cmdBuilder.append("\"").append(splittedCommand[0]).append("\"");
		cmdBuilder.append(" --username ");
		cmdBuilder.append(user);
		cmdBuilder.append(" --password ");
		cmdBuilder.append(password);
		cmdBuilder.append(" --wait-exit");

		if (splittedCommand.length > 1) {
			cmdBuilder.append(" --");
			for (int i = 1; i < splittedCommand.length; i++) {
				cmdBuilder.append(" ").append(splittedCommand[i]);
			}
		}

		ProcessBuilder stopProcessBuilder = getProcessBuilder(cmdBuilder.toString());
		return HypervisorUtils.runProcess(stopProcessBuilder);
	}

	@Override
	public void takeSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception {
		String vMName = virtualMachine.getName();

		if (snapshotExists(virtualMachine, snapshotName)) {
			throw new SnapshotAlreadyExistsException("Snapshot [ " + snapshotName + " ] " +
					"already exists for virtual machine [ " + vMName + " ].");
		}

		ProcessBuilder takeSnapshotProcessBuilder = getProcessBuilder(
				"snapshot " + vMName + " take " + snapshotName);
		HypervisorUtils.runAndCheckProcess(takeSnapshotProcessBuilder);

	}

	@Override
	public void restoreSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception {
		if (status(virtualMachine).equals(VirtualMachineStatus.RUNNING)) {
			stop(virtualMachine);
		}

		String vMName = virtualMachine.getName();

		if (! snapshotExists(virtualMachine, snapshotName)) {
			throw new Exception("Snapshot [ " + snapshotName + " ] does not exist for " +
					"virtual machine [ " + virtualMachine.getName() + " ].");
		}

		ProcessBuilder restoreSnapshotProcessBuilder = getProcessBuilder(
				"snapshot " + vMName + " restore " + snapshotName);
		HypervisorUtils.runAndCheckProcess(restoreSnapshotProcessBuilder);
	}

	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {

		if (status(virtualMachine).equals(VirtualMachineStatus.RUNNING)) {
			stop(virtualMachine);
		}
		
		List<String> snapshots = listSnapshots(virtualMachine);
		for (String snapshot : snapshots) {
			ProcessBuilder destroySnapshotBuilder = getProcessBuilder(
					"snapshot " + virtualMachine.getName() + " delete " + snapshot);
			HypervisorUtils.runProcess(destroySnapshotBuilder);
		}
		
		ProcessBuilder destroyProcessBuilder = getProcessBuilder(
				"unregistervm " + virtualMachine.getName() + " --delete");
		HypervisorUtils.runProcess(destroyProcessBuilder);
		
		String imagePath = virtualMachine.getProperty(
				VirtualMachineConstants.DISK_IMAGE_PATH);
		
		if (imagePath != null) {
			String imageName = new File(imagePath).getName();
			
			ProcessBuilder destroyDiskBuilder = getProcessBuilder(
					"closemedium disk " + imageName);
			HypervisorUtils.runProcess(destroyDiskBuilder);
		}
		
		File virtualVmDir = new File(VIRTUALBOX_VMS + "/" + virtualMachine.getName());
		if (virtualVmDir.exists()) {
			virtualVmDir.delete();
		}
		
	}

	private static ProcessBuilder getProcessBuilder(
			String cmd) throws Exception {

		String vboxManageCmdLine = "VBoxManage --nologo " + cmd;
		ProcessBuilder processBuilder = null;

		String vBoxInstallPath = System.getenv().get("VBOX_INSTALL_PATH");

		if (HypervisorUtils.isWindowsHost()) {
			if (!new File(vBoxInstallPath + "\\VBoxManage.exe").exists()) {
				vBoxInstallPath = null;
			}
			processBuilder = new ProcessBuilder("cmd", 
					"/C " + vboxManageCmdLine);

		} else if (HypervisorUtils.isLinuxHost()) {

			List<String> matchList = HypervisorUtils.splitCmdLine(vboxManageCmdLine); 
			processBuilder =  new ProcessBuilder(matchList.toArray(new String[]{}));

		} else {
			throw new Exception("Host OS not supported");
		}

		if (vBoxInstallPath != null) {
			processBuilder.directory(new File(vBoxInstallPath));
		}

		return processBuilder;
	}

	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath) throws Exception {

		ProcessBuilder versionProcessBuilder = getProcessBuilder(
				"sharedfolder add \"" + virtualMachine.getName() + "\"" + 
						" --hostpath \"" + new File(hostPath).getAbsolutePath() + "\"" +
						" --name " + shareName); 
		HypervisorUtils.runAndCheckProcess(versionProcessBuilder);

	}
	
	@Override
	public void deleteSharedFolder(VirtualMachine virtualMachine, String shareName) throws Exception {
		
		ProcessBuilder versionProcessBuilder = getProcessBuilder(
				"sharedfolder remove \"" + virtualMachine.getName() + "\"" + 
						" --name " + shareName); 
		ExecutionResult deleteSharedFolderResult = HypervisorUtils.runProcess(versionProcessBuilder);

		if (deleteSharedFolderResult.getReturnValue() != ExecutionResult.OK) {
			// Shared folder already exists
			String stdErr = deleteSharedFolderResult.getStdErr().toString();
			if (!stdErr.contains(OBJECT_NOT_FOUND)) {
				throw new Exception(stdErr);
			}
		}
	}

	private List<String> list(boolean onlyRunning) throws Exception {

		ProcessBuilder listBuilder = getProcessBuilder(
				"list " + (onlyRunning ? "runningvms" : "vms"));
		ExecutionResult listResult = HypervisorUtils.runProcess(listBuilder);

		HypervisorUtils.checkReturnValue(listResult);

		List<String> vmsOutput = listResult.getStdOut();
		List<String> vms = new LinkedList<String>();

		for (String vmOutputted : vmsOutput) {
			vms.add(vmOutputted.substring(
					vmOutputted.indexOf("\"") + 1, vmOutputted.lastIndexOf("\"")));
		}

		return vms;
	}

	@Override
	public List<String> listVMs() throws Exception {
		return list(false);
	}

	@Override
	public boolean isSupported() {
		try {
			ProcessBuilder versionProcessBuilder = getProcessBuilder("--version");
			HypervisorUtils.runAndCheckProcess(versionProcessBuilder);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public VirtualMachineStatus status(VirtualMachine virtualMachine)
			throws Exception {

		if (list(true).contains(virtualMachine.getName())) {
			return VirtualMachineStatus.RUNNING;
		}

		if (list(false).contains(virtualMachine.getName())) {
			return VirtualMachineStatus.POWERED_OFF;
		}

		return VirtualMachineStatus.NOT_CREATED;
	}

	@Override
	public List<String> listSnapshots(VirtualMachine virtualMachine) throws Exception {

		ProcessBuilder startProcessBuilder = getProcessBuilder(
				"snapshot " + virtualMachine.getName() + " list --machinereadable");
		ExecutionResult runProcess = HypervisorUtils.runProcess(startProcessBuilder);
		
		List<String> snapshots = new LinkedList<String>();
		
		if (runProcess.getReturnValue() != 0) {
			return snapshots;
		}
		
		for (String line : runProcess.getStdOut()) {
			if (line.startsWith("SnapshotName")) {
				String snapshotWithQuotes = line.split("=")[1];
				snapshots.add(snapshotWithQuotes.substring(
						1, snapshotWithQuotes.length() - 1));
			}
		}
		
		return snapshots;
	}
	
	private boolean snapshotExists(VirtualMachine vm, String snapshot) throws Exception {
		return listSnapshots(vm).contains(snapshot);
	}

	@Override
	public List<String> listSharedFolders(VirtualMachine virtualMachine) throws Exception {

		ProcessBuilder startProcessBuilder = getProcessBuilder(
				"showvminfo " + virtualMachine.getName() + " list --machinereadable");
		ExecutionResult runProcess = HypervisorUtils.runProcess(startProcessBuilder);
		
		List<String> shares = new LinkedList<String>();
		
		if (runProcess.getReturnValue() != 0) {
			return shares;
		}
		
		for (String line : runProcess.getStdOut()) {
			if (line.startsWith("SharedFolderName")) {
				String shareNameWithQuotes = line.split("=")[1];
				shares.add(shareNameWithQuotes.substring(
						1, shareNameWithQuotes.length() - 1));
			}
		}
		
		return shares;
	}

	@Override
	public void unmountSharedFolder(VirtualMachine virtualMachine, String shareName, 
			String hostPath, String guestPath)
			throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception("Unable to unmount shared folder. Machine is not started.");
		}

		String password = virtualMachine.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
		String user = virtualMachine.getProperty(VirtualMachineConstants.GUEST_USER);

		if (HypervisorUtils.isWindowsGuest(virtualMachine)) {

			//TODO unmount for Windows guest

		} else if (HypervisorUtils.isLinuxGuest(virtualMachine)) {

			File unmountFile = File.createTempFile("unmount-", ".sh");
			String unmountScriptFilePath = "/tmp/" + unmountFile.getName();
			
			FileWriter unmountFileWriter = new FileWriter(unmountFile);
			unmountFileWriter.write(
					"sudo umount " + guestPath + "; " +
							"rm " + unmountScriptFilePath);
			unmountFileWriter.close();

			try {

				ProcessBuilder copyUnmountScriptBuilder = getProcessBuilder(
						"guestcontrol " + virtualMachine.getName() +  
						" copyto \"" + unmountFile.getCanonicalPath() + "\" /tmp/" + 
						" --username " + user + " --password " + password);
				ExecutionResult executionResult = HypervisorUtils.runProcess(
						copyUnmountScriptBuilder);

				if (executionResult.getReturnValue() != ExecutionResult.OK && 
						!executionResult.getStdErr().toString().contains(VBoxStrategy.COPY_ERROR)) {
					//We are checking if COPY_ERROR was sent because of a bug where the copy is done
					//properly although it still spawns this error message with exit value different than 0
					throw new Exception(executionResult.getStdErr().toString());
				}

				HypervisorUtils.checkReturnValue(
						exec(virtualMachine, "/bin/bash " + unmountScriptFilePath));

			} finally {
				unmountFile.delete();
			}

		} else {
			throw new Exception("Guest OS not supported");
		}

	}

	@Override
	public void prepareEnvironment(Map<String, String> props) throws Exception {
		String userName = props.get(VirtualMachineConstants.HOST_USER);
		if ( HypervisorUtils.isLinuxHost() ){
			LinuxUtils.appendNoPasswdToSudoers(userName, "/usr/bin/VBoxManage");
		} else if ( HypervisorUtils.isWindowsHost() ){
			//TODO verify if something is needed in order to VBox manage virtual machines properly
		} else {
			throw new Exception("Unable to prepare environment. OS not supported by VServer hypervisor.");
		}
	}

	@Override
	public void clone(String sourceDevice, String destDevice) throws Exception {
		ProcessBuilder startProcessBuilder = getProcessBuilder(
				"clonehd " + sourceDevice + " " + destDevice);
		HypervisorUtils.runAndCheckProcess(startProcessBuilder);
		
	}

	@Override
	public Object getProperty(VirtualMachine registeredVM, String propertyName)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setProperty(VirtualMachine registeredVM, String propertyName,
			Object propertyValue) throws Exception {
		 registeredVM.setProperty(propertyName, propertyValue); 
		
	}
	
	@Override
	public void reboot(VirtualMachine virtualMachine) throws Exception {
		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			return;
		}

		ProcessBuilder acpiPowerProcessBuilder = getProcessBuilder(
				"controlvm " + virtualMachine.getName() + " reset");
		HypervisorUtils.runAndCheckProcess(acpiPowerProcessBuilder);

		checkOSStarted(virtualMachine);
	}

	@Override
	public CPUStats getCPUStats(VirtualMachine virtualMachine) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<DiskStats> getDiskStats(VirtualMachine registeredVM) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String attachDevice(VirtualMachine registeredVM, String devName) {
		return null;
	}

	@Override
	public void detachDevice(VirtualMachine registeredVM, String hostDevicePath)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getConsoleOuput(VirtualMachine registeredVM) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public NetworkStats getNetworkStats(VirtualMachine registeredVM) throws Exception {
		String ifName = registeredVM.getProperty(VirtualMachineConstants.BRIDGED_INTERFACE);
		return HypervisorUtils.getNetworkStats(registeredVM, ifName);
	}
}

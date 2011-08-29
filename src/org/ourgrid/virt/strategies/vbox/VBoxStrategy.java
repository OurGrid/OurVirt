package org.ourgrid.virt.strategies.vbox;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

public class VBoxStrategy implements HypervisorStrategy {

	private static final String FILE_ERROR = "VBOX_E_FILE_ERROR";
	private static final String OBJECT_IN_USE = "VBOX_E_OBJECT_IN_USE";
	private static final String INVALID_ARG = "E_INVALIDARG";
	private static final String E_FAIL = "E_FAIL";
	
	private static final String DISK_CONTROLLER_NAME = "Disk Controller";
	
	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {
		
		register(virtualMachine);
		boolean definedSata = define(virtualMachine);
		
		if (!definedSata) {
			return;
		}
		
		String imagePath = virtualMachine.getProperty("diskimagepath");
		
		boolean attached = attachDisk(virtualMachine, imagePath);
		
		if (attached) {
			return;
		}
		
		long randomId = Math.abs(new Random().nextLong());

		String diskName = "/tmp/disk-" + randomId + ".vdi";
		clone(virtualMachine, imagePath, diskName);
		attachDisk(virtualMachine, diskName);

		virtualMachine.setProperty("tmpdisk", diskName);
	}

	private void clone(VirtualMachine virtualMachine, String sourceDisk, String targetDisk)
			throws Exception {
		
		
		ProcessBuilder cloneHDBuilder = getProcessBuilder(
				"clonehd \"" + sourceDisk + "\" " + targetDisk + "");
		HypervisorUtils.runAndCheckProcess(cloneHDBuilder);
	}

	/**
	 * Returns true if disk was successfully attached. 
	 * Returns false if the disk is already attached.
	 * Throws an exception if the disk could not be attached
	 * for any other reason.
	 * 
	 * @param virtualMachine
	 * @param diskPath
	 * @return
	 * @throws Exception
	 */
	private boolean attachDisk(VirtualMachine virtualMachine, String diskPath) 
			throws Exception {
		
		if (!new File(diskPath).exists()) {
			throw new FileNotFoundException("The image file does not exist: " + diskPath);
		}
		
		ProcessBuilder attachMediaBuilder = getProcessBuilder(
				"storageattach " + virtualMachine.getName() +  
				" --storagectl \"" + DISK_CONTROLLER_NAME + "\" --medium \"" + diskPath + 
				"\" --port 0 --device 0 --type hdd");
		
		ExecutionResult runProcess = HypervisorUtils.runProcess(attachMediaBuilder);
		String stdErr = runProcess.getStdErr().toString();
		
		if (runProcess.getReturnValue() != ExecutionResult.OK) {
			
			if (stdErr.contains(E_FAIL)) {
				return false;
			}
			
			if (stdErr.contains(E_FAIL)) {
				return false;
			}
			
			throw new Exception(stdErr);
		}
		return true;
		
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
		
		String memory = virtualMachine.getProperty("memory");
		String diskType = virtualMachine.getProperty("disktype");
		
		ProcessBuilder modifyVMBuilder = getProcessBuilder(
				"modifyvm " + virtualMachine.getName() + " --memory " + memory + 
				" --acpi on --boot1 disk");
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
			if (stdErr.contains(INVALID_ARG)) {
				return false;
			}
			
			throw new Exception(stdErr);
		}
		
		return true;
	}

	private void register(VirtualMachine virtualMachine)
			throws Exception {
		
		String os = virtualMachine.getProperty("osversion");
		
		ProcessBuilder createProcessBuilder = getProcessBuilder(
				"createvm --name " + virtualMachine.getName() + " --register --ostype " + os);
		ExecutionResult createExecutionResult = HypervisorUtils.runProcess(createProcessBuilder);
		
		String stdErr = createExecutionResult.getStdErr().toString();
		
		if (createExecutionResult.getReturnValue() != ExecutionResult.OK
				&& !stdErr.contains(FILE_ERROR)) {
			throw new Exception(stdErr);
		}
		
	}
	
	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {
		
		if (HypervisorUtils.isWindowsHost()) {
			ProcessBuilder vbsProcessBuilder = new ProcessBuilder("wscript", 
					new File(getClass().getResource("start-vm.vbs").getFile()).getCanonicalPath(), virtualMachine.getName());
			vbsProcessBuilder.directory(new File(
					System.getenv().get("VBOX_INSTALL_PATH")));
			ExecutionResult vbsExecutionResult = 
					HypervisorUtils.runProcess(vbsProcessBuilder);
			
			if (vbsExecutionResult.getReturnValue() != 0) {
				throw new Exception("Could not start VM");
			}
			
		} else {
			ProcessBuilder startProcessBuilder = getProcessBuilder(
					"startvm " + virtualMachine.getName() + " --type headless");
			HypervisorUtils.runAndCheckProcess(startProcessBuilder, "successfully started");
		}
		
		
		while (true) {
			try {
				ExecutionResult executionResult = exec(
						virtualMachine, "/bin/echo check-started");
				HypervisorUtils.checkExpectedMessage("check-started", 
						executionResult.getStdOut());
				break;
			} catch (Exception e) {}
			
			Thread.sleep(1000 * 10);
		}
	}

	@Override
	public void stop(VirtualMachine virtualMachine) throws Exception {
		
		ProcessBuilder acpiPowerProcessBuilder = getProcessBuilder(
				"controlvm " + virtualMachine.getName() + " acpipowerbutton");
		HypervisorUtils.runAndCheckProcess(acpiPowerProcessBuilder);
		
		ProcessBuilder stopProcessBuilder = getProcessBuilder(
				"controlvm " + virtualMachine.getName() + " poweroff");
		HypervisorUtils.runAndCheckProcess(stopProcessBuilder);
	}
	
	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String command) throws Exception {
		
		String user = virtualMachine.getConfiguration().get("user");
		String password = virtualMachine.getConfiguration().get("password");
		
		String[] splittedCommand = command.split(" ");
		
		StringBuilder cmdBuilder = new StringBuilder("guestcontrol ");
		cmdBuilder.append("\"").append(virtualMachine.getName()).append("\"");
		cmdBuilder.append(" exec --image ");
		cmdBuilder.append("\"").append(splittedCommand[0]).append("\"");
		cmdBuilder.append(" --username ");
		cmdBuilder.append(user);
		cmdBuilder.append(" --password ");
		cmdBuilder.append(password);
		cmdBuilder.append(" --wait-exit --wait-stdout");
		
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
	public void takeSnapshot(String vMName, String snapshotName)
			throws Exception {
		ProcessBuilder takeSnapshotProcessBuilder = getProcessBuilder(
				"snapshot " + vMName + " take " + snapshotName);
		HypervisorUtils.runAndCheckProcess(takeSnapshotProcessBuilder);
	}
	
	@Override
	public void restoreSnapshot(String vMName, String snapshotName)
			throws Exception {
		ProcessBuilder restoreSnapshotProcessBuilder = getProcessBuilder(
				"snapshot " + vMName + " restore " + snapshotName);
		HypervisorUtils.runAndCheckProcess(restoreSnapshotProcessBuilder);
	}
	
	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {
		ProcessBuilder destroyProcessBuilder = getProcessBuilder(
				"unregistervm " + virtualMachine.getName() + " --delete");
		HypervisorUtils.runAndCheckProcess(destroyProcessBuilder);
		
		String tmpDisk = virtualMachine.getConfiguration().get("tmpdisk");
		if (tmpDisk != null) {
			File tmpDiskFile = new File(tmpDisk);
			if (tmpDiskFile.exists()) {
				tmpDiskFile.delete();
			}
		}
	}
	
	private static ProcessBuilder getProcessBuilder(String cmd) {
		
		String vboxManageCmdLine = "VBoxManage --nologo " + cmd;
		ProcessBuilder processBuilder = null;
		
		if (HypervisorUtils.isWindowsHost()) {
			processBuilder = new ProcessBuilder("cmd", 
					"/C " + vboxManageCmdLine);
		} else {
			processBuilder =  new ProcessBuilder(vboxManageCmdLine);
		}
		
		processBuilder.directory(new File(
				System.getenv().get("VBOX_INSTALL_PATH")));
		
		return processBuilder;
	}
	
	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String hostPath, String guestPath) throws Exception {
		
	}

	private List<String> list(boolean onlyRunning) throws Exception {
		
		ProcessBuilder listBuilder = getProcessBuilder(
				"list " + (onlyRunning ? "runningvms" : "vms"));
		ExecutionResult listResult = HypervisorUtils.runProcess(listBuilder);
		
		if (listResult.getReturnValue() != ExecutionResult.OK) {
			throw new Exception(listResult.getStdErr().toString());
		}
		
		List<String> vmsOutput = listResult.getStdOut();
		List<String> vms = new LinkedList<String>();
		
		for (String vmOutputted : vmsOutput) {
			vms.add(vmOutputted.substring(
					vmOutputted.indexOf("\"") + 1, vmOutputted.lastIndexOf("\"")));
		}
		
		return vms;
	}
	
	@Override
	public List<String> list() throws Exception {
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
		
		return VirtualMachineStatus.NOT_REGISTERED;
	}

}

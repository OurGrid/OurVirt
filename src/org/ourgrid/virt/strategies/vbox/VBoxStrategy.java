package org.ourgrid.virt.strategies.vbox;

import java.io.File;
import java.util.Random;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

public class VBoxStrategy implements HypervisorStrategy {

	private static final String VM_ALREADY_CREATED = "VBOX_E_FILE_ERROR";
	private static final String CONTROLLER_EXISTS = "VBOX_E_OBJECT_IN_USE";
	private static final String DISK_INVALID_ARG = "E_INVALIDARG";
	private static final String DISK_ATTACH_FAIL = "E_FAIL";
	
	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {
		
		register(virtualMachine);
		boolean definedSata = define(virtualMachine);
		
		if (!definedSata) {
			return;
		}
		
		boolean attached = attachDisk(virtualMachine, virtualMachine.getImagePath());
		
		if (attached) {
			return;
		}
		
		long randomId = Math.abs(new Random().nextLong());

		String diskName = "/tmp/disk-" + randomId + ".vdi";
		clone(virtualMachine, diskName);
		attachDisk(virtualMachine, diskName);

		virtualMachine.setProperty("tmp-disk", diskName);
	}

	private void clone(VirtualMachine virtualMachine, String diskName)
			throws Exception {
		ProcessBuilder cloneHDBuilder = getProcessBuilder(
				"clonehd \"" + virtualMachine.getImagePath() + "\" " + diskName + "");
		HypervisorUtils.runAndCheckProcess(cloneHDBuilder);
	}

	private boolean attachDisk(VirtualMachine virtualMachine, String diskName) 
			throws Exception {
		ProcessBuilder attachMediaBuilder = getProcessBuilder(
				"storageattach " + virtualMachine.getName() +  
				" --storagectl \"SATA Controller\" --medium \"" + diskName + 
				"\" --port 0 --device 0 --type hdd");
		
		ExecutionResult runProcess = HypervisorUtils.runProcess(attachMediaBuilder);
		String stdErr = runProcess.getStdErr().toString();
		
		if (runProcess.getReturnValue() == 0) {
			return true;
		} else {
			if (stdErr.contains(DISK_INVALID_ARG) || stdErr.contains(DISK_ATTACH_FAIL)) {
				return false;
			}
		}
		
		throw new Exception(stdErr);
	}

	private boolean define(VirtualMachine virtualMachine)
			throws Exception {
		
		String memory = virtualMachine.getProperty("memory");
		
		ProcessBuilder modifyVMBuilder = getProcessBuilder(
				"modifyvm " + virtualMachine.getName() + " --memory " + memory + 
				" --acpi on --boot1 disk");
		HypervisorUtils.runAndCheckProcess(modifyVMBuilder);
		
		ProcessBuilder createControllerBuilder = getProcessBuilder(
				"storagectl " + virtualMachine.getName() + " --name \"SATA Controller\" --add sata");
		ExecutionResult createControllerResult = HypervisorUtils.runProcess(createControllerBuilder);
		
		String stdErr = createControllerResult.getStdErr().toString();
		
		if (createControllerResult.getReturnValue() != ExecutionResult.OK) {
			
			// Controller exists
			if (stdErr.contains(CONTROLLER_EXISTS)) {
				return true;
			}
			
			// VM already has a SATA controller with different name
			if (stdErr.contains(DISK_INVALID_ARG)) {
				return false;
			}
			
			throw new Exception(stdErr);
		}
		
		return true;
	}

	private void register(VirtualMachine virtualMachine)
			throws Exception {
		
		String os = virtualMachine.getProperty("os");
		
		ProcessBuilder createProcessBuilder = getProcessBuilder(
				"createvm --name " + virtualMachine.getName() + " --register --ostype " + os);
		ExecutionResult createExecutionResult = HypervisorUtils.runProcess(createProcessBuilder);
		
		String stdErr = createExecutionResult.getStdErr().toString();
		
		if (createExecutionResult.getReturnValue() != ExecutionResult.OK
				&& !stdErr.contains(VM_ALREADY_CREATED)) {
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
				throw new Exception();
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
		
		String tmpDisk = virtualMachine.getConfiguration().get("tmp-disk");
		if (tmpDisk != null) {
			new File(tmpDisk).delete();
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
		// TODO Auto-generated method stub
		
	}

}

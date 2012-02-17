package org.ourgrid.virt.strategies.vserver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;
import org.ourgrid.virt.strategies.LinuxUtils;

public class VServerStrategy implements HypervisorStrategy {

	private static final int VSERVER_STOPPED_EXIT_VALUE = 3;
	private static final int START_RECHECK_DELAY = 10;
	private static final int CONTEXT_RANGE_INITIAL= 2;
	private static final int CONTEXT_RANGE = 49151 - CONTEXT_RANGE_INITIAL;
	
	private static final String SNAPSHOT_PREFIX = "OVSS_";
	private static final String SHAREDFOLDER_PREFIX = "ovsf_";
	
	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {

		if (HypervisorUtils.isLinuxGuest(virtualMachine)) {

			boolean vmExists = checkWhetherMachineExists(virtualMachine);

			if (vmExists) {
				return;
			}

			register(virtualMachine);

		} else {
			throw new Exception("Guest OS not supported");
		}
	}

	private void register(VirtualMachine virtualMachine) throws Exception {

		String vmName = virtualMachine.getName();
		String imagePath = virtualMachine
				.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String osVersion = virtualMachine
				.getProperty(VirtualMachineConstants.OS_VERSION);

		ProcessBuilder buildVMBuilder = getVServerProcessBuilder(vmName
				+ " build -m template -- -t " + imagePath + " -d "
				+ osVersion.toLowerCase());
		HypervisorUtils.runAndCheckProcess(buildVMBuilder);
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
			String shareName, String hostPath, String guestPath) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception(
					"Unable to mount shared folder. Machine is not started.");
		}

		String vmName = virtualMachine.getName();

		String guestPathOnVM = "/etc/vservers/.defaults/vdirbase/" + vmName
				+ "/" + guestPath;

		ExecutionResult createSharedFolderDir = HypervisorUtils
				.runProcess(getProcessBuilder("/usr/bin/sudo /usr/sbin/vnamespace -e "
						+ vmName + " -- mkdir -p " + guestPathOnVM));
		HypervisorUtils.checkReturnValue(createSharedFolderDir);

		ExecutionResult mountProcess = HypervisorUtils
				.runProcess(getProcessBuilder("/usr/bin/sudo /usr/sbin/vnamespace -e "
						+ vmName
						+ " -- mount -o " + SHAREDFOLDER_PREFIX + shareName + " --bind "
						+ hostPath
						+ " "
						+ guestPathOnVM));
		HypervisorUtils.checkReturnValue(mountProcess);
	}

	private void startVirtualMachine(VirtualMachine virtualMachine)
			throws IOException, Exception {

		int contextIdx = (int) (Math.random() * (double) CONTEXT_RANGE);
		
		ProcessBuilder setContextBuilder = getProcessBuilder(
				"/usr/bin/sudo /bin/echo " + (CONTEXT_RANGE_INITIAL + contextIdx) + 
				" > /etc/vservers/" + virtualMachine.getName() + "/context");
		HypervisorUtils.runProcess(setContextBuilder);
		
		ProcessBuilder startProcessBuilder = getVMProcessBuilder(
				virtualMachine, "start");
		HypervisorUtils.runAndCheckProcess(startProcessBuilder);
	}

	private void checkOSStarted(VirtualMachine virtualMachine) throws Exception {

		String startTimeout = virtualMachine
				.getProperty(VirtualMachineConstants.START_TIMEOUT);
		boolean checkTimeout = startTimeout != null;

		int remainingTries = 0;
		if (checkTimeout) {
			remainingTries = Integer.parseInt(startTimeout)
					/ START_RECHECK_DELAY;
		}

		while (true) {
			try {

				ExecutionResult executionResult = exec(virtualMachine,
						"/bin/echo check-started");
				HypervisorUtils.checkReturnValue(executionResult);

				break;
			} catch (Exception e) {
				if (checkTimeout && remainingTries-- == 0) {
					throw e;
				}
			}

			Thread.sleep(1000 * START_RECHECK_DELAY);
		}
	}

	@Override
	public void stop(VirtualMachine virtualMachine) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			return;
		}

		ProcessBuilder stopProcessBuilder = getVMProcessBuilder(virtualMachine,
				"stop");
		HypervisorUtils.runAndCheckProcess(stopProcessBuilder);
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String command)
			throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception(
					"Unable to execute command. Machine is not started.");
		}

		ProcessBuilder execProcessBuilder = new ProcessBuilder("/usr/bin/sudo",
				"/usr/sbin/vserver", virtualMachine.getName(), "exec",
				"/bin/sh", "-c", command);
		return HypervisorUtils.runProcess(execProcessBuilder);
	}

	@Override
	public void takeSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.RUNNING) {
			throw new Exception("Unable to take snapshot. Machine is started.");
		}

		String vmName = virtualMachine.getName();

		String actualSnapshotName = SNAPSHOT_PREFIX + vmName + "_" + snapshotName;

		ExecutionResult takeSnapshotProcess = HypervisorUtils
				.runProcess(getVServerProcessBuilder(actualSnapshotName
						+ " build --force -m clone -- --source /etc/vservers/.defaults/vdirbase/"
						+ vmName));
		HypervisorUtils.checkReturnValue(takeSnapshotProcess);

	}

	private boolean snapshotExists(VirtualMachine virtualMachine,
			String snapshotName) {
		return new File("/etc/vservers/" + SNAPSHOT_PREFIX + virtualMachine
				+ "_" + snapshotName).exists();
	}
	
	private boolean checkWhetherMachineExists(VirtualMachine virtualMachine) {
		String vmName = virtualMachine.getName();
		return new File("/etc/vservers/" + vmName).exists();
	}

	@Override
	public void restoreSnapshot(VirtualMachine virtualMachine,
			String snapshotName) throws Exception {

		String vmName = virtualMachine.getName();

		if (!snapshotExists(virtualMachine, snapshotName)) {
			throw new Exception("Snapshot [ " + snapshotName
					+ " ] does not exist " + "for virtual machine [ " + vmName
					+ " ].");
		}

		String actualSnapshotName = SNAPSHOT_PREFIX + vmName + "_" + snapshotName;

		if (status(virtualMachine) == VirtualMachineStatus.RUNNING) {
			stop(virtualMachine);
		}

		destroy(virtualMachine);

		ExecutionResult restoreSnapshotProcess = HypervisorUtils
				.runProcess(getVMProcessBuilder(virtualMachine,
						"build --force -m clone -- --source /etc/vservers/.defaults/vdirbase/"
								+ actualSnapshotName));
		HypervisorUtils.checkReturnValue(restoreSnapshotProcess);
	}

	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.RUNNING) {
			stop(virtualMachine);
		}

		String vmName = virtualMachine.getName();

		List<String> sharedFolderHostPaths = getSharedFoldersHosts(virtualMachine);

		for (String hostPath : sharedFolderHostPaths) {
			if (hostPath == null) {
				throw new IllegalArgumentException(
						"Shared folder with hostpath [" + hostPath
								+ "] does not exist.");
			}
			if (new File(hostPath).exists()) {
				unmountSharedFolderByHostPath(virtualMachine, hostPath);
			}
		}

		ProcessBuilder destroyProcessBuilder = new ProcessBuilder("/bin/sh",
				"-c", "/bin/echo  \"y\" | /usr/bin/sudo vserver " + vmName
						+ " delete ");
		HypervisorUtils.runAndCheckProcess(destroyProcessBuilder);
		
	}

	private static List<String> getSharedFoldersHosts(
			VirtualMachine virtualMachine) throws Exception {

		String vmName = virtualMachine.getName();

		List<String> sharedFoldersHostPaths = new ArrayList<String>();

		ExecutionResult getSharedFoldersProcess = HypervisorUtils
				.runProcess(getProcessBuilder("/usr/bin/sudo /bin/mount -l"));

		List<String> getSharedFoldersProcessOutPut = getSharedFoldersProcess
				.getStdOut();

		for (String mountedFolder : getSharedFoldersProcessOutPut) {
			if (mountedFolder.contains(vmName)) {
				String currentHostPath = "/etc/vservers/"
						+ mountedFolder
								.substring(mountedFolder.indexOf(vmName))
								.split(" ")[0]
								.replace(vmName, vmName + "/vdir");
				sharedFoldersHostPaths.add(currentHostPath);
			}
		}

		return sharedFoldersHostPaths;
	}

	private static ProcessBuilder getVMProcessBuilder(
			VirtualMachine virtualMachine, String cmd) throws Exception {
		return getVServerProcessBuilder(virtualMachine.getName() + " " + cmd);
	}

	private static ProcessBuilder getVServerProcessBuilder(String cmd)
			throws Exception {
		String vserverCmdLine = "/usr/bin/sudo /usr/sbin/vserver " + cmd;
		return getProcessBuilder(vserverCmdLine);
	}

	private static ProcessBuilder getProcessBuilder(String cmd)
			throws Exception {

		ProcessBuilder processBuilder = null;

		if (HypervisorUtils.isLinuxHost()) {

			List<String> matchList = HypervisorUtils.splitCmdLine(cmd);
			processBuilder = new ProcessBuilder(
					matchList.toArray(new String[] {}));

		} else {
			throw new Exception("Host OS not supported");
		}

		return processBuilder;
	}

	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
			throws Exception {

		String vmName = virtualMachine.getName();
		String guestPathOnVM = "/etc/vservers/.defaults/vdirbase/" + vmName
				+ "/" + guestPath;

		if (status(virtualMachine) == VirtualMachineStatus.RUNNING) {

			ExecutionResult createSharedFolderDir = HypervisorUtils
					.runProcess(getProcessBuilder("/usr/bin/sudo /usr/sbin/vnamespace -e "
							+ vmName + " -- mkdir -p " + guestPathOnVM));
			HypervisorUtils.checkReturnValue(createSharedFolderDir);

		} else {
			ExecutionResult createSharedFolderDir = HypervisorUtils
					.runProcess(getProcessBuilder("/usr/bin/sudo /bin/mkdir -p "
							+ guestPathOnVM));
			HypervisorUtils.checkReturnValue(createSharedFolderDir);
		}

	}
	
	@Override
	public void deleteSharedFolder(VirtualMachine virtualMachine, String shareName) throws Exception {
		
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

		boolean vmExists = checkWhetherMachineExists(virtualMachine);

		if (!vmExists) {
			return VirtualMachineStatus.NOT_CREATED;
		}

		ProcessBuilder statusProcess = getVMProcessBuilder(virtualMachine,
				"status");
		ExecutionResult statusInfo = HypervisorUtils.runProcess(statusProcess);

		if (statusInfo.getReturnValue() != ExecutionResult.OK) {
			if (statusInfo.getReturnValue() != VSERVER_STOPPED_EXIT_VALUE) {
				throw new Exception(statusInfo.getStdErr().toString());
			}
		}

		String status = statusInfo.getStdOut().get(0);
		if (status.contains("running")) {
			return VirtualMachineStatus.RUNNING;
		} else if (status.contains("stopped")) {
			return VirtualMachineStatus.POWERED_OFF;
		}
		return VirtualMachineStatus.NOT_REGISTERED;
	}

	@Override
	public List<String> listSnapshots(VirtualMachine virtualMachine)
			throws Exception {

		
		File vserverBase = new File("/etc/vservers/");
		if (!vserverBase.exists()) {
			throw new Exception("VServer base directory does not exist.");
		}
		List<String> snapshotsList = new ArrayList<String>();
		
		for (String vmImage : vserverBase.list()) {
			if (vmImage.startsWith(SNAPSHOT_PREFIX + virtualMachine + "_")) {
				String snapshot = vmImage.substring(vmImage.lastIndexOf('_') + 1);
				snapshotsList.add(snapshot);
			}
		}
		
		return snapshotsList;
	}

	@Override
	public List<String> listSharedFolders(VirtualMachine virtualMachine)
			throws Exception {
		
		
		ExecutionResult listMounts = HypervisorUtils
				.runProcess(getProcessBuilder("/usr/bin/sudo /usr/sbin/vnamespace -e "
						+ virtualMachine.getName() + " -- mount -l "));
		
		List<String> listOutput = listMounts.getStdOut();
		List<String> shareNames = new LinkedList<String>();
		
		for (String line : listOutput) {
			String optionsP = line.split(" +")[5];
			String options = optionsP.substring(1, optionsP.length() - 1);
			String[] optionsA = options.split(",");
			
			for (String option : optionsA) {
				if (option.startsWith(SHAREDFOLDER_PREFIX)) {
					String shareName = option.substring(SHAREDFOLDER_PREFIX.length());
					shareNames.add(shareName);
				}
			}
		}
		
		return shareNames;
	}

	@Override
	public void unmountSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception(
					"Unable to unmount shared folder. Machine is not started.");
		}

		unmountSharedFolderByHostPath(virtualMachine, hostPath);
	}

	private void unmountSharedFolderByHostPath(VirtualMachine virtualMachine,
			String hostPath) throws Exception {
		ProcessBuilder unmountSFProcess = getProcessBuilder("/usr/bin/sudo /bin/umount "
				+ hostPath);
		ExecutionResult process = HypervisorUtils.runProcess(unmountSFProcess);

		// If exit value is 1 or 2 when mount point is not mounted
		if (process.getReturnValue() != ExecutionResult.OK
				&& process.getReturnValue() != 2
				&& process.getReturnValue() != 1) {
			throw new Exception(process.getStdErr().toString());
		}
	}

	@Override
	public void prepareEnvironment(String userName) throws Exception {
		if (HypervisorUtils.isLinuxHost()) {
			LinuxUtils
					.appendLineToSudoersFile(
							userName,
							"/usr/sbin/vnamespace,/usr/sbin/vserver,/bin/mkdir,/bin/echo,/bin/mount,/bin/umount");
		} else {
			throw new Exception(
					"Unable to prepare environment. OS not supported by VServer hypervisor.");
		}
	}

	@Override
	public void clone(String sourceDevice, String destDevice) throws Exception {
		ProcessBuilder cloneProcess = getVServerProcessBuilder(
				destDevice + " build --force -m clone -- --source /etc/vservers/.defaults/vdirbase/" + sourceDevice);
		HypervisorUtils.runAndCheckProcess(cloneProcess);
	}

}

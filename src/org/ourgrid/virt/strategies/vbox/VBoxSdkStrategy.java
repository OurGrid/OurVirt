package org.ourgrid.virt.strategies.vbox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.exception.SnapshotAlreadyExistsException;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;
import org.virtualbox_4_2.AccessMode;
import org.virtualbox_4_2.CPUPropertyType;
import org.virtualbox_4_2.CleanupMode;
import org.virtualbox_4_2.DeviceType;
import org.virtualbox_4_2.IConsole;
import org.virtualbox_4_2.IMachine;
import org.virtualbox_4_2.IMedium;
import org.virtualbox_4_2.INetworkAdapter;
import org.virtualbox_4_2.IProgress;
import org.virtualbox_4_2.ISession;
import org.virtualbox_4_2.ISharedFolder;
import org.virtualbox_4_2.IVirtualBox;
import org.virtualbox_4_2.LockType;
import org.virtualbox_4_2.MachineState;
import org.virtualbox_4_2.NetworkAttachmentType;
import org.virtualbox_4_2.SessionState;
import org.virtualbox_4_2.StorageBus;
import org.virtualbox_4_2.VBoxException;
import org.virtualbox_4_2.VirtualBoxManager;


public class VBoxSdkStrategy implements HypervisorStrategy {

	private static final String VM_BLANK_FLAGS = "forceOverwrite=1,UUID=00000000-0000-0000-0000-000000000000";
	private static final String IP_GUEST_PROPERTY = "/VirtualBox/GuestInfo/Net/0/V4/IP";
	private static final String SESSION = "VBOX_SESSION";
	private static final String DISK_CONTROLLER_NAME = "Disk Controller";
	private final int START_RECHECK_DELAY = 10;
	private final VirtualBoxManager vboxm = VirtualBoxManager.createInstance(
			System.getProperty("vbox.home"));
	private IVirtualBox vbox;
	
	public VBoxSdkStrategy(){
		this.vbox = this.vboxm.getVBox();
	}

	private ISession getSession(VirtualMachine virtualMachine) {
		ISession session = virtualMachine.getProperty(SESSION);
		if (session == null) {
			session = vboxm.getSessionObject();
			virtualMachine.setProperty(SESSION, session);
		}
		return session;
	}
	
	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {

		VirtualMachineStatus status = status(virtualMachine);
		
		if (status.equals(VirtualMachineStatus.RUNNING)) {
			return;
		}
		
		register(virtualMachine);
		define(virtualMachine);

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

		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		ISession session = getSession(virtualMachine);
		machine.lockMachine(session, LockType.Shared);
		try {
			IMachine mutable = session.getMachine();
			IMedium medium = vbox.openMedium(diskPath,
					DeviceType.HardDisk, AccessMode.ReadWrite, true);
			mutable.attachDevice(DISK_CONTROLLER_NAME, 0, 0, DeviceType.HardDisk,
					medium);
			mutable.saveSettings();
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
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
	private boolean define(VirtualMachine virtualMachine) throws Exception {

		String memory = virtualMachine
				.getProperty(VirtualMachineConstants.MEMORY);
		String diskType = virtualMachine
				.getProperty(VirtualMachineConstants.DISK_TYPE);
		String networkType = virtualMachine
				.getProperty(VirtualMachineConstants.NETWORK_TYPE);
		String networkAdapterName = virtualMachine
				.getProperty(VirtualMachineConstants.NETWORK_ADAPTER_NAME);
		String mac = virtualMachine.getProperty(VirtualMachineConstants.MAC);
		String bridgedInterface = virtualMachine.getProperty(
				VirtualMachineConstants.BRIDGED_INTERFACE);
		String paeEnabled = virtualMachine.getProperty(VirtualMachineConstants.PAE_ENABLED);
		boolean pae = false;
		if (paeEnabled != null) {
			pae = Boolean.valueOf(paeEnabled);
		}

		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		ISession session = getSession(virtualMachine);
		machine.lockMachine(session, LockType.Shared);
		try {
			IMachine mutable = session.getMachine();
			mutable.setMemorySize(Long.valueOf(memory));
			INetworkAdapter networkAdapter = mutable.getNetworkAdapter(0L);
			networkAdapter.setAttachmentType(getNetworkAttachmentType(networkType));
			if (networkAdapterName != null) {
				networkAdapter.setHostOnlyInterface(networkAdapterName);
			}
			if (mac != null) {
				networkAdapter.setMACAddress(mac);
			}
			if (bridgedInterface != null) {
				networkAdapter.setBridgedInterface(bridgedInterface);
			}
			
			try {
				mutable.addStorageController(DISK_CONTROLLER_NAME,
					getStorageBus(diskType));
			} catch (VBoxException e) {
				// Do nothing for the controller already exists
			}
			mutable.setCPUProperty(CPUPropertyType.PAE, pae);
			mutable.saveSettings();
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}

		return true;
	}

	private NetworkAttachmentType getNetworkAttachmentType(String networkType) {
		if ("internal".equals(networkType.toLowerCase())) {
			return NetworkAttachmentType.Internal;
		} else if ("host-only".equals(networkType.toLowerCase())) {
			return NetworkAttachmentType.HostOnly;
		} else if ("nat".equals(networkType.toLowerCase())) {
			return NetworkAttachmentType.NAT;
		} else if ("bridged".equals(networkType.toLowerCase())) {
			return NetworkAttachmentType.Bridged;
		}
		return null;
	}

	private StorageBus getStorageBus(String diskType) {
		if ("sata".equals(diskType.toLowerCase())) {
			return StorageBus.SATA;
		}
		return null;
	}

	private void register(VirtualMachine virtualMachine)
			throws Exception {

		String os = virtualMachine.getProperty(VirtualMachineConstants.OS_VERSION);
		IMachine machine = vbox.createMachine("", virtualMachine.getName(),
				new LinkedList<String>(), os, VM_BLANK_FLAGS);
		machine.saveSettings();

		this.vbox.registerMachine(machine);
	}

	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {
		if (status(virtualMachine) == VirtualMachineStatus.RUNNING) {
			return;
		}

		startVirtualMachine(virtualMachine);
		checkOSStarted(virtualMachine);
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
					createSSHClient(virtualMachine).disconnect();
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
	
	private SSHClient createSSHClient(VirtualMachine virtualMachine) throws Exception {
		String ip = virtualMachine.getProperty(VirtualMachineConstants.IP);
		if (ip == null) {
			throw new Exception("Could not acquire IP.");
		}
		SSHClient ssh = new SSHClient();
		ssh.addHostKeyVerifier(createBlankHostKeyVerifier());
		ssh.connect(ip);
		return ssh;
	}

	private HostKeyVerifier createBlankHostKeyVerifier() {
		return new HostKeyVerifier() {
			@Override
			public boolean verify(String arg0, int arg1, PublicKey arg2) {
				return true;
			}
		};
	}

	@Override
	public void mountSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
					throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception("Unable to mount shared folder. Machine is not started.");
		}

		if (guestPath == null) {
			throw new IllegalArgumentException(
					"Shared folder [" + shareName + "] does not exist.");
		}

		if (HypervisorUtils.isLinuxGuest(virtualMachine)) {
			String user = virtualMachine
					.getProperty(VirtualMachineConstants.GUEST_USER);
			String mountCommandLine = "/bin/mkdir -p " + guestPath + "; "
					+ "sudo mount -t vboxsf -o uid=" + user + ",gid=" + user
					+ " " + shareName + " " + guestPath + "; ";
			ExecutionResult executionResult = exec(virtualMachine, mountCommandLine);
			
			if (executionResult.getReturnValue() != 0) {
				throw new Exception("Could not mount the shared folder.");
			}
		} else {
			throw new Exception("Guest OS not supported");
		}
	}

	private void startVirtualMachine(VirtualMachine virtualMachine)
			throws IOException, Exception {
		
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		ISession session = getSession(virtualMachine);
		IProgress prog = machine.launchVMProcess(session, "headless", "");
		try {
			prog.waitForCompletion(-1);
			if (prog.getResultCode() != 0) {
				throw new Exception("Could not start VM. " + prog.getErrorInfo().getText());
			}
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
	}

	private void unlock(ISession session) {
		if (session.getState().equals(SessionState.Locked)) {
			session.unlockMachine();
		}
	}
	
	@Override
	public void stop(VirtualMachine virtualMachine) throws Exception {
		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			return;
		}

		ISession session = getSession(virtualMachine);
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		machine.lockMachine(session, LockType.Shared);
		try {
			IConsole console = session.getConsole();
			IProgress shutDownProg = console.powerDown();
			shutDownProg.waitForCompletion(-1);
			if (shutDownProg.getResultCode() != 0) {
				throw new Exception("Cannot stop VM. " + shutDownProg.getErrorInfo().getText());
			}
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine,
			String commandLine) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception(
					"Unable to execute command. Machine is not started.");
		}

		SSHClient sshClient = createSSHClient(virtualMachine);
		String user = virtualMachine.getProperty(VirtualMachineConstants.GUEST_USER);
	    String password = virtualMachine.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
		sshClient.authPassword(user, password);
	    
		Session session = sshClient.startSession();
		Command command = session.exec(commandLine);

		command.join();

		Integer exitStatus = command.getExitStatus();
		List<String> stdOut = IOUtils.readLines(command.getInputStream());
		List<String> stdErr = IOUtils.readLines(command.getErrorStream());

		ExecutionResult executionResult = new ExecutionResult();
		executionResult.setReturnValue(exitStatus);
		executionResult.setStdErr(stdErr);
		executionResult.setStdOut(stdOut);

		session.close();
		sshClient.disconnect();

		return executionResult;
	}

	@Override
	public void takeSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception {
		
		String vMName = virtualMachine.getName();
		
		if (snapshotExists(virtualMachine, snapshotName)) {
			throw new SnapshotAlreadyExistsException("Snapshot [ " + snapshotName + " ] " +
					"already exists for virtual machine [ " + vMName + " ].");
		}
		
		ISession session = getSession(virtualMachine);
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		machine.lockMachine(session, LockType.Shared);
		try {
			IConsole console = session.getConsole();
			IProgress takeSnapshotProg = console.takeSnapshot(snapshotName, "");
			takeSnapshotProg.waitForCompletion(-1);
			if (takeSnapshotProg.getResultCode() != 0) {
				throw new Exception("Cannot take snapshot from VM. " + 
						takeSnapshotProg.getErrorInfo().getText());
			}
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
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
		
		IMachine machine = this.vbox.findMachine(vMName);
		ISession session = getSession(virtualMachine);
		machine.lockMachine(session, LockType.Shared);
		try {
			IConsole console = session.getConsole();
			IProgress restoreSnapshotProg = console.restoreSnapshot(machine
					.findSnapshot(snapshotName));
			restoreSnapshotProg.waitForCompletion(-1);
			if (restoreSnapshotProg.getResultCode() != 0) {
				throw new Exception("Cannot restore snapshot from VM. " + 
						restoreSnapshotProg.getErrorInfo().getText());
			}
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
	}

	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {

		if (status(virtualMachine).equals(VirtualMachineStatus.RUNNING)) {
			stop(virtualMachine);
		}
		
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		ISession session = getSession(virtualMachine);
		machine.lockMachine(session, LockType.Write);
		try {
			IConsole console = session.getConsole();
			if (machine.getCurrentSnapshot() != null) {
				IProgress deleteSnapshotProg = console.deleteSnapshot(machine.getCurrentSnapshot().getId());
				deleteSnapshotProg.waitForCompletion(-1);
				if (deleteSnapshotProg.getResultCode() != 0) {
					throw new Exception("Cannot delete snapshot from VM. " + 
							deleteSnapshotProg.getErrorInfo().getText());
				}
			}
			IMachine mutable = session.getMachine();
			try {
				mutable.removeStorageController(DISK_CONTROLLER_NAME);
			} catch (VBoxException e) {
				// Do nothing if the controller does not exist
			}
			mutable.saveSettings();
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
		machine.delete(machine.unregister(CleanupMode.Full));
	}


	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath) throws Exception {
		
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		ISession session = getSession(virtualMachine);
		machine.lockMachine(session, LockType.Shared);
		try {
			IMachine mutable = session.getMachine();
			mutable.createSharedFolder(shareName, hostPath, true, false);
			mutable.saveSettings();
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
	}
	
	@Override
	public void deleteSharedFolder(VirtualMachine virtualMachine,
			String shareName) throws Exception {
		
		List<String> sharedFolders = listSharedFolders(virtualMachine);
		
		if (!sharedFolders.contains(shareName)) {
			return;
		}
		
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		ISession session = getSession(virtualMachine);
		machine.lockMachine(session, LockType.Shared);
		try {
			IMachine mutable = session.getMachine();
			mutable.removeSharedFolder(shareName);
			mutable.saveSettings();
		} catch (Exception e) {
			throw e;
		} finally {
			unlock(session);
		}
	}

	private List<String> list(boolean onlyRunning) throws Exception {

		List<IMachine> vmsOutput = this.vbox.getMachines();
		List<String> vms = new LinkedList<String>();

		for (IMachine vmOutputted : vmsOutput) {
			if(!onlyRunning || vmOutputted.getState().equals(MachineState.Running)){
				vms.add(vmOutputted.getName());
			}
		}

		return vms;
	}

	@Override
	public List<String> listVMs() throws Exception {
		return list(false);
	}

	@Override
	public boolean isSupported() {
		return true;
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
		return new LinkedList<String>();
	}
	
	private boolean snapshotExists(VirtualMachine vm, String snapshot) throws Exception {
		IMachine machine = this.vbox.findMachine(vm.getName());
		return machine.getCurrentSnapshot() != null;
	}

	@Override
	public List<String> listSharedFolders(VirtualMachine virtualMachine) throws Exception {

		List<String> shares = new LinkedList<String>();
		
		IMachine machine = this.vbox.findMachine(virtualMachine.getName());
		List<ISharedFolder> sharedFolders = machine.getSharedFolders();
		
		for (ISharedFolder folder : sharedFolders) {
			shares.add(folder.getName());
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

		if (HypervisorUtils.isLinuxGuest(virtualMachine)) {
			String unmountCommandLine = "sudo umount " + guestPath + ";";
			ExecutionResult executionResult = exec(virtualMachine, unmountCommandLine);
			
			if (executionResult.getReturnValue() != 0) {
				throw new Exception("Could not unmount the shared folder.");
			}
			
		} else {
			throw new Exception("Guest OS not supported");
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
			String vBoxManageDir = System.getenv().get("VBOX_HOME");
			vboxManageCmdLine = vBoxManageDir + "/" + vboxManageCmdLine;
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
	public void clone(String sourceDevice, String destDevice) throws Exception {
		ProcessBuilder startProcessBuilder = getProcessBuilder(
				"clonehd " + sourceDevice + " " + destDevice);
		HypervisorUtils.runAndCheckProcess(startProcessBuilder);
	}

	@Override
	public Object getProperty(VirtualMachine registeredVM, String propertyName)
			throws Exception {
		Object property = registeredVM.getProperty(propertyName);
		if (property != null) {
			return property;
		}
		if (propertyName.equals(VirtualMachineConstants.IP)) {
			IMachine machine = vbox.findMachine(registeredVM.getName());
			String ip = machine.getGuestPropertyValue(IP_GUEST_PROPERTY);
			return ip;
		}
		return null;
	}

	@Override
	public void setProperty(VirtualMachine registeredVM, String propertyName,
			Object propertyValue) throws Exception {
		 registeredVM.setProperty(propertyName, propertyValue); 
	}

	@Override
	public void prepareEnvironment(Map<String, String> props) throws Exception {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void reboot(VirtualMachine virtualMachine) throws Exception {
		// TODO call actual hypervisor reboot method, if existent
		stop(virtualMachine);
		start(virtualMachine);
	}
	
}

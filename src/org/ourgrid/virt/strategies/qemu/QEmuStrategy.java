package org.ourgrid.virt.strategies.qemu;

import java.io.File;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

public class QEmuStrategy implements HypervisorStrategy {

	private static final String RESTORE_SNAPSHOT = "RESTORE_SNAPSHOT";

	public static final String QEMU_LOCATION = "QEMU_LOCATION";
	
	private static final String PROCESS = "QEMU_LOCATION";
	private static final String DESTROYED = "DESTROYED";
	private static final int START_RECHECK_DELAY = 10;
	private static final String SHARED_FOLDERS = "SHARED_FOLDERS";

	private String qemuLocation;
	
	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {
		String hda = virtualMachine.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String memory = virtualMachine.getProperty(VirtualMachineConstants.MEMORY);
		
		String snapshot = virtualMachine.getProperty(RESTORE_SNAPSHOT);
		
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("-net nic")
			.append(" -net user");
		
		String netType = virtualMachine.getProperty(VirtualMachineConstants.NETWORK_TYPE);
		if (netType != null && netType.equals("host-only")) {
			Integer sshPort = new Random().nextInt(10000) + 10000;
			strBuilder.append(",restrict=yes,hostfwd=tcp:localhost:").append(sshPort).append("-:22");
			virtualMachine.setProperty(VirtualMachineConstants.IP, "localhost");
			virtualMachine.setProperty(VirtualMachineConstants.SSH_PORT, sshPort);
		}
			
		strBuilder.append(" -m ").append(memory);
		
		strBuilder.append(" -hda ");
		
		if (snapshot != null && new File(snapshot).exists()) {
			strBuilder.append(snapshot).append(" -snapshot");
		} else {
			strBuilder.append(hda);
		}
		
		ProcessBuilder builder = getSystemProcessBuilder(strBuilder.toString());
		virtualMachine.setProperty(PROCESS, builder.start());
		
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
		String ip = (String) getProperty(virtualMachine, VirtualMachineConstants.IP);
		Integer sshPort = (Integer) getProperty(virtualMachine, VirtualMachineConstants.SSH_PORT);
		
		if (ip == null) {
			throw new Exception("Could not acquire IP.");
		}
		
		if (sshPort == null) {
			sshPort = VirtualMachineConstants.DEFAULT_SSH_PORT;
		}
		
		SSHClient ssh = new SSHClient();
		ssh.addHostKeyVerifier(createBlankHostKeyVerifier());
		ssh.connect(ip, sshPort);
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
	public void stop(VirtualMachine virtualMachine) throws Exception {
		Process p = virtualMachine.getProperty(PROCESS);
		p.destroy();
		virtualMachine.setProperty(DESTROYED, true);
	}

	@Override
	public VirtualMachineStatus status(VirtualMachine virtualMachine)
			throws Exception {
		Process p = virtualMachine.getProperty(PROCESS);
		if (p == null) {
			return VirtualMachineStatus.NOT_CREATED;
		}
		if (virtualMachine.getProperty(DESTROYED) != null) {
			return VirtualMachineStatus.POWERED_OFF;
		}
		return VirtualMachineStatus.RUNNING;
	}

	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
			throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	public void takeSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception {
		String hda = virtualMachine.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		ProcessBuilder builder = getImgProcessBuilder(" create -f qcow2 -b " + hda + " " + snapshotName);
		builder.start().waitFor();
	}

	@Override
	public void restoreSnapshot(VirtualMachine virtualMachine,
			String snapshotName) throws Exception {
		virtualMachine.setProperty(RESTORE_SNAPSHOT, snapshotName);
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String commandLine)
			throws Exception {
		
		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception(
					"Unable to execute command. Machine is not started.");
		}

		SSHClient sshClient = createAuthSSHClient(virtualMachine);
	    
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

		syncSharedFolders(virtualMachine);
		
		return executionResult;
	}

	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {
	
	}

	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {
		stop(virtualMachine);
	}

	@Override
	public List<String> listVMs() throws Exception {
		return null;
	}

	@Override
	public List<String> listSnapshots(VirtualMachine virtualMachine)
			throws Exception {
		return null;
	}

	@Override
	public List<String> listSharedFolders(VirtualMachine virtualMachine)
			throws Exception {
		return null;
	}

	@Override
	public boolean isSupported() {
		return true;
	}

	@Override
	public void mountSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
			throws Exception {
		SSHClient sshClient = createAuthSSHClient(virtualMachine);
		SCPFileTransfer fileTransfer = sshClient.newSCPFileTransfer();
		fileTransfer.upload(new FileSystemFile(hostPath), guestPath);
		
		Map<String, SharedFolder> sharedFolders = virtualMachine.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null) {
			sharedFolders = new HashMap<String, SharedFolder>();
			virtualMachine.setProperty(SHARED_FOLDERS, sharedFolders);
		}
		sharedFolders.put(shareName, new SharedFolder(shareName, hostPath, guestPath));
	}

	private void syncSharedFolders(VirtualMachine virtualMachine) throws Exception {
		Map<String, SharedFolder> sharedFolders = virtualMachine.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null || sharedFolders.isEmpty()) {
			return;
		}
		
		SSHClient sshClient = createAuthSSHClient(virtualMachine);
		for (SharedFolder sharedFolder : sharedFolders.values()) {
			SCPFileTransfer fileTransfer = sshClient.newSCPFileTransfer();
			fileTransfer.download(sharedFolder.guestPath, new FileSystemFile(sharedFolder.hostPath));
		}
	}
	
	private SSHClient createAuthSSHClient(VirtualMachine virtualMachine)
			throws Exception, UserAuthException, TransportException {
		SSHClient sshClient = createSSHClient(virtualMachine);
		String user = virtualMachine.getProperty(VirtualMachineConstants.GUEST_USER);
	    String password = virtualMachine.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
		sshClient.authPassword(user, password);
		return sshClient;
	}

	@Override
	public void unmountSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
			throws Exception {
		deleteSharedFolder(virtualMachine, shareName);
	}

	@Override
	public void deleteSharedFolder(VirtualMachine registeredVM, String shareName)
			throws Exception {
		Map<String, SharedFolder> sharedFolders = registeredVM.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null || sharedFolders.isEmpty()) {
			return;
		}
		sharedFolders.remove(shareName);
	}

	@Override
	public void clone(String sourceDevice, String destDevice) throws Exception {
		FileUtils.copyFile(new File(sourceDevice), new File(destDevice));
	}

	@Override
	public Object getProperty(VirtualMachine registeredVM, String propertyName)
			throws Exception {
		return registeredVM.getProperty(propertyName);
	}

	@Override
	public void setProperty(VirtualMachine registeredVM, String propertyName,
			Object propertyValue) throws Exception {
		registeredVM.setProperty(propertyName, propertyValue);
	}

	@Override
	public void prepareEnvironment(Map<String, String> props) throws Exception {
		this.qemuLocation = props.get(QEMU_LOCATION);
	}
	
	private ProcessBuilder getSystemProcessBuilder(String cmd) throws Exception {
		return getProcessBuilder("qemu-system-i386w --nographic " + cmd);
	}
	
	private ProcessBuilder getImgProcessBuilder(String cmd) throws Exception {
		return getProcessBuilder("qemu-img " + cmd);
	}
	
	private ProcessBuilder getProcessBuilder(String cmd) throws Exception {

		ProcessBuilder processBuilder = null;

		if (HypervisorUtils.isWindowsHost()) {
			processBuilder = new ProcessBuilder("cmd", "/C " + cmd);
		} else if (HypervisorUtils.isLinuxHost()) {
			processBuilder =  new ProcessBuilder(cmd);

		} else {
			throw new Exception("Host OS not supported");
		}

		if (qemuLocation != null) {
			processBuilder.directory(new File(qemuLocation));
		}

		return processBuilder;
	}

	private static class SharedFolder {
		
		@SuppressWarnings("unused")
		String name;
		String hostPath;
		String guestPath;
		
		public SharedFolder(String name, String hostPath, String guestPath) {
			this.name = name;
			this.hostPath = hostPath;
			this.guestPath = guestPath;
		}
	}
	
}

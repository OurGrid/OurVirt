package org.ourgrid.virt.strategies.qemu;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

public class QEmuStrategy implements HypervisorStrategy {

	private static final Logger LOGGER = Logger.getLogger(QEmuStrategy.class);
	
	private static final String RESTORE_SNAPSHOT = "RESTORE_SNAPSHOT";
	private static final String PROCESS = "QEMU_LOCATION";
	private static final String DESTROYED = "DESTROYED";
	private static final String QMP_PORT = "QMP_PORT";
	private static final String CURRENT_SNAPSHOT = "current";
	
	private static final int START_RECHECK_DELAY = 10;
	private static final String SHARED_FOLDERS = "SHARED_FOLDERS";

	private String qemuLocation = System.getProperty("qemu.home");
		
	@Override
	public void start(final VirtualMachine virtualMachine) throws Exception {
		String hda = virtualMachine.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String memory = virtualMachine.getProperty(VirtualMachineConstants.MEMORY);
		
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("-net nic")
			.append(" -net user");
		
		String netType = virtualMachine.getProperty(VirtualMachineConstants.NETWORK_TYPE);
		if (netType != null && netType.equals("host-only")) {
			Integer sshPort = randomPort();
			strBuilder.append(",restrict=yes,hostfwd=tcp:127.0.0.1:").append(sshPort).append("-:22");
			virtualMachine.setProperty(VirtualMachineConstants.IP, "localhost");
			virtualMachine.setProperty(VirtualMachineConstants.SSH_PORT, sshPort);
		}
			
		strBuilder.append(" -m ").append(memory);
		strBuilder.append(" -nodefconfig");
		Integer qmpPort = randomPort();
		strBuilder.append(" -qmp tcp:127.0.0.1:").append(qmpPort).append(",server,nowait,nodelay");
		virtualMachine.setProperty(QMP_PORT, qmpPort);
		
		String snapshot = virtualMachine.getProperty(RESTORE_SNAPSHOT);
		String snapshotLocation = getSnapshotLocation(virtualMachine, CURRENT_SNAPSHOT);
		
		if (snapshot != null && new File(snapshotLocation).exists()) {
			strBuilder.append(" -hda \"").append(snapshotLocation).append("\"");
		} else {
			strBuilder.append(" -hda \"").append(hda).append("\"");
		}
		
		ProcessBuilder builder = getSystemProcessBuilder(strBuilder.toString());
		virtualMachine.setProperty(PROCESS, builder.start());
		
		Runnable runnable = new Runnable() {
		    public void run() {
		        try {
					stop(virtualMachine);
				} catch (Exception e) {
					// Best effort
				}
		    }
		};
		Runtime.getRuntime().addShutdownHook(new Thread(runnable));
		
		checkOSStarted(virtualMachine);
	}

	private static Integer randomPort() {
		Integer sshPort = new Random().nextInt(10000) + 10000;
		return sshPort;
	}

	private void checkOSStarted(VirtualMachine virtualMachine)
			throws Exception {
		
		Process process = virtualMachine.getProperty(PROCESS);
		try {
			process.exitValue();
			
			List<String> stderr = IOUtils.readLines(process.getInputStream());
			List<String> stdout = IOUtils.readLines(process.getErrorStream());
			
			throw new Exception("Virtual Machine was forcibly terminated. " +
					"Stdout [" + stdout + "] stderr [" + stderr + "]");
			
		} catch (IllegalThreadStateException e) {
			// Should proceed, process hasn't terminated yet
		}
		
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
		Socket s = new Socket("127.0.0.1", (Integer) virtualMachine.getProperty(QMP_PORT));
		PrintStream ps = new PrintStream(s.getOutputStream());
		ps.println("{\"execute\":\"qmp_capabilities\"}");
		ps.println("{\"execute\":\"quit\"}");
		ps.flush();
		s.close();
		
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
		String snapshotFile = getSnapshotLocation(virtualMachine, snapshotName);
		ProcessBuilder snapBuilder = getImgProcessBuilder(" create -f qcow2 -b " + hda + " " + snapshotFile);
		snapBuilder.start().waitFor();
		
		restoreSnapshot(virtualMachine, snapshotName);
	}

	private String getSnapshotLocation(VirtualMachine virtualMachine, String snapshotName) {
		String hda = virtualMachine.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String hdaLocation = new File(hda).getParent();
		String snapshotFile = hdaLocation + "/" + snapshotName + "_" + virtualMachine.getName() + ".img";
		return snapshotFile;
	}

	@Override
	public void restoreSnapshot(VirtualMachine virtualMachine,
			String snapshotName) throws Exception {
		
		String snapshotFile = getSnapshotLocation(virtualMachine, snapshotName);
		
		String currentSnapshotFile = getSnapshotLocation(virtualMachine, CURRENT_SNAPSHOT);
		ProcessBuilder currSnapBuilder = getImgProcessBuilder(" create -f qcow2 -b " + snapshotFile + " " + currentSnapshotFile);
		currSnapBuilder.start().waitFor();
		
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
		
		syncSharedFoldersIn(virtualMachine, sshClient);
	    
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

		syncSharedFoldersOut(virtualMachine, sshClient);
		
		sshClient.disconnect();
		
		return executionResult;
	}

	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {
	
	}

	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {
	
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
		
		new File(hostPath).mkdirs();
		
		SSHClient sshClient = createAuthSSHClient(virtualMachine);
		
		Session session = sshClient.startSession();
		session.exec("mkdir -p " + guestPath).join();
		session.close();
		
		syncSharedFolderIn(hostPath, guestPath, sshClient);
		sshClient.close();
		
		Map<String, SharedFolder> sharedFolders = virtualMachine.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null) {
			sharedFolders = new HashMap<String, SharedFolder>();
			virtualMachine.setProperty(SHARED_FOLDERS, sharedFolders);
		}
		sharedFolders.put(shareName, new SharedFolder(shareName, hostPath, guestPath));
	}

	private void syncSharedFolderIn(String hostPath, String guestPath,
			SSHClient sshClient) throws IOException {
		for (File child : new File(hostPath).listFiles()) {
			SCPFileTransfer fileTransfer = sshClient.newSCPFileTransfer();
			fileTransfer.upload(new FileSystemFile(child), guestPath);
		}
	}

	private void syncSharedFoldersIn(VirtualMachine virtualMachine, SSHClient sshClient) throws Exception {
		Map<String, SharedFolder> sharedFolders = virtualMachine.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null || sharedFolders.isEmpty()) {
			return;
		}
		for (SharedFolder sharedFolder : sharedFolders.values()) {
			syncSharedFolderIn(sharedFolder.hostPath, sharedFolder.guestPath, sshClient);
		}
	}
	
	private void syncSharedFoldersOut(VirtualMachine virtualMachine, SSHClient sshClient) throws Exception {
		Map<String, SharedFolder> sharedFolders = virtualMachine.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null || sharedFolders.isEmpty()) {
			return;
		}
		
		for (SharedFolder sharedFolder : sharedFolders.values()) {
			SFTPClient sftpClient = sshClient.newSFTPClient();
			List<RemoteResourceInfo> ls = sftpClient.ls(sharedFolder.guestPath);
			for (RemoteResourceInfo remoteResourceInfo : ls) {
				SCPFileTransfer fileTransfer = sshClient.newSCPFileTransfer();
				fileTransfer.download(remoteResourceInfo.getPath(), new FileSystemFile(sharedFolder.hostPath));
			}
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
	}
	
	private ProcessBuilder getSystemProcessBuilder(String cmd) throws Exception {
		return getProcessBuilder("qemu-system-i386 --nographic " + cmd);
	}
	
	private ProcessBuilder getImgProcessBuilder(String cmd) throws Exception {
		return getProcessBuilder("qemu-img " + cmd);
	}
	
	private ProcessBuilder getProcessBuilder(String cmd) throws Exception {

		LOGGER.debug("Command line: " + cmd);
		
		ProcessBuilder processBuilder = null;

		if (HypervisorUtils.isWindowsHost()) {
			processBuilder = new ProcessBuilder("cmd", "/C " + cmd);
		} else if (HypervisorUtils.isLinuxHost()) {
			List<String> matchList = HypervisorUtils.splitCmdLine("./" + cmd); 
			processBuilder =  new ProcessBuilder(matchList.toArray(new String[]{}));
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

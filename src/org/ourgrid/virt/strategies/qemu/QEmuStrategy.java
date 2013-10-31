package org.ourgrid.virt.strategies.qemu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

import org.alfresco.jlan.server.NetworkServer;
import org.alfresco.jlan.server.ServerListener;
import org.alfresco.jlan.smb.server.SMBServer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.ourgrid.virt.model.CPUStats;
import org.ourgrid.virt.model.DiskStats;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.SharedFolder;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class QEmuStrategy implements HypervisorStrategy {

	private static final int AUTHSSH_RETRIES = 5;
	private static final int RANDOM_PORT_RETRIES = 5;

	private static final int QMP_CAPABILITY_WAIT =3000;

	private static final Logger LOGGER = Logger.getLogger(QEmuStrategy.class);

	private static final String RESTORE_SNAPSHOT = "RESTORE_SNAPSHOT";
	private static final String PROCESS = "PROCESS";
	private static final String POWERED_OFF = "POWERED_OFF";
	private static final String QMP_PORT = "QMP_PORT";
	private static final String CIFS_SERVER = "CIFS_SERVER";
	private static final String HDA_FILE = "HDA_FILE";
	private static final String SHARED_FOLDERS = "SHARED_FOLDERS";
	private static final String CURRENT_DEVICE_IDX = "CURRENT_DEVICE_IDX";
	private static final String DEVICES_ATTACHED = "DEVICES_ATTACHED";
	
	private static final String CIFS_DEVICE = "10.0.2.100";
	private static final String CIFS_PORT_GUEST = "9999";
	private static final String CURRENT_SNAPSHOT = "current";

	private static final int START_RECHECK_DELAY = 10;
	private static final int DEF_CONNECTION_TIMEOUT = 180;

	private String qemuLocation = System.getProperty("qemu.home");
	
	public enum QmpCmd {
		STOP("quit"),
		REBOOT("system_reset"),
		CAPABILITIES("qmp_capabilities"),
		BLOCKSTATS("query-blockstats");
		
		private String cmd;
		QmpCmd(String cmd) {
			this.cmd = cmd;
		}
		
		public String getCmd() {
			return cmd;
		}
	}
	
	public enum QmpJsonTag {
		RETURN("return"),
		STATS("stats"),
		DEVICE("device"),
		READ_BYTES("rd_bytes"),
		READ_OPS("rd_operations"),
		READ_TOTAL_TIME_NS("rd_total_time_ns"),
		WRITE_BYTES("wr_bytes"),
		WRITE_OPS("wr_operations"),
		WRITE_TOTAL_TIME_NS("wr_total_time_ns"); 
		
		private String tag;
		QmpJsonTag(String tag) {
			this.tag = tag;
		}
		
		public String getTag() {
			return tag;
		}
	
	};

	@Override
	public void start(final VirtualMachine virtualMachine) throws Exception {
		String hda = virtualMachine
				.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String memory = virtualMachine
				.getProperty(VirtualMachineConstants.MEMORY);

		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("-net nic");

		String netType = virtualMachine
				.getProperty(VirtualMachineConstants.NETWORK_TYPE);
		if (netType != null) {
			if (netType.equals("nat")) {
				configureNat(virtualMachine, strBuilder);
			} else if (netType.equals("host-only")) {
				configureHostOnly(virtualMachine, strBuilder);
			} else if (netType.equals("bridged")) {
				configureBridged(virtualMachine, strBuilder);
			} else if (netType.equals("internal")) {
				configureInternal(virtualMachine, strBuilder);
			}
		}
		
		if (virtualMachine.getProperty(SHARED_FOLDERS) != null) {
			Integer cifsPort = randomPort();
			strBuilder.append(",guestfwd=tcp:").append(CIFS_DEVICE).append(":")
					.append(CIFS_PORT_GUEST).append("-tcp:127.0.0.1:")
					.append(cifsPort);
			createSMBServer(virtualMachine, cifsPort);
		}

		strBuilder.append(" -m ").append(memory);
		strBuilder.append(" -nodefconfig");
		Integer qmpPort = randomPort();
		strBuilder.append(" -qmp tcp:127.0.0.1:").append(qmpPort)
				.append(",server,nowait,nodelay");
		virtualMachine.setProperty(QMP_PORT, qmpPort);
		
		String useUSBHub = virtualMachine
				.getProperty(VirtualMachineConstants.USE_USB_HUB);
		if (useUSBHub != null && Boolean.parseBoolean(useUSBHub)) {
			strBuilder.append(" -device piix3-usb-uhci,id=usb");
		}
		
		String useMonitor = virtualMachine
				.getProperty(VirtualMachineConstants.USE_MONITOR);
		if (useMonitor != null && Boolean.parseBoolean(useMonitor)) {
			File monitorFile = getMonitorFile(virtualMachine);
			strBuilder.append(" -chardev socket,id=charmonitor,path=" + monitorFile.getAbsolutePath() 
					+ ",server,nowait -mon chardev=charmonitor,id=monitor,mode=control");
		}
		
		File pidFile = getPidFile(virtualMachine);
		strBuilder.append(" -pidfile \"").append(pidFile.getAbsolutePath()).append("\"");
		
		String useConsoleOutputFile = virtualMachine
				.getProperty(VirtualMachineConstants.USE_CONSOLE_OUTPUT_FILE);
		if (useConsoleOutputFile != null && Boolean.parseBoolean(useConsoleOutputFile)) {
			File serialFile = getConsoleOutputFile(virtualMachine);
			strBuilder.append(" -serial file:").append(serialFile.getAbsolutePath());
		}
		
		String snapshot = virtualMachine.getProperty(RESTORE_SNAPSHOT);
		String snapshotLocation = getSnapshotLocation(virtualMachine,
				CURRENT_SNAPSHOT);

		if (snapshot != null && new File(snapshotLocation).exists()) {
			strBuilder.append(" -hda \"").append(snapshotLocation).append("\"");
			virtualMachine.setProperty(HDA_FILE, snapshotLocation);
		} else {
			strBuilder.append(" -hda \"").append(hda).append("\"");
			virtualMachine.setProperty(HDA_FILE, hda);
		}

		
		if (checkKVM()) {
			strBuilder.append(" -enable-kvm"); 
		}

		try {
			kill(virtualMachine);
		} catch (Exception e) {
			// Best effort
		}
		
		final ProcessBuilder builder = getSystemProcessBuilder(strBuilder.toString());
		
		final LinkedBlockingQueue<Object> lbq = new LinkedBlockingQueue<Object>();
		
		SMBServer cifsServer = virtualMachine.getProperty(CIFS_SERVER);
		if (cifsServer != null) {
			cifsServer.addServerListener(new ServerListener() {
				@Override
				public void serverStatusEvent(NetworkServer server, int event) {
					if (event == ServerListener.ServerActive) {
						try {
							startQEmuProcess(virtualMachine, builder);
							lbq.add(Void.class);
						} catch (Exception e) {
							lbq.add(e);
						}
					} else if (event == ServerListener.ServerError) {
						lbq.add(server.getException());
					}
				}
			});
			
			cifsServer.startServer();
		} else {
			startQEmuProcess(virtualMachine, builder);
			lbq.add(Void.class);
		}
		
		Object flag = lbq.take();
		if (flag instanceof Exception) {
			throw (Exception)flag;
		}
		
		checkOSStarted(virtualMachine);
	}

	private void configureInternal(VirtualMachine virtualMachine,
			StringBuilder strBuilder) {
		// TODO Auto-generated method stub
		
	}
	
	private void execAndWait(String cmd) throws Exception {
		getProcessBuilder(cmd).start().waitFor();
	}
	
	private void configureBridged(VirtualMachine virtualMachine,
			StringBuilder strBuilder) throws Exception {
		String tapIf = "tap-" + virtualMachine.getName();
		String brName = virtualMachine.getProperty(
				VirtualMachineConstants.BRIDGED_INTERFACE);
		
		String userName = System.getProperty("user.name");
		execAndWait("qemu-bridge mktap " + tapIf + " " + userName);
		execAndWait("qemu-bridge addtobr " + brName + " " + tapIf);
		execAndWait("qemu-bridge ifup " + tapIf);

		String macAddr = virtualMachine.getProperty(VirtualMachineConstants.MAC);
		strBuilder.append(",macaddr=").append(macAddr)
			.append(" -net tap,ifname=").append(tapIf).append(",script=no,downscript=no");
	}
	
	private void unconfigureBridged(VirtualMachine virtualMachine) throws Exception {
		String tapIf = "tap-" + virtualMachine.getName();
		String brName = virtualMachine.getProperty(
				VirtualMachineConstants.BRIDGED_INTERFACE);
		
		execAndWait("qemu-bridge ifdown " + tapIf);
		execAndWait("qemu-bridge delfrombr " + brName + " " + tapIf);
		execAndWait("qemu-bridge deltap " + tapIf);
	}

	private void configureHostOnly(VirtualMachine virtualMachine,
			StringBuilder strBuilder) {
		Integer sshPort = randomPort();
		strBuilder.append(" -net user");
		strBuilder.append(",restrict=yes,hostfwd=tcp:127.0.0.1:").append(
				sshPort).append("-:22");
		virtualMachine.setProperty(VirtualMachineConstants.IP, "localhost");
		virtualMachine.setProperty(VirtualMachineConstants.SSH_PORT,
				sshPort);
	}

	private void configureNat(VirtualMachine virtualMachine,
			StringBuilder strBuilder) {
		strBuilder.append(" -net user");
	}

	private void startQEmuProcess(final VirtualMachine virtualMachine,
			ProcessBuilder builder) throws IOException, 
			SecurityException, NoSuchFieldException, 
			IllegalArgumentException, IllegalAccessException {
		virtualMachine.setProperty(PROCESS, builder.start());
		virtualMachine.setProperty(POWERED_OFF, null);

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
	}

	private boolean checkKVM() {
		if (!HypervisorUtils.isLinuxHost()) {
			LOGGER.debug("It is not a linux host");
			return false;
		}
		if (!new File("/dev/kvm").exists()) {
			LOGGER.debug("/dev/kvm does not exist");
			return false;
		}
		
		if (!new File("/dev/kvm").canRead() || !new File("/dev/kvm").canWrite()) {
			LOGGER.debug("No permission to access /dev/kvm");
			return false;
		}
		
		try {
			return new ProcessBuilder("kvm-ok").start().waitFor() == 0;
		} catch (Exception e) {
			LOGGER.debug("kvm-ok failed");
			return false;
		}
	}

	private void createSMBServer(VirtualMachine virtualMachine, Integer smbPort)
			throws Exception {
		Map<String, SharedFolder> sharedFolders = virtualMachine
				.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null) {
			return;
		}
		String user = virtualMachine
				.getProperty(VirtualMachineConstants.GUEST_USER);
		String password = virtualMachine
				.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
		SMBServer server = EmbeddedCifsServer.create(sharedFolders.values(),
				user, password, smbPort);
		virtualMachine.setProperty(CIFS_SERVER, server);
	}

	private static Integer randomPort() {
		int retries = RANDOM_PORT_RETRIES;
		Random random = new Random();
		while (retries-- > 0) {
			ServerSocket socket = null;
			try {
				Integer port = random.nextInt(50000) + 10000;
				LOGGER.debug("Trying random port: " + port);
				socket = new ServerSocket(port);
				return port;
			} catch (Exception e) {
				LOGGER.warn("Port busy", e);
				try {
					Thread.sleep(random.nextInt(10) * 1000 + 1000);
				} catch (InterruptedException e1) {
					// Ignore
				}
			} finally {
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e) {
						// Ignore
					}
				}
			}
		}
		throw new IllegalStateException("Could not find a suitable random port");
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
			Exception ex = null;
			
			try {
				verifyProcessRunning(virtualMachine);
			} catch (Exception e) {
				stopCIFS(virtualMachine);
				throw e;
			}
			
			try {
				if (HypervisorUtils.isLinuxGuest(virtualMachine)) {
					createSSHClient(virtualMachine).disconnect();
					break;
				} else {
					ex = new Exception("Guest OS not supported");
				}
			} catch (Exception e) {
				if (checkTimeout && remainingTries-- == 0) {
					ex = new Exception(
							"Virtual Machine OS was not started. Please check you credentials.", e);
				}
			}

			if (ex != null) {
				stopCIFS(virtualMachine);
				throw ex;
			}
			Thread.sleep(1000 * START_RECHECK_DELAY);
		}
	}

	private void verifyProcessRunning(VirtualMachine virtualMachine)
			throws Exception {
		Process process = virtualMachine.getProperty(PROCESS);
		try {
			process.exitValue();

			List<String> stderr = IOUtils.readLines(process.getInputStream());
			List<String> stdout = IOUtils.readLines(process.getErrorStream());

			throw new Exception("Virtual Machine was forcibly terminated. "
					+ "Stdout [" + stdout + "] stderr [" + stderr + "]");

		} catch (IllegalThreadStateException e) {
			// Should proceed, process hasn't terminated yet
		}
	}

	private SSHClient createSSHClient(VirtualMachine virtualMachine)
			throws Exception {
		String ip = (String) getProperty(virtualMachine,
				VirtualMachineConstants.IP);
		Integer sshPort = (Integer) getProperty(virtualMachine,
				VirtualMachineConstants.SSH_PORT);

		if (ip == null) {
			throw new Exception("Could not acquire IP.");
		}

		if (sshPort == null) {
			sshPort = VirtualMachineConstants.DEFAULT_SSH_PORT;
		}

		SSHClient ssh = new SSHClient();
		ssh.addHostKeyVerifier(createBlankHostKeyVerifier());
		
		LOGGER.debug("Connecting SSH client " + ip + ":" + sshPort);
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
		stopCIFS(virtualMachine);
		
		runQMPCommand(virtualMachine, QmpCmd.STOP.getCmd());

		Process p = virtualMachine.getProperty(PROCESS);
		p.destroy();
		
		try {
			kill(virtualMachine);
		} catch (Exception e) {
			// Best effort
		}
		
		String netType = virtualMachine
				.getProperty(VirtualMachineConstants.NETWORK_TYPE);
		if (netType.equals("bridged")) {
			unconfigureBridged(virtualMachine);
		}
		
		virtualMachine.setProperty(POWERED_OFF, true);
	}
	
	private void kill(VirtualMachine virtualMachine) throws Exception {
		try {
			String pid = getPid(virtualMachine);
			if (pid != null) {
				new ProcessBuilder("/bin/kill", pid).start().waitFor();
			}
		} catch (Exception e) {
			throw e;
		} finally {
			getPidFile(virtualMachine).delete();
		}
	}

	private File getPidFile(final VirtualMachine virtualMachine) {
		String temp = System.getProperty("java.io.tmpdir");
		return new File(new File(temp), "qemu-" + virtualMachine.getName() + ".pid");
	}
	
	private File getMonitorFile(final VirtualMachine virtualMachine) {
		String temp = System.getProperty("java.io.tmpdir");
		return new File(new File(temp), "qemu-" + virtualMachine.getName() + ".monitor");
	}
	
	private File getConsoleOutputFile(final VirtualMachine virtualMachine) {
		String temp = System.getProperty("java.io.tmpdir");
		return new File(new File(temp), "qemu-" + virtualMachine.getName() + "-console.log");
	}
	
	private String getPid(VirtualMachine virtualMachine) {
		try {
			File pidFile = getPidFile(virtualMachine);
			if (!pidFile.exists()) {
				return null;
			}
			String pid = IOUtils.toString(new FileInputStream(pidFile)).trim();
			if (pid.length() == 0) {
				return null;
			}
			return pid;
		} catch (Exception e) {
			return null;
		}
	}

	private void stopCIFS(VirtualMachine virtualMachine) {
		SMBServer smbServer = virtualMachine.getProperty(CIFS_SERVER);
		if (smbServer != null) {
			smbServer.shutdownServer(true);
		}
	}

	@Override
	public VirtualMachineStatus status(VirtualMachine virtualMachine)
			throws Exception {
		Process p = virtualMachine.getProperty(PROCESS);
		if (p == null) {
			return VirtualMachineStatus.NOT_CREATED;
		}
		Boolean poweredOff = virtualMachine.getProperty(POWERED_OFF);
		if (poweredOff != null && poweredOff) {
			return VirtualMachineStatus.POWERED_OFF;
		}
		return VirtualMachineStatus.RUNNING;
	}

	@Override
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
			throws Exception {
		new File(hostPath).mkdirs();
		Map<String, SharedFolder> sharedFolders = virtualMachine
				.getProperty(SHARED_FOLDERS);
		if (sharedFolders == null) {
			sharedFolders = new HashMap<String, SharedFolder>();
			virtualMachine.setProperty(SHARED_FOLDERS, sharedFolders);
		}
		sharedFolders.put(shareName, new SharedFolder(shareName, hostPath,
				guestPath));
	}

	@Override
	public void takeSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception {
		String hda = virtualMachine
				.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		if (!new File(hda).exists()) {
			throw new Exception(
					"Could not take snapshot. Original disk image does not exist.");
		}

		String snapshotFile = getSnapshotLocation(virtualMachine, snapshotName);
		ProcessBuilder snapBuilder = getImgProcessBuilder(" create -f qcow2 -b "
				+ hda + " " + snapshotFile);
		Process snapProcess = snapBuilder.start();
		snapProcess.waitFor();

		int exitValue = snapProcess.exitValue();
		if (exitValue != 0) {
			throw new Exception("Could not take snapshot. Exit value "
					+ exitValue);
		}

		restoreSnapshot(virtualMachine, snapshotName);
	}

	private String getSnapshotLocation(VirtualMachine virtualMachine,
			String snapshotName) {
		String hda = virtualMachine
				.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String hdaLocation = new File(hda).getParent();
		String snapshotFile = new File(hdaLocation + "/" + snapshotName + "_"
				+ virtualMachine.getName() + ".img").getAbsolutePath();
		return snapshotFile;
	}

	@Override
	public void restoreSnapshot(VirtualMachine virtualMachine,
			String snapshotName) throws Exception {

		String snapshotFile = getSnapshotLocation(virtualMachine, snapshotName);
		if (!new File(snapshotFile).exists()) {
			throw new Exception(
					"Could not restore snapshot. Snapshot file does not exist.");
		}

		String currentSnapshotFile = getSnapshotLocation(virtualMachine,
				CURRENT_SNAPSHOT);
		ProcessBuilder currSnapBuilder = getImgProcessBuilder(" create -f qcow2 -b "
				+ snapshotFile + " " + currentSnapshotFile);

		Process snapProcess = currSnapBuilder.start();
		snapProcess.waitFor();

		int exitValue = snapProcess.exitValue();
		if (exitValue != 0) {
			throw new Exception("Could not restore snapshot. Exit value "
					+ exitValue);
		}

		virtualMachine.setProperty(RESTORE_SNAPSHOT, snapshotName);
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine,
			String commandLine) throws Exception {

		if (status(virtualMachine) == VirtualMachineStatus.POWERED_OFF) {
			throw new Exception(
					"Unable to execute command. Machine is not started.");
		}

		LOGGER.info("Exec: " + commandLine);
		
		SSHClient sshClient = createAuthSSHClient(virtualMachine);

		Session session = sshClient.startSession();
		session.setAutoExpand(true);
		Command command = session.exec(commandLine);
		command.setAutoExpand(true);

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

		SSHClient sshClient = createAuthSSHClient(virtualMachine);

		String user = virtualMachine
				.getProperty(VirtualMachineConstants.GUEST_USER);
		String password = virtualMachine
				.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
		Session session = sshClient.startSession();
		
		StringBuilder mntBuilder = new StringBuilder();
		mntBuilder.append("mkdir -p ").append(guestPath).append(";")
			.append("sudo mount -t cifs //").append(CIFS_DEVICE).append("/")
			.append(shareName).append(" ")
			.append(guestPath).append(" -o ")
			.append("port=").append(CIFS_PORT_GUEST)
			.append(",username=").append(user)
			.append(",password=").append(password)
			.append(",uid=").append(user).append(",forceuid")
			.append(",gid=").append(user).append(",forcegid,rw");
		
		session.exec(mntBuilder.toString()).join();
		session.close();
	}

	private SSHClient createAuthSSHClient(VirtualMachine virtualMachine) throws Exception {
		
		int retries = AUTHSSH_RETRIES;
		SSHClient sshClient = null;
		
		while (true) {
			try {
				sshClient = createSSHClient(virtualMachine);
				String user = virtualMachine
						.getProperty(VirtualMachineConstants.GUEST_USER);
				String password = virtualMachine
						.getProperty(VirtualMachineConstants.GUEST_PASSWORD);
				sshClient.getConnection().setTimeout(DEF_CONNECTION_TIMEOUT);
				
				LOGGER.debug("Trying password authentication with to=" + DEF_CONNECTION_TIMEOUT);
				sshClient.authPassword(user, password);
				LOGGER.debug("SSHClient authenticated.");
				
				return sshClient;
			} catch (Exception e) {
				LOGGER.debug("Auth SSH Failed. Cause: " + e.getMessage() + ". Retrying...");
				Thread.sleep(10000);
				if (--retries == 0) {
					throw e;
				}
			}
		}
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
		Map<String, SharedFolder> sharedFolders = registeredVM
				.getProperty(SHARED_FOLDERS);
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
		} else if (HypervisorUtils.isLinuxHost() || HypervisorUtils.isMacOSHost()) {
			List<String> matchList = HypervisorUtils.splitCmdLine("./" + cmd);
			processBuilder = new ProcessBuilder(matchList.toArray(new String[] {}));
		} else {
			throw new Exception("Host OS not supported");
		}

		if (qemuLocation != null) {
			processBuilder.directory(new File(qemuLocation));
		}

		return processBuilder;
	}

	@Override
	public void reboot(VirtualMachine virtualMachine) throws Exception {
		runQMPCommand(virtualMachine, QmpCmd.REBOOT.getCmd());
		checkOSStarted(virtualMachine);
	}
	
	private JsonElement runQMPCommand(VirtualMachine virtualMachine, String command) throws Exception {
		return runQMPCommand(virtualMachine, command, null);
	}
	
	private JsonElement runQMPCommand(VirtualMachine virtualMachine,
			String command, String arguments) throws Exception {
		Socket s = new Socket("127.0.0.1",
				(Integer) virtualMachine.getProperty(QMP_PORT));
		PrintStream ps = new PrintStream(s.getOutputStream());
		BufferedReader in = new BufferedReader(
				new InputStreamReader(s.getInputStream()));
		
		ps.println("{\"execute\":\"" + QmpCmd.CAPABILITIES.getCmd() + "\"}");
		ps.flush();
		Thread.sleep(QMP_CAPABILITY_WAIT);
		
		if (arguments == null) {
			ps.println("{\"execute\":\"" + command + "\"}");
		} else {
			String jsonCommand = "{\"execute\":\"" + command + "\", \"arguments\":" + arguments + "}";
			ps.println(jsonCommand);
		}
		
		ps.flush();
		Thread.sleep(QMP_CAPABILITY_WAIT);
		
		StringBuilder sb = new StringBuilder();
		
		while (in.ready()) {
			sb.append(in.readLine());
			sb.append("\n");
		}
		
		String cmdResp = sb.toString().split("\n")[2];
		
		JsonParser jParser = new JsonParser();
		JsonElement response = jParser.parse(cmdResp);
		
		s.close();
		
		return response;
	}
	
	@SuppressWarnings("unused")
	private void runMonitorCommand(VirtualMachine registeredVM, String command) throws Exception {
		FileOutputStream fos = new FileOutputStream(getMonitorFile(registeredVM));
		IOUtils.write(command + "\n", fos);
		fos.flush();
		Thread.sleep(QMP_CAPABILITY_WAIT);
		fos.close();
	}

	private void runMonitorCommandViaQMP(VirtualMachine registeredVM, String command) throws Exception {
		String args = "{ \"command-line\": \"" + command + "\" }";
		runQMPCommand(registeredVM, "human-monitor-command", args);
	}
	
	@Override
	public CPUStats getCPUStats(VirtualMachine virtualMachine) throws Exception {
		return HypervisorUtils.getCPUStats(getPid(virtualMachine));
	}

	@Override
	public List<DiskStats> getDiskStats(VirtualMachine virtualMachine) throws Exception {
		
		List<DiskStats> disksStats = new ArrayList<DiskStats>();
		
		JsonElement bStats = runQMPCommand(virtualMachine, QmpCmd.BLOCKSTATS.getCmd());
		//get timestamp subtracting qmp_capabilities command waited time
		long timestamp = System.currentTimeMillis() - QMP_CAPABILITY_WAIT;
		JsonObject ret = bStats.getAsJsonObject();
		JsonArray devices = ret.get(QmpJsonTag.RETURN.getTag()).getAsJsonArray();
		
		for (JsonElement device : devices) {
			JsonObject deviceObj = device.getAsJsonObject();
			JsonObject deviceStats = deviceObj.get(QmpJsonTag.STATS.getTag()).getAsJsonObject();
			
			DiskStats diskStats = new DiskStats();
			diskStats.setDeviceName(deviceObj.get(QmpJsonTag.DEVICE.getTag()).getAsString());
			diskStats.setTimestamp(timestamp);
			diskStats.setReadBytes(deviceStats.get(QmpJsonTag.READ_BYTES.getTag()).getAsLong());
			diskStats.setReadOps(deviceStats.get(QmpJsonTag.READ_OPS.getTag()).getAsLong());
			diskStats.setReadTotalTime(deviceStats.get(QmpJsonTag.READ_TOTAL_TIME_NS.getTag()).getAsLong()/1000);
			diskStats.setWriteBytes(deviceStats.get(QmpJsonTag.WRITE_BYTES.getTag()).getAsLong());
			diskStats.setWriteOps(deviceStats.get(QmpJsonTag.WRITE_OPS.getTag()).getAsLong());
			diskStats.setWriteTotalTime(deviceStats.get(QmpJsonTag.WRITE_TOTAL_TIME_NS.getTag()).getAsLong()/1000);
			
			disksStats.add(diskStats);
		}
		
		return disksStats;
	}

	@Override
	public String attachDevice(VirtualMachine registeredVM, String hostDevicePath) throws Exception {
		Integer currentDeviceIdx = registeredVM.getProperty(CURRENT_DEVICE_IDX);
		currentDeviceIdx = currentDeviceIdx == null ? 0 : currentDeviceIdx + 1;
		registeredVM.setProperty(CURRENT_DEVICE_IDX, currentDeviceIdx);
		
		String driveId = "drive-scsi0-0-0-" + currentDeviceIdx;
		String deviceId = "scsi0-0-0-" + currentDeviceIdx;
		
		runMonitorCommandViaQMP(registeredVM, "drive_add 0 file=" + hostDevicePath + ",if=none,id=" + driveId);
		runMonitorCommandViaQMP(registeredVM, "device_add usb-storage,bus=usb.0,drive=" + driveId + ",id=" + deviceId);
		
		Map<String, String> devicesAttached = registeredVM.getProperty(DEVICES_ATTACHED);
		if (devicesAttached == null) {
			devicesAttached = new HashMap<String, String>();
			registeredVM.setProperty(DEVICES_ATTACHED, devicesAttached);
		}
		devicesAttached.put(hostDevicePath, deviceId);
		
		char deviceIdx = (char) ('b' + currentDeviceIdx);
		
		return "/dev/sd" + deviceIdx;
	}
	
	@Override
	public void detachDevice(VirtualMachine registeredVM, String hostDevicePath) throws Exception {
		Map<String, String> devicesAttached = registeredVM.getProperty(DEVICES_ATTACHED);
		if (devicesAttached == null) {
			return;
		}
		String deviceId = devicesAttached.get(hostDevicePath);
		if (deviceId == null) {
			return;
		}
		runMonitorCommandViaQMP(registeredVM, "device_del " + deviceId);
	}

	@Override
	public String getConsoleOuput(VirtualMachine registeredVM) throws IOException {
		File consoleOutputFile = getConsoleOutputFile(registeredVM);
		return IOUtils.toString(new FileInputStream(consoleOutputFile));
	}
	
}

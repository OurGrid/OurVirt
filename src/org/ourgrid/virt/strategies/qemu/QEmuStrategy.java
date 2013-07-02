package org.ourgrid.virt.strategies.qemu;

import java.io.File;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategy;
import org.ourgrid.virt.strategies.HypervisorUtils;

public class QEmuStrategy implements HypervisorStrategy {

	public static final String QEMU_LOCATION = "QEMU_LOCATION";
	
	private static final String PROCESS = "QEMU_LOCATION";
	private static final String DESTROYED = "DESTROYED";
	private static final int START_RECHECK_DELAY = 10;

	
	private String qemuLocation;
	
	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {
		String hda = virtualMachine.getProperty(VirtualMachineConstants.DISK_IMAGE_PATH);
		String memory = virtualMachine.getProperty(VirtualMachineConstants.MEMORY);
		
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("-net nic")
			.append(" -net user,restrict=yes,hostfwd=tcp:localhost:5555-:22")
			.append(" -m ").append(memory)
			.append(" -hda ").append(hda);
		
		ProcessBuilder builder = getProcessBuilder(strBuilder.toString());
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restoreSnapshot(VirtualMachine virtualMachine,
			String snapshotName) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String command)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void create(VirtualMachine virtualMachine) throws Exception {
	
	}

	@Override
	public void destroy(VirtualMachine virtualMachine) throws Exception {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
	}

	@Override
	public void unmountSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteSharedFolder(VirtualMachine registeredVM, String shareName)
			throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void clone(String sourceDevice, String destDevice) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object getProperty(VirtualMachine registeredVM, String propertyName)
			throws Exception {
		return null;
	}

	@Override
	public void setProperty(VirtualMachine registeredVM, String propertyName,
			Object propertyValue) throws Exception {
		
	}

	@Override
	public void prepareEnvironment(Map<String, String> props) throws Exception {
		this.qemuLocation = props.get(QEMU_LOCATION);
	}
	
	private ProcessBuilder getProcessBuilder(String cmd) throws Exception {

		String vboxManageCmdLine = "qemu-system-i386w --nographic " + cmd;
		ProcessBuilder processBuilder = null;

		if (HypervisorUtils.isWindowsHost()) {
			processBuilder = new ProcessBuilder("cmd", "/C " + vboxManageCmdLine);
		} else if (HypervisorUtils.isLinuxHost()) {
			processBuilder =  new ProcessBuilder(vboxManageCmdLine, cmd);

		} else {
			throw new Exception("Host OS not supported");
		}

		if (qemuLocation != null) {
			processBuilder.directory(new File(qemuLocation));
		}

		return processBuilder;
	}

}

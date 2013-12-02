package org.ourgrid.virt.strategies;

import java.io.FileReader;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.model.CPUStats;
import org.ourgrid.virt.model.NetworkStats;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;

public class LinuxUtils {

	private static final int CPU_TIME_INDEX = 10;
	public static final String LINUX_PERMISSIONS_FILE = "/etc/sudoers";

	public static void appendNoPasswdToSudoers(String userName, String line)
			throws Exception {
		String permissionLine = userName + " ALL=NOPASSWD: " + line;
		appendLineToSudoers(permissionLine);
	}

	public static void appendLineToSudoers(String line) throws Exception {
		String sudoers = IOUtils.toString(new FileReader(LINUX_PERMISSIONS_FILE));

		if ( !sudoers.contains(line) ){
			ProcessBuilder sudoersAppendProc = new ProcessBuilder(
					"/bin/bash", "-c", "/bin/echo " + line + " >> " + LINUX_PERMISSIONS_FILE);
			try {
				HypervisorUtils.runAndCheckProcess(sudoersAppendProc);
			} catch (Exception e) {
				throw new Exception("Could not append sudoer line " + line, e);
			}
		}
	}

	public static NetworkStats getNetworkStats(VirtualMachine registeredVM, 
			String ifName) throws Exception {
		
		if (!registeredVM.getProperty(VirtualMachineConstants.NETWORK_TYPE)
				.equals(VirtualMachineConstants.BRIDGED_NET_MODE)) {
			throw new RuntimeException("Unsupported network type");
		}
		
		StringBuilder cmdSB = new StringBuilder();
		cmdSB.append("cat /proc/net/dev | grep " + ifName);
		
		ProcessBuilder psProcessBuilder = 
				new ProcessBuilder("/bin/bash", "-c", cmdSB.toString());
		Process p = psProcessBuilder.start();
		
		int psExitValue = p.waitFor();
		//Getting the approximate timestamp of the cat process return
		long timestamp = System.currentTimeMillis();
		if (psExitValue != 0) {
			String stdError = IOUtils.toString(p.getErrorStream());
			String stdOut = IOUtils.toString(p.getInputStream());
			throw new RuntimeException(
					"Error while trying to get device information. StdOut: [ " 
					+ stdOut + " ]. StdError: [ " + stdError + " ]");
		}
		
		String[] ifStats = IOUtils.toString(p.getInputStream()).split("\\s+");
		
		NetworkStats networkStats = new NetworkStats();
		networkStats.setDeviceName(ifName);
		networkStats.setTimestamp(timestamp);
		networkStats.setReceivedBytes(Long.parseLong(ifStats[1]));
		networkStats.setReceivedPackets(Long.parseLong(ifStats[2]));
		networkStats.setReceivedErrors(Long.parseLong(ifStats[3]));
		networkStats.setReceivedDropped(Long.parseLong(ifStats[4]));
		networkStats.setReceivedFIFOErrors(Long.parseLong(ifStats[5]));
		networkStats.setReceivedPktFramingErrors(Long.parseLong(ifStats[6]));
		networkStats.setReceivedCompressed(Long.parseLong(ifStats[7]));
		networkStats.setReceivedMulticast(Long.parseLong(ifStats[8]));
		
		networkStats.setTransferredBytes(Long.parseLong(ifStats[9]));
		networkStats.setTransferredPackets(Long.parseLong(ifStats[10]));
		networkStats.setTransferredErrors(Long.parseLong(ifStats[11]));
		networkStats.setTransferredDropped(Long.parseLong(ifStats[12]));
		networkStats.setTransferredFIFOErrors(Long.parseLong(ifStats[13]));
		networkStats.setTransferredCollisions(Long.parseLong(ifStats[14]));
		networkStats.setTransferredCarrierLosses(Long.parseLong(ifStats[15]));
		networkStats.setTransferredCompressed(Long.parseLong(ifStats[16]));
		
		return networkStats;
	}

	public static CPUStats getCPUStats(String vmProcessPid) throws Exception {
			
		CPUStats cpuStats;
		
		String[] topStats;
		long cpuTime = 0;
		
		StringBuilder topCmd = new StringBuilder();
		topCmd.append("top -b -n 1 -p ");
		topCmd.append(vmProcessPid);
		topCmd.append(" | grep ");
		topCmd.append(vmProcessPid);
		
		ProcessBuilder psProcessBuilder = 
				new ProcessBuilder("/bin/bash", "-c", topCmd.toString());
		
		Process psProcess = psProcessBuilder.start();
		InputStream psIn = psProcess.getInputStream();
		int psExitValue = psProcess.waitFor();
		//Getting the approximate timestamp of the top process return
		long timestamp = System.currentTimeMillis();
		if (psExitValue != 0) {
			throw new Exception("Could not retrieve cpu statistics. " +
					"Process return value: " + psExitValue);
		}
		
		String processStr = IOUtils.toString(psIn);
		topStats = processStr.trim().split("\\s+");
		
		if (topStats.length < CPU_TIME_INDEX + 1) {
			throw new Exception("Could not retrieve cpu statistics.");
		}
		
//		CPUTime Pattern: [DD-]mm:ss.hh
		String cpuTimeStr = topStats[CPU_TIME_INDEX];
		String[] cpuTimeArray = cpuTimeStr.split("-");
		if (cpuTimeArray.length > 1) {
			cpuTime += Long.parseLong(cpuTimeArray[0])*24*60*60;
			cpuTimeStr = cpuTimeArray[1];
		}
		long minutes = Long.parseLong(cpuTimeStr.split(":")[0]);
		long seconds = Long.parseLong(cpuTimeStr.split(":")[1].split("\\.")[0]);
		long hundredths = Long.parseLong(cpuTimeStr.split(":")[1].split("\\.")[1]);
		
		// Changing time unit to ms
		cpuTime += minutes*60*1000;
		cpuTime += seconds*1000;
		cpuTime += hundredths*10; 
		
		cpuStats = new CPUStats();
		cpuStats.setCpuTime(cpuTime);
		cpuStats.setTimestamp(timestamp);
		
		return cpuStats;
	}

}

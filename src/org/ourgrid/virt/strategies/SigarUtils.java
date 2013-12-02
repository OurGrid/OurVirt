package org.ourgrid.virt.strategies;

import org.hyperic.sigar.NetInterfaceStat;
import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.Sigar;
import org.ourgrid.virt.model.CPUStats;
import org.ourgrid.virt.model.NetworkStats;
import org.ourgrid.virt.model.VirtualMachine;

public class SigarUtils {

	private static Sigar SIGAR = new Sigar();
	
	public static NetworkStats getNetworkStats(VirtualMachine registeredVM, 
			String ifName) throws Exception {
		NetInterfaceStat netInterfaceStat = SIGAR.getNetInterfaceStat(ifName);
		
		NetworkStats networkStats = new NetworkStats();
		
		networkStats.setDeviceName(ifName);
		networkStats.setTimestamp(System.currentTimeMillis());
		networkStats.setReceivedBytes(netInterfaceStat.getRxBytes());
		networkStats.setReceivedPackets(netInterfaceStat.getRxPackets());
		networkStats.setReceivedErrors(netInterfaceStat.getRxErrors());
		networkStats.setReceivedDropped(netInterfaceStat.getRxDropped());
		
		networkStats.setTransferredBytes(netInterfaceStat.getTxBytes());
		networkStats.setTransferredPackets(netInterfaceStat.getTxPackets());
		networkStats.setTransferredErrors(netInterfaceStat.getTxErrors());
		networkStats.setTransferredDropped(netInterfaceStat.getTxDropped());
		
		return networkStats;
	}
	
	public static CPUStats getCPUStats(String vmProcessPid) throws Exception {
		ProcCpu procCpu = SIGAR.getProcCpu(vmProcessPid);
		CPUStats cpuStats = new CPUStats();
		cpuStats.setCpuTime(procCpu.getTotal());
		cpuStats.setTimestamp(System.currentTimeMillis());
		return cpuStats;
	}
	
}

package org.ourgrid.virt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategyFactory;

public class OurVirt {

	private Map<String, VirtualMachine> vMCache = new HashMap<String, VirtualMachine>();
	private HypervisorStrategyFactory factory = new HypervisorStrategyFactory();
	
	public void register(String vmName, Map<String, String> configuration) {
		VirtualMachine vm = new VirtualMachine(vmName);
		vm.setConfiguration(configuration);
		vMCache.put(vmName, vm);
	}
	
	public void start(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).start(vMCache.get(vmName));
	}

	public void stop(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).stop(vMCache.get(vmName));
	}

	public VirtualMachineStatus status(HypervisorType hypervisor, String vmName) throws Exception {
		VirtualMachine virtualMachine = vMCache.get(vmName);
		if (virtualMachine == null) {
			return VirtualMachineStatus.NOT_REGISTERED;
		}
		
		return factory.get(hypervisor).status(virtualMachine);
	}
	
	public ExecutionResult exec(HypervisorType hypervisor, String vmName, String command) throws Exception {
		return factory.get(hypervisor).exec(vMCache.get(vmName), command);
	}
	
	public void takeSnapshot(HypervisorType hypervisor, 
			String vmName, String snapshotName) throws Exception {
		factory.get(hypervisor).takeSnapshot(vmName, snapshotName);
	}
	
	public void restoreSnapshot(HypervisorType hypervisor, 
			String vmName, String snapshotName) throws Exception {
		factory.get(hypervisor).restoreSnapshot(vmName, snapshotName);
	}
	
	public void create(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).create(vMCache.get(vmName));
	}

	public void destroy(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).destroy(vMCache.get(vmName));
	}
	
	public List<String> list(HypervisorType hypervisor) throws Exception {
		return factory.get(hypervisor).listVMs();
	}
	
	public boolean isSupported(HypervisorType hypervisor) throws Exception {
		return factory.get(hypervisor).isSupported();
	}
	
	public List<String> listSnapshots(HypervisorType hypervisor, String vmName) throws Exception {
		return factory.get(hypervisor).listSnapshots(vMCache.get(vmName));
	}
	
	public List<String> listSharedFolders(HypervisorType hypervisor, String vmName) throws Exception {
		return factory.get(hypervisor).listSharedFolders(vMCache.get(vmName));
	}
	
	/**
	 * The shared folder will be mounted automatically.
	 * 
	 * @param hypervisor
	 * @param vmName
	 * @param shareName
	 * @param hostPath
	 * @param guestPath
	 * @throws Exception
	 */
	public void createSharedFolder(HypervisorType hypervisor, String vmName, String shareName, String hostPath, String guestPath) throws Exception {
		factory.get(hypervisor).createSharedFolder(vMCache.get(vmName), shareName, hostPath, guestPath);
	}
	
	public void createSharedFolder(HypervisorType hypervisor, String vmName, String shareName, String hostPath) throws Exception {
		factory.get(hypervisor).createSharedFolder(vMCache.get(vmName), shareName, hostPath);
	}
	
	public void mountSharedFolder(HypervisorType hypervisor, String vmName, String shareName, String guestPath) throws Exception {
		factory.get(hypervisor).mountSharedFolder(vMCache.get(vmName), shareName, guestPath);
	}
}

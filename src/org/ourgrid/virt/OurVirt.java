package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.strategies.HypervisorStrategyFactory;

public class OurVirt {

	private Map<String, VirtualMachine> vMCache = new HashMap<String, VirtualMachine>();
	private HypervisorStrategyFactory factory = new HypervisorStrategyFactory();
	
	public void start(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).start(vMCache.get(vmName));
	}

	public void stop(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).stop(vmName);
	}

	public void takeSnapshot(HypervisorType hypervisor, 
			String vmName, String snapshotName) throws Exception {
		factory.get(hypervisor).takeSnapshot(vmName, snapshotName);
	}
	
	public void restoreSnapshot(HypervisorType hypervisor, 
			String vmName, String snapshotName) throws Exception {
		factory.get(hypervisor).restoreSnapshot(vmName, snapshotName);
	}
	
	public void create(HypervisorType vbox, String vmName, String imagePath, 
			Map<String, String> configuration) {
		
		VirtualMachine vm = new VirtualMachine(vmName, imagePath);
		vm.setConfiguration(configuration);
		vMCache.put(vmName, vm);
	}
}

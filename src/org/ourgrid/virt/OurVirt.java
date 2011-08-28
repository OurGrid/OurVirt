package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.strategies.HypervisorStrategyFactory;

public class OurVirt {

	private Map<String, VirtualMachine> vMCache = new HashMap<String, VirtualMachine>();
	private HypervisorStrategyFactory factory = new HypervisorStrategyFactory();
	
	public void register(HypervisorType hypervisor, String vmName, String imagePath, 
			Map<String, String> configuration) {
		VirtualMachine vm = new VirtualMachine(vmName, imagePath);
		vm.setConfiguration(configuration);
		vMCache.put(vmName, vm);
	}
	
	public void start(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).start(vMCache.get(vmName));
	}

	public void stop(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).stop(vMCache.get(vmName));
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
}

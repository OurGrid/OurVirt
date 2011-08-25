package org.ourgrid.virt;

import org.ourgrid.virt.strategies.HypervisorStrategyFactory;

public class OurVirt {

	private HypervisorStrategyFactory factory = new HypervisorStrategyFactory();
	
	public void start(Hypervisor hypervisor, String vmName) throws Exception {
		factory.create(hypervisor).start(vmName);
	}

	public void stop(Hypervisor hypervisor, String vmName) throws Exception {
		factory.create(hypervisor).stop(vmName);
	}
	
}

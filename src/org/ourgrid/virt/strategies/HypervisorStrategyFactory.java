package org.ourgrid.virt.strategies;

import org.ourgrid.virt.Hypervisor;

public class HypervisorStrategyFactory {

	public HypervisorStrategy create(Hypervisor hypervisorType) {
		
		switch (hypervisorType) {
		case VBOX:
			return new VBoxStrategy();

		default:
			return null;
		}
		
	}
	
}

package org.ourgrid.virt.strategies;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.strategies.vbox.VBoxStrategy;

public class HypervisorStrategyFactory {

	private Map<HypervisorType, HypervisorStrategy> strategies = new HashMap<HypervisorType, HypervisorStrategy>();
	
	public HypervisorStrategy get(HypervisorType hypervisorType) {
		
		HypervisorStrategy hypervisorStrategy = strategies.get(hypervisorType);
		
		if (hypervisorStrategy == null) {
			hypervisorStrategy = create(hypervisorType);
			strategies.put(hypervisorType, hypervisorStrategy);
		}
		
		return hypervisorStrategy;
	}

	private HypervisorStrategy create(HypervisorType hypervisorType) {
		switch (hypervisorType) {
		case VBOX:
			return new VBoxStrategy();
		default:
			return null;
		}
	}
	
}

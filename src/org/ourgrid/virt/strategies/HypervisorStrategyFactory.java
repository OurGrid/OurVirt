package org.ourgrid.virt.strategies;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.strategies.vbox.VBoxSdkStrategy;
import org.ourgrid.virt.strategies.vbox.VBoxStrategy;
import org.ourgrid.virt.strategies.vserver.VServerStrategy;

/**
 * This class uses the Factory design pattern
 * TODO
 */
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
		case VBOXSDK:
			return new VBoxSdkStrategy();
		case VSERVER:
			return new VServerStrategy();
		default:
			return null;
		}
	}
	
}

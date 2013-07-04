package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachineConstants;
import org.ourgrid.virt.strategies.HypervisorStrategyFactory;

public class PrepareEnvironment {

	private static HypervisorStrategyFactory factory = new HypervisorStrategyFactory();
	
	public static void main(String[] args) throws Exception{
		String hypervisorTypeStr = args[0];
		String ourVirtUser = args[1];
		
		HypervisorType hypervisorType = HypervisorType.valueOf(hypervisorTypeStr.toUpperCase());
		if ( hypervisorType == null ){
			throw new Exception("Hypervisor type " + hypervisorTypeStr + " not supported by OurVirt.");
		}
		
		Map<String, String> props = new HashMap<String, String>();
		props.put(VirtualMachineConstants.HOST_USER, ourVirtUser);
		
		factory.get(hypervisorType).prepareEnvironment(props);
	}
	
}

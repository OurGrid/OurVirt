package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.HypervisorType;

public class Main {

	public static void main(String[] args) throws Exception {
		OurVirt ourVirt = new OurVirt();
		
		System.out.println(ourVirt.isSupported(HypervisorType.VBOX));
		System.out.println(ourVirt.list(HypervisorType.VBOX));
		runVmServer(ourVirt);
	}

	private static void runVmServer(OurVirt ourVirt) throws Exception {
		String vmName = "abmar-vm-server";
		
		Map<String, String> conf = new HashMap<String, String>();
		conf.put("user", "worker");
		conf.put("password", "worker");
		conf.put("memory", "512");
		conf.put("os", "Linux");
		conf.put("osversion", "Ubuntu");
		conf.put("disktype", "sata");
		conf.put("diskimagepath", 
				"C:\\Users\\Abmar\\VirtualBox VMs\\abmar-vm-server\\abmar-vm-server.vdi");
		
		ourVirt.register(vmName, conf);
		
		ourVirt.create(HypervisorType.VBOX, vmName);
		ourVirt.start(HypervisorType.VBOX, vmName);
		
		ExecutionResult exec = ourVirt.exec(HypervisorType.VBOX, vmName, "/bin/echo Hello World");
		System.out.println(exec.getStdOut().toString());
		
		ourVirt.stop(HypervisorType.VBOX, vmName);
//		ourVirt.destroy(HypervisorType.VBOX, vmName);
	}
	
}

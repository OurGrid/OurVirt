package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachineConstants;

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
		conf.put(VirtualMachineConstants.GUEST_USER, "worker");
		conf.put(VirtualMachineConstants.GUEST_PASSWORD, "worker");
		conf.put(VirtualMachineConstants.MEMORY, "512");
		conf.put(VirtualMachineConstants.OS, "Linux");
		conf.put(VirtualMachineConstants.OS_VERSION, "Ubuntu");
		conf.put(VirtualMachineConstants.DISK_TYPE, "sata");
		conf.put(VirtualMachineConstants.DISK_IMAGE_PATH, 
				"C:\\Users\\Abmar\\VirtualBox VMs\\abmar-vm-server\\abmar-vm-server.vdi");
		
		ourVirt.register(vmName, conf);
		
		System.out.println(ourVirt.listSnapshots(HypervisorType.VBOX, vmName));
		System.out.println(ourVirt.listSharedFolders(HypervisorType.VBOX, vmName));
		
		ourVirt.create(HypervisorType.VBOX, vmName);
		ourVirt.start(HypervisorType.VBOX, vmName);
		
		ExecutionResult exec = ourVirt.exec(HypervisorType.VBOX, vmName, "/bin/echo Hello World");
		System.out.println(exec.getStdOut().toString());
		
		ourVirt.stop(HypervisorType.VBOX, vmName);
		ourVirt.destroy(HypervisorType.VBOX, vmName);
	}
	
}

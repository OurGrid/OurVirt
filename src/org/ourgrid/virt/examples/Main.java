package org.ourgrid.virt.examples;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.OurVirt;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachineConstants;

public class Main {

	public static void main(String[] args) throws Exception {
		OurVirt ourVirt = new OurVirt();
		runVmServer(ourVirt);
	}

	private static void runVmServer(OurVirt ourVirt) throws Exception {
		String vmName = "owvbox_1";
		
		Map<String, String> conf = new HashMap<String, String>();
		conf.put(VirtualMachineConstants.GUEST_USER, "worker");
		conf.put(VirtualMachineConstants.GUEST_PASSWORD, "worker");
		conf.put(VirtualMachineConstants.MEMORY, "512");
		conf.put(VirtualMachineConstants.OS, "Linux");
		conf.put(VirtualMachineConstants.OS_VERSION, "Ubuntu");
		conf.put(VirtualMachineConstants.DISK_TYPE, "sata");
		conf.put(VirtualMachineConstants.DISK_IMAGE_PATH, 
				"C:\\Users\\Abmar\\VirtualBox VMs\\abmar-vm-server\\abmar-vm-server.vdi");
		conf.put(VirtualMachineConstants.START_TIMEOUT, "60");
		
		ourVirt.register(vmName, conf);
		
		try {
			ourVirt.stop(HypervisorType.VBOX, vmName);
		} catch (Exception e) {}
		
		ourVirt.create(HypervisorType.VBOX, vmName);
		ourVirt.createSharedFolder(HypervisorType.VBOX, vmName, 
				"shared-home", "C:\\Users\\Abmar", "/home/worker/shared");
		
		ourVirt.start(HypervisorType.VBOX, vmName);
		
		ourVirt.mountSharedFolder(HypervisorType.VBOX, vmName, 
				"shared-home");
		
		File file = new File("C:\\Users\\Abmar\\hello.txt");
		FileWriter writer = new FileWriter(file);
		writer.write("Hello World");
		writer.close();
		
		ExecutionResult exec = ourVirt.exec(HypervisorType.VBOX, vmName, "/bin/cat /home/worker/shared/hello.txt");
		System.out.println(exec.getReturnValue());
		System.out.println(exec.getStdOut().toString());
		
//		ourVirt.destroy(HypervisorType.VBOX, vmName);
	}
	
}

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
		String vmName = "owvbox_12";
		
		Map<String, String> conf = new HashMap<String, String>();
		conf.put(VirtualMachineConstants.GUEST_USER, "worker");
		conf.put(VirtualMachineConstants.GUEST_PASSWORD, "worker");
		conf.put(VirtualMachineConstants.MEMORY, "512");
		conf.put(VirtualMachineConstants.OS, "Linux");
		conf.put(VirtualMachineConstants.NETWORK_TYPE, "nat");
		conf.put(VirtualMachineConstants.OS_VERSION, "Ubuntu");
		conf.put(VirtualMachineConstants.DISK_TYPE, "sata");
		conf.put(VirtualMachineConstants.DISK_IMAGE_PATH, 
				"/home/marcosancj/tmp/og-image.vdi");
		conf.put(VirtualMachineConstants.START_TIMEOUT, "120");
		
		ourVirt.register(vmName, conf);
		
		try {
			ourVirt.stop(HypervisorType.VBOXSDK, vmName);
		} catch (Exception e) {}
		
		ourVirt.create(HypervisorType.VBOXSDK, vmName);
		
		try {
			ourVirt.takeSnapshot(HypervisorType.VBOXSDK, vmName, "OurGrid-VM");
		} catch (Exception e) {
			ourVirt.restoreSnapshot(HypervisorType.VBOXSDK, vmName, "OurGrid-VM");
		}
		
//		ourVirt.deleteSharedFolder(HypervisorType.VBOXSDK, vmName, "shared-home");
		ourVirt.createSharedFolder(HypervisorType.VBOXSDK, vmName, 
				"shared-home", "/home/marcosancj/tmp", "/home/worker/shared");
		
		ourVirt.start(HypervisorType.VBOXSDK, vmName);
		
		
		ourVirt.mountSharedFolder(HypervisorType.VBOXSDK, vmName, 
				"shared-home", "/home/marcosancj/tmp", "/home/worker/shared");
		
		File file = new File("/home/marcosancj/tmp/mainteste.txt");
		FileWriter writer = new FileWriter(file);
		writer.write("Hello World");
		writer.close();
		
		ExecutionResult exec = ourVirt.exec(HypervisorType.VBOXSDK, 
				vmName, "/bin/cat /home/worker/shared/mainteste.txt");
		System.out.println(exec.getReturnValue());
		System.out.println(exec.getStdOut().toString());
		
		ourVirt.stop(HypervisorType.VBOXSDK, vmName);
		ourVirt.destroy(HypervisorType.VBOXSDK, vmName);
	}
	
}

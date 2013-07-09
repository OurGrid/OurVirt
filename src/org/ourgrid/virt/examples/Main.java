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

	static final String QEMU_LOCATION = "C:\\Users\\Abmar\\Downloads\\qemu-1.5.1\\target";
	static final String IMG_LOCATION = "C:\\Users\\Abmar\\Downloads\\qemu-1.5.0-win32-sdl\\qemu-1.5.0-win32-sdl\\ubuntu-lts.img";
	
	private static void runVmServer(OurVirt ourVirt) throws Exception {
		
		String vmName = "owvbox_12";
		
		Map<String, String> conf = new HashMap<String, String>();
		conf.put(VirtualMachineConstants.GUEST_USER, "ubuntu");
		conf.put(VirtualMachineConstants.GUEST_PASSWORD, "reverse");
		conf.put(VirtualMachineConstants.MEMORY, "256");
		conf.put(VirtualMachineConstants.OS, "Linux");
		conf.put(VirtualMachineConstants.NETWORK_TYPE, "host-only");
		conf.put(VirtualMachineConstants.OS_VERSION, "Ubuntu");
		conf.put(VirtualMachineConstants.DISK_TYPE, "sata");
		conf.put(VirtualMachineConstants.DISK_IMAGE_PATH, IMG_LOCATION);
		conf.put(VirtualMachineConstants.START_TIMEOUT, "180");
		
		System.setProperty("qemu.home", QEMU_LOCATION);
		
		ourVirt.register(vmName, conf);
		
		try {
			ourVirt.stop(HypervisorType.QEMU, vmName);
		} catch (Exception e) {}
		
		ourVirt.create(HypervisorType.QEMU, vmName);
		
		try {
			ourVirt.takeSnapshot(HypervisorType.QEMU, vmName, "OurGrid-VM");
		} catch (Exception e) {
			ourVirt.restoreSnapshot(HypervisorType.QEMU, vmName, "OurGrid-VM");
		}
		
		ourVirt.deleteSharedFolder(HypervisorType.QEMU, vmName, "shared-home");
		ourVirt.createSharedFolder(HypervisorType.QEMU, vmName, 
				"shared-home", "C:\\Users\\Abmar\\Downloads\\backups", "/home/ubuntu/shared");
		
		ourVirt.start(HypervisorType.QEMU, vmName);
		
		ourVirt.mountSharedFolder(HypervisorType.QEMU, vmName, 
				"shared-home", "C:\\Users\\Abmar\\Downloads\\backups", "/home/ubuntu/shared");
		
		File file = new File("C:\\Users\\Abmar\\Downloads\\backups\\mainteste.txt");
		FileWriter writer = new FileWriter(file);
		writer.write("Hello World");
		writer.close();
		
		
		ExecutionResult exec = ourVirt.exec(HypervisorType.QEMU, 
				vmName, "/bin/cat /home/ubuntu/shared/mainteste.txt > /home/ubuntu/shared/output.out");
		System.out.println(exec.getReturnValue());
		System.out.println(exec.getStdOut().toString());
		
		ourVirt.stop(HypervisorType.QEMU, vmName);
		ourVirt.destroy(HypervisorType.QEMU, vmName);
	}
}

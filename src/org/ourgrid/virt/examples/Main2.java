package org.ourgrid.virt.examples;

import java.util.HashMap;
import java.util.Map;

import org.ourgrid.virt.OurVirt;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachineConstants;

public class Main2 {
	
	static final String IMG_LOCATION = "/local/tarciso/vm-images/linux-qemu-20130722.qcow2";

	public static void main(String[] args) throws Exception {
		
//		System.setProperty("vbox.home", "/usr/lib/virtualbox/");
		System.setProperty("qemu.home", "/local/tarciso/programs-64b/qemu/bin");
		OurVirt OURVIRT = new OurVirt();
		
		String vmName = "bridge-test";
		Map<String, String> conf = new HashMap<String, String>();
		conf.put(VirtualMachineConstants.GUEST_USER, "worker");
		conf.put(VirtualMachineConstants.GUEST_PASSWORD, "worker");
		conf.put(VirtualMachineConstants.MEMORY, "256");
		conf.put(VirtualMachineConstants.OS, "Linux");
		conf.put(VirtualMachineConstants.NETWORK_TYPE, "host-only");
		conf.put(VirtualMachineConstants.OS_VERSION, "Ubuntu");
		conf.put(VirtualMachineConstants.DISK_TYPE, "sata");
		conf.put(VirtualMachineConstants.DISK_IMAGE_PATH, IMG_LOCATION);
		conf.put(VirtualMachineConstants.START_TIMEOUT, "180");
		
		try {
			OURVIRT.register(vmName, conf);
			OURVIRT.start(HypervisorType.QEMU, vmName);
			System.out.println(OURVIRT.getDiskStats(HypervisorType.QEMU, vmName));
			
			OURVIRT.stop(HypervisorType.QEMU, vmName);
		} catch(Exception e) {
			OURVIRT.stop(HypervisorType.QEMU, vmName);
			System.out.println(e.getMessage());
		}
		
		
		
		
//		System.out.println(OURVIRT.status(HypervisorType.VBOXSDK, vmName));
		
	}

}

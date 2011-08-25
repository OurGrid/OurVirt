package org.ourgrid.virt;

public class Main {

	public static void main(String[] args) throws Exception {
		OurVirt ourVirt = new OurVirt();
		
		ourVirt.start(Hypervisor.VBOX, "abmar-vm");
		Thread.sleep(30000L);
		ourVirt.stop(Hypervisor.VBOX, "abmar-vm");
	}
	
}

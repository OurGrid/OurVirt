package org.ourgrid.virt;

import java.util.HashMap;
import java.util.Map;

public class Main {

	public static void main(String[] args) throws Exception {
		OurVirt ourVirt = new OurVirt();
		
		Map<String, String> conf = new HashMap<String, String>();
		conf.put("user", "worker");
		conf.put("password", "worker");
		
		ourVirt.create(HypervisorType.VBOX, "abmar-vm", null, conf);
		ourVirt.start(HypervisorType.VBOX, "abmar-vm");
	}
	
}

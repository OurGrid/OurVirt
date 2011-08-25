package org.ourgrid.virt.strategies;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class VBoxStrategy implements HypervisorStrategy {

	@Override
	public void start(String vMName) throws Exception {
		ProcessBuilder startProcessBuilder = getProcessBuilder("startvm " + vMName);
		runProcess(startProcessBuilder);
	}

	@Override
	public void stop(String vMName) throws Exception {
		ProcessBuilder stopProcessBuilder = getProcessBuilder("controlvm " + vMName + " poweroff");
		runProcess(stopProcessBuilder);
	}
	
	private static void runProcess(ProcessBuilder processBuilder)
			throws IOException, InterruptedException {
		
		processBuilder.directory(new File(
				System.getenv().get("VBOX_INSTALL_PATH")));
		Process startProcess = processBuilder.start();
		int returnvalue = startProcess.waitFor();
		
		List<String> readLines = IOUtils.readLines(startProcess.getInputStream());
		
		if (returnvalue != 0) {
			throw new IOException(readLines.toString());
		}
	}
	
	private static ProcessBuilder getProcessBuilder(String cmd) {
		String osName = System.getProperty("os.name");
		
		if (osName.toLowerCase().contains("windows")) {
			return new ProcessBuilder(
					"cmd", "/C start /B VBoxManage " + cmd + " --type headless");
		} else {
			return new ProcessBuilder(
					"VBoxManage " + cmd + " --type headless");
		}
	}
}

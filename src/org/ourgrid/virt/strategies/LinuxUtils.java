package org.ourgrid.virt.strategies;

import java.io.FileReader;

import org.apache.commons.io.IOUtils;

public class LinuxUtils {

	public static final String LINUX_PERMISSIONS_FILE = "/etc/sudoers";

	public static void appendNoPasswdToSudoers(String userName, String line)
			throws Exception {
		String permissionLine = userName + " ALL=NOPASSWD: " + line;
		appendLineToSudoers(permissionLine);
	}

	public static void appendLineToSudoers(String line) throws Exception {
		String sudoers = IOUtils.toString(new FileReader(LINUX_PERMISSIONS_FILE));

		if ( !sudoers.contains(line) ){
			ProcessBuilder sudoersAppendProc = new ProcessBuilder(
					"/bin/bash", "-c", "/bin/echo " + line + " >> " + LINUX_PERMISSIONS_FILE);
			try {
				HypervisorUtils.runAndCheckProcess(sudoersAppendProc);
			} catch (Exception e) {
				throw new Exception("Could not append sudoer line " + line, e);
			}
		}
	}

}

package org.ourgrid.virt.strategies.qemu;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;

public class DevConUtils {

	private static final Lock LOCK = new ReentrantReadWriteLock().writeLock();
	
	public static String installTap(VirtualMachine vm) throws Exception {
		LOCK.lock();
		try {
			List<String> oldHwIdsList = runDevCon(vm, "hwids tap0901");
			Set<String> oldHwIds = parseHwIds(oldHwIdsList);
			runDevCon(vm, "install driver\\OemWin2k.inf tap0901");
			List<String> newHwIdsList = runDevCon(vm, "hwids tap0901");
			Set<String> newHwIds = parseHwIds(newHwIdsList);
			
			newHwIds.removeAll(oldHwIds);
			String tapDevId = newHwIds.iterator().next();
			return tapDevId;
		} finally {
			LOCK.unlock();
		}
	}

	private static Set<String> parseHwIds(List<String> hwIdsList) {
		Set<String> hwIds = new HashSet<String>();
		for (int i = 0; i < hwIdsList.size() - 1; i++) {
			hwIds.add(hwIdsList.get(i).split(":")[0].trim());
		}
		return hwIds;
	}

	private static List<String> runDevCon(VirtualMachine vm, String command) throws Exception {
		String tapWinDir = vm.getProperty(VirtualMachineConstants.TAP_WINDOWS_DIR);
		ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/C devcon " + command);
		processBuilder.directory(new File(tapWinDir));
		
		Process process = processBuilder.start();
		int ret = process.waitFor();
		
		List<String> stdIo = IOUtils.readLines(process.getInputStream());
		String stdErr = IOUtils.toString(process.getErrorStream());
		
		if (ret != 0) {
			throw new Exception(stdErr);
		}
		
		return stdIo;
	}

	public static void uninstallTap(VirtualMachine vm, String tapDevId) throws Exception {
		LOCK.lock();
		try {
			runDevCon(vm, "remove @'" + tapDevId);
		} finally {
			LOCK.unlock();
		}
	}
	
}

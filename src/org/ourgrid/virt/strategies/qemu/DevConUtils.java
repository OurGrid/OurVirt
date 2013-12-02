package org.ourgrid.virt.strategies.qemu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
			List<String> oldHwIdsList = runDevCon(vm, "find tap0901");
			Set<String> oldHwIds = parseHwIds(oldHwIdsList);
			runDevCon(vm, "install driver\\OemWin2k.inf tap0901");
			List<String> newHwIdsList = runDevCon(vm, "find tap0901");
			Set<String> newHwIds = parseHwIds(newHwIdsList);
			
			newHwIds.removeAll(oldHwIds);
			String tapDevId = newHwIds.iterator().next();
			return tapDevId;
		} finally {
			LOCK.unlock();
		}
	}
	
	public static void renameTap(String tapDevId, String tapIf) throws IOException, InterruptedException {
		File file = File.createTempFile("rentap", ".reg");
		
		new ProcessBuilder("regedit.exe /e \"" + file.getAbsolutePath() 
				+ "\" HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Class\\{4D36E972-E325-11CE-BFC1-08002BE10318}")
			.start().waitFor();
		
		FileInputStream fis = new FileInputStream(file);
		List<String> regLines = IOUtils.readLines(fis, "UTF-16");
		fis.close();
		
		String tapDevIdEscaped = tapDevId.replace("\\", "\\\\");
		
		String netCfgInstanceId = null;
		for (String regLine : regLines) {
			if (regLine.contains("NetCfgInstanceId")) {
				netCfgInstanceId = getRegLineValue(regLine);
			} else if (regLine.contains("DeviceInstanceID")) {
				if (getRegLineValue(regLine).equals(tapDevIdEscaped)) {
					break;
				}
			}
		}
		
		FileOutputStream fos = new FileOutputStream(file);
		IOUtils.write("Windows Registry Editor Version 5.00\n", fos);
		IOUtils.write("\n", fos);
		IOUtils.write("[HKEY_LOCAL_MACHINE\\SYSTEM\\ControlSet001\\Control\\Network\\{4D36E972-E325-11CE-BFC1-08002BE10318}\\" 
				+ netCfgInstanceId + "\\Connection]\n", fos);
		IOUtils.write("\"Name\"=\"" + tapIf + "\"\n", fos);
		fos.close();
		
		new ProcessBuilder("cmd", "/C regedit.exe /s \"" + file.getAbsolutePath() + "\"").start().waitFor();
		file.delete();
	}

	private static String getRegLineValue(String regLine) {
		String regLineValue = regLine.split("=")[1];
		return regLineValue.substring(1, regLineValue.length() - 1);
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
		ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/C bin\\devcon.exe " + command);
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

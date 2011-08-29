package org.ourgrid.virt.strategies;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;

public class HypervisorUtils {

	/**
	 * Runs a process and checks whether the process finished with exit value 0.
	 * If not, it throws an exception
	 * 
	 * @param processBuilder
	 * @throws Exception
	 */
	public static void runAndCheckProcess(ProcessBuilder processBuilder)
			throws Exception {
		HypervisorUtils.runAndCheckProcess(processBuilder, "");
	}

	/**
	 * Runs a process and checks whether the process finished with exit value 0, 
	 * and the stdout contains the expectedMessage.
	 * If not, it throws an exception
	 * 
	 * @param processBuilder
	 * @param expectedMessage
	 * @throws Exception
	 */
	public static void runAndCheckProcess(ProcessBuilder processBuilder, String expectedMessage)
			throws Exception {
		
		ExecutionResult executionResult = HypervisorUtils.runProcess(processBuilder);
		
		List<String> stdOut = executionResult.getStdOut();
		List<String> stdErr = executionResult.getStdErr();
		
		if (executionResult.getReturnValue() != 0) {
			throw new IOException(stdErr.toString());
		}
		
		HypervisorUtils.checkExpectedMessage(expectedMessage, stdOut);
	}

	public static void checkExpectedMessage(String expectedMessage,
			List<String> stdOut) throws IOException {
		
		if (expectedMessage.isEmpty()) {
			return;
		}
		
		for (String line : stdOut) {
			if (line.toLowerCase().contains(
					expectedMessage.toLowerCase())) {
				return;
			}
		}
		
		throw new IOException();
	}

	public static ExecutionResult runProcess(
			ProcessBuilder processBuilder)
			throws Exception {
		
		final Process startedProcess = processBuilder.start();
		
		List<String> stdOut = readStdOut(startedProcess);
		List<String> stdErr = readStdErr(startedProcess);
		
		int returnValue = startedProcess.waitFor();
		
		ExecutionResult executionResult = new ExecutionResult();
		
		executionResult.setReturnValue(returnValue);
		executionResult.setStdErr(stdErr);
		executionResult.setStdOut(stdOut);
		
		return executionResult;
	}
	
	private static List<String> readStdOut(final Process startProcess)
			throws Exception {
		return readStream(startProcess, startProcess.getInputStream());
	}
	
	private static List<String> readStdErr(final Process startProcess)
			throws Exception {
		return readStream(startProcess, startProcess.getErrorStream());
	}
	
	private static List<String> readStream(final Process process, final InputStream stream) 
			throws Exception {
		
		Callable<List<String>> readLineCallable = new Callable<List<String>>() {
			@Override
			public List<String> call() throws Exception {
				return IOUtils.readLines(stream);
			}
		};
		
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<List<String>> future = executor.submit(
				readLineCallable);
		
		List<String> readLines = new LinkedList<String>();
		try {
			readLines = future.get(10, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
		} finally {
			future.cancel(true);
			executor.shutdownNow();
		}
		
		return readLines;
	}

	public static boolean isWindowsHost() {
		return checkHostOS("windows");
	}

	public static boolean isLinuxHost() {
		return checkHostOS("linux");
	}
	
	private static boolean checkHostOS(String osName) {
		return System.getProperty("os.name").toLowerCase().contains(osName);
	}
	
	public static boolean isWindowsGuest(VirtualMachine virtualMachine) {
		return checkGuestOS(virtualMachine, "windows");
	}
	
	public static boolean isLinuxGuest(VirtualMachine virtualMachine) {
		return checkGuestOS(virtualMachine, "linux");
	}
	
	private static boolean checkGuestOS(VirtualMachine virtualMachine, String osName) {
		String os = virtualMachine.getProperty(VirtualMachineConstants.OS);
		if (os == null) {
			return false;
		}
		
		return os.toLowerCase().contains(osName);
	}
}

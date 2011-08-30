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
		
		checkReturnValue(executionResult);
		checkExpectedMessage(expectedMessage, 
				executionResult);
	}

	public static void checkReturnValue(ExecutionResult executionResult) 
			throws IOException {
		if (executionResult.getReturnValue() != ExecutionResult.OK) {
			throw new IOException(executionResult.getStdErr().toString());
		}
	}

	public static void checkExpectedMessage(String expectedMessage,
			ExecutionResult executionResult) throws IOException {
		
		if (expectedMessage.isEmpty()) {
			return;
		}
		
		for (String line : executionResult.getStdOut()) {
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
		
		Callable<List<String>> stdOutCallable = createStreamCallable(
				startedProcess.getInputStream());
		Callable<List<String>> stdErrCallable = createStreamCallable(
				startedProcess.getErrorStream());
		
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Future<List<String>> stdOutFuture = executor.submit(stdOutCallable);
		Future<List<String>> stdErrFuture = executor.submit(stdErrCallable);
		
		int returnValue = startedProcess.waitFor();
		
		ExecutionResult executionResult = new ExecutionResult();
		
		executionResult.setReturnValue(returnValue);
		executionResult.setStdOut(readStream(stdOutFuture));
		executionResult.setStdErr(readStream(stdErrFuture));
		
		executor.shutdownNow();
		
		return executionResult;
	}
	
	private static List<String> readStream(Future<List<String>> future) 
			throws Exception {
		
		List<String> readLines = new LinkedList<String>();
		try {
			readLines = future.get(5, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
		} finally {
			future.cancel(true);
		}
		
		return readLines;
	}

	private static Callable<List<String>> createStreamCallable(
			final InputStream stream) {
		Callable<List<String>> readLineCallable = new Callable<List<String>>() {
			@Override
			public List<String> call() throws Exception {
				return IOUtils.readLines(stream);
			}
		};
		return readLineCallable;
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

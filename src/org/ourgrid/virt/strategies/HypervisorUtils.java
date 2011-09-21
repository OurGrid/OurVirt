package org.ourgrid.virt.strategies;

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
import org.ourgrid.virt.strategies.vbox.VBoxStrategy;

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
		ExecutionResult executionResult = HypervisorUtils.runProcess(processBuilder);
		checkReturnValue(executionResult);
	}

	public static void checkReturnValue(ExecutionResult executionResult) 
			throws Exception {
		if (executionResult.getReturnValue() != ExecutionResult.OK) {
			if ( !executionResult.getStdErr().contains(VBoxStrategy.COPY_ERROR) ){
				throw new Exception(executionResult.getStdErr().toString());
			}
		}
	}

	public static ExecutionResult runProcess(
			ProcessBuilder processBuilder)
			throws Exception {
		
		final Process startedProcess = processBuilder.start();
		
		Callable<List<String>> stdOutCallable = createStreamCallable(
				startedProcess.getInputStream());
		Callable<List<String>> stdErrCallable = createStreamCallable(
				startedProcess.getErrorStream());
		Callable<Integer> waitForCallable = createWaitForCallable(
				startedProcess);
		
		ExecutorService executor = Executors.newFixedThreadPool(3);
		Future<List<String>> stdOutFuture = executor.submit(stdOutCallable);
		Future<List<String>> stdErrFuture = executor.submit(stdErrCallable);
		Future<Integer> waitForFuture = executor.submit(waitForCallable);
		
		ExecutionResult executionResult = new ExecutionResult();
		
		executionResult.setStdOut(getStreamCallableResult(stdOutFuture));
		executionResult.setStdErr(getStreamCallableResult(stdErrFuture));
		executionResult.setReturnValue(getWaitForCallableResult(waitForFuture));
		
		executor.shutdownNow();
		
		return executionResult;
	}
	
	private static List<String> getStreamCallableResult(Future<List<String>> future) throws Exception {
		List<String> streamResult = readCallableResult(future);
		return streamResult == null ? new LinkedList<String>() : streamResult;
	}
	
	private static Integer getWaitForCallableResult(Future<Integer> future) throws Exception {
		Integer waitForResult = readCallableResult(future);
		return waitForResult == null ? -1 : waitForResult;
	}
	
	private static <T> T  readCallableResult(Future<T> future) 
			throws Exception {
		
		try {
			return future.get(5, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			return null;
		} finally {
			future.cancel(true);
		}
		
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

	private static Callable<Integer> createWaitForCallable(
			final Process startedProcess) {
		Callable<Integer> waitForCallable = new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				return startedProcess.waitFor();
			}
		};
		return waitForCallable;
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

package org.ourgrid.virt.strategies;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.NetworkStats;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineConstants;

public class HypervisorUtils {

	/**
	 * Runs a process and checks whether the process finished with exit value 0.
	 * If not, it throws an exception.
	 * 
	 * @param processBuilder the builder of the process
	 * @throws Exception if exit value is different than 0
	 */
	public static void runAndCheckProcess(ProcessBuilder processBuilder)
			throws Exception {
		ExecutionResult executionResult = runProcess(processBuilder);
		checkReturnValue(executionResult);
	}

	/**
	 * Checks whether the execution result return value is 0.
	 * If not, throws an exception.
	 * @param executionResult the process execution result
	 * @throws Exception if exit value is different than 0
	 */
	public static void checkReturnValue(ExecutionResult executionResult) 
			throws Exception {
		int exitValue = executionResult.getReturnValue();
		if (exitValue != ExecutionResult.OK) {
			throw new Exception("Exit value: " + exitValue + ", StdErr: "
					+ executionResult.getStdErr().toString() + ", StdOut: "
					+ executionResult.getStdOut().toString());
		}
	}

	/**
	 * Runs the process builded by the given process builder.
	 * @param processBuilder the builder of the process
	 * @return the process execution result
	 * @throws Exception if a problem occurs amidst execution of the process
	 */
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
		
		executionResult.setReturnValue(getWaitForCallableResult(waitForFuture));
		executionResult.setStdOut(getStreamCallableResult(stdOutFuture));
		executionResult.setStdErr(getStreamCallableResult(stdErrFuture));
		
		executor.shutdownNow();
		
		return executionResult;
	}
	
	private static List<String> getStreamCallableResult(Future<List<String>> future) throws Exception {
		List<String> streamResult = readCallableResult(future);
		return streamResult == null ? new LinkedList<String>() : streamResult;
	}
	
	private static Integer getWaitForCallableResult(Future<Integer> future) throws Exception {
		Integer waitForResult = waitForCallableResult(future);
		return waitForResult == null ? -1 : waitForResult;
	}
	
	private static <T> T  readCallableResult(Future<T> future) 
			throws Exception {
		
		try {
			return future.get(60, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			return null;
		} finally {
			future.cancel(true);
		}
		
	}
	
	private static <T> T waitForCallableResult(Future<T> future)
			throws Exception {
		try {
			return future.get();
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
	
	/**
	 * Checks if host operational system is Windows.
	 * @return <b><i>true</b></i> if host operational system is Windows, <b><i>false</b></i> otherwise.
	 */
	public static boolean isWindowsHost() {
		return checkHostOS("windows");
	}

	/**
	 * Checks if host operational system is Linux.
	 * @return <b><i>true</b></i> if host operational system is Linux, <b><i>false</b></i> otherwise.
	 */
	public static boolean isLinuxHost() {
		return checkHostOS("linux");
	}
	
	/**
	 * Checks if host operational system matches given operational system name.
	 * @param osName the operational system name
	 * @return <b><i>true</b></i> if host operational system matches given name, <b><i>false</b></i> otherwise.
	 */
	private static boolean checkHostOS(String osName) {
		return System.getProperty("os.name").toLowerCase().contains(osName);
	}
	
	/**
	 * Checks if guest operational system is Windows.
	 * @param virtualMachine the virtual machine
 	 * @return <b><i>true</b></i> if guest operational system is Windows, <b><i>false</b></i> otherwise.
	 */
	public static boolean isWindowsGuest(VirtualMachine virtualMachine) {
		return checkGuestOS(virtualMachine, "windows");
	}
	
	/**
	 * Checks if guest operational system is Linux.
	 * @param virtualMachine the virtual machine
	 * @return <b><i>true</b></i> if guest operational system is Linux, <b><i>false</b></i> otherwise.
	 */
	public static boolean isLinuxGuest(VirtualMachine virtualMachine) {
		return checkGuestOS(virtualMachine, "linux");
	}
	
	/**
	 * Checks if guest operational system matches given operational system name.
	 * @param virtualMachine the virtual machine
	 * @param osName the operational system name
	 * @return <b><i>true</b></i> if guest operational system matches given name, <b><i>false</b></i> otherwise.
	 */
	private static boolean checkGuestOS(VirtualMachine virtualMachine, String osName) {
		String os = virtualMachine.getProperty(VirtualMachineConstants.OS);
		if (os == null) {
			return false;
		}
		
		return os.toLowerCase().contains(osName);
	}
	
	/**
	 * Splits the command string around blank spaces avoiding strings surrounded by quotation marks.
	 * @param vboxManageCmdLine the command to be handled
	 * @return a list of strings of the split command string pieces
	 */
	public static List<String> splitCmdLine(String vboxManageCmdLine) {
		List<String> matchList = new ArrayList<String>();
		Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
		Matcher regexMatcher = regex.matcher(vboxManageCmdLine);
		while (regexMatcher.find()) {
		    if (regexMatcher.group(1) != null) {
		        // Add double-quoted string without the quotes
		        matchList.add(regexMatcher.group(1));
		    } else if (regexMatcher.group(2) != null) {
		        // Add single-quoted string without the quotes
		        matchList.add(regexMatcher.group(2));
		    } else {
		        // Add unquoted word
		        matchList.add(regexMatcher.group());
		    }
		}
		return matchList;
	}

	public static NetworkStats getNetworkStats(VirtualMachine registeredVM) throws Exception {
		
		if (!registeredVM.getProperty(VirtualMachineConstants.NETWORK_TYPE)
				.equals(VirtualMachineConstants.BRIDGED_NET_MODE)) {
			return null;
		}
		
		String deviceName = registeredVM.getProperty(VirtualMachineConstants.BRIDGED_INTERFACE);
		
		StringBuilder cmdSB = new StringBuilder();
		
		cmdSB.append("cat /proc/net/dev | grep eth0");
		
		ProcessBuilder psProcessBuilder = 
				new ProcessBuilder("/bin/bash", "-c",cmdSB.toString());
		
		Process p = psProcessBuilder.start();
		
		int psExitValue = p.waitFor();
		//Getting the approximate timestamp of the cat process return
		long timestamp = System.currentTimeMillis();
		if (psExitValue != 0) {
			return null;
		}
		
		String[] ifStats = IOUtils.toString(p.getInputStream()).split("\\s+");
		
		NetworkStats networkStats = new NetworkStats();
		networkStats.setDeviceName(deviceName);
		networkStats.setTimestamp(timestamp);
		networkStats.setReceivedBytes(Long.parseLong(ifStats[2]));
		networkStats.setReceivedPackets(Long.parseLong(ifStats[3]));
		networkStats.setReceivedErrors(Long.parseLong(ifStats[4]));
		networkStats.setReceivedDropped(Long.parseLong(ifStats[5]));
		networkStats.setReceivedFIFOErrors(Long.parseLong(ifStats[6]));
		networkStats.setReceivedPktFramingErrors(Long.parseLong(ifStats[7]));
		networkStats.setReceivedCompressed(Long.parseLong(ifStats[8]));
		networkStats.setReceivedMulticast(Long.parseLong(ifStats[9]));
		
		networkStats.setTransferredBytes(Long.parseLong(ifStats[10]));
		networkStats.setTransferredPackets(Long.parseLong(ifStats[11]));
		networkStats.setTransferredErrors(Long.parseLong(ifStats[12]));
		networkStats.setTransferredDropped(Long.parseLong(ifStats[13]));
		networkStats.setTransferredFIFOErrors(Long.parseLong(ifStats[14]));
		networkStats.setTransferredCollisions(Long.parseLong(ifStats[15]));
		networkStats.setTransferredCarrierLosses(Long.parseLong(ifStats[16]));
		networkStats.setTransferredCompressed(Long.parseLong(ifStats[17]));
		
		return networkStats;
	}
}

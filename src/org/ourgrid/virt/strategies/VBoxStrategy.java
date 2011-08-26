package org.ourgrid.virt.strategies;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.ourgrid.virt.ExecutionResult;
import org.ourgrid.virt.VirtualMachine;

public class VBoxStrategy implements HypervisorStrategy {

	@Override
	public void start(VirtualMachine virtualMachine) throws Exception {
		
		ProcessBuilder startProcessBuilder = getProcessBuilder(
				"startvm " + virtualMachine.getName());
		runProcess(startProcessBuilder, "successfully started");
		
		while (true) {
			try {
				ExecutionResult executionResult = exec(
						virtualMachine, "/bin/echo check-started");
				checkExpectedMessage("check-started", 
						executionResult.getStdOut());
				break;
			} catch (Exception e) {}
			
			Thread.sleep(1000 * 10);
		}
	}

	@Override
	public void stop(String vMName) throws Exception {
		ProcessBuilder stopProcessBuilder = getProcessBuilder(
				"controlvm " + vMName + " poweroff");
		runProcess(stopProcessBuilder);
	}
	
	@Override
	public void createSharedFolder(String vMName) throws Exception {
		
	}

	@Override
	public ExecutionResult exec(VirtualMachine virtualMachine, String command) throws Exception {
		
		String user = virtualMachine.getConfiguration().get("user");
		String password = virtualMachine.getConfiguration().get("password");
		
		String[] splittedCommand = command.split(" ");
		
		StringBuilder cmdBuilder = new StringBuilder("--nologo guestcontrol ");
		cmdBuilder.append("\"").append(virtualMachine.getName()).append("\"");
		cmdBuilder.append(" exec --image ");
		cmdBuilder.append("\"").append(splittedCommand[0]).append("\"");
		cmdBuilder.append(" --username ");
		cmdBuilder.append(user);
		cmdBuilder.append(" --password ");
		cmdBuilder.append(password);
		cmdBuilder.append(" --wait-exit --wait-stdout");
		
		if (splittedCommand.length > 1) {
			cmdBuilder.append(" --");
			for (int i = 1; i < splittedCommand.length; i++) {
				cmdBuilder.append(" ").append(splittedCommand[i]);
			}
		}
		
		ProcessBuilder stopProcessBuilder = getProcessBuilder(cmdBuilder.toString());
		return runProcessReturning(stopProcessBuilder);
	}
	
	@Override
	public void takeSnapshot(String vMName, String snapshotName)
			throws Exception {
		ProcessBuilder takeSnapshotProcessBuilder = getProcessBuilder(
				"snapshot " + vMName + " take " + snapshotName);
		runProcess(takeSnapshotProcessBuilder);
	}
	
	@Override
	public void restoreSnapshot(String vMName, String snapshotName)
			throws Exception {
		ProcessBuilder restoreSnapshotProcessBuilder = getProcessBuilder(
				"snapshot " + vMName + " restore " + snapshotName);
		runProcess(restoreSnapshotProcessBuilder);
	}
	
	private static void runProcess(ProcessBuilder processBuilder)
			throws Exception {
		runProcess(processBuilder, "");
	}
	
	private static void runProcess(ProcessBuilder processBuilder, String expectedMessage)
			throws Exception {
		
		ExecutionResult executionResult = runProcessReturning(processBuilder);
		
		List<String> stdOut = executionResult.getStdOut();
		if (executionResult.getReturnValue() != 0) {
			throw new IOException(stdOut.toString());
		}
		
		checkExpectedMessage(expectedMessage, stdOut);
	}

	private static void checkExpectedMessage(String expectedMessage,
			List<String> stdOut) throws IOException {
		
		for (String line : stdOut) {
			if (line.toLowerCase().contains(
					expectedMessage.toLowerCase())) {
				return;
			}
		}
		
		throw new IOException();
	}

	private static ExecutionResult runProcessReturning(
			ProcessBuilder processBuilder)
			throws Exception {
		
		final Process startedProcess = processBuilder.start();
		
		int returnValue = startedProcess.waitFor();
		
		List<String> stdOut = readStdOut(startedProcess);
		List<String> stdErr = readStdErr(startedProcess);
		
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
		
		Future<List<String>> future = Executors.newSingleThreadExecutor().submit(
				readLineCallable);
		
		List<String> readLines = new LinkedList<String>();
		try {
			readLines = future.get(10, TimeUnit.SECONDS);
		} catch (TimeoutException e) {}
		return readLines;
	}
	
	private static ProcessBuilder getProcessBuilder(String cmd) {
		String osName = System.getProperty("os.name");
		
		String vboxManageCmdLine = "VBoxManage " + cmd;
		
		ProcessBuilder processBuilder = null;
		
		if (osName.toLowerCase().contains("windows")) {
			processBuilder = new ProcessBuilder("cmd", "/C start /B " + vboxManageCmdLine);
		} else {
			processBuilder =  new ProcessBuilder(vboxManageCmdLine);
		}
		
		processBuilder.directory(new File(
				System.getenv().get("VBOX_INSTALL_PATH")));
		
		return processBuilder;
	}

}

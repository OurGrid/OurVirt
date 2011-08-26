package org.ourgrid.virt.strategies;

import org.ourgrid.virt.ExecutionResult;
import org.ourgrid.virt.VirtualMachine;


public interface HypervisorStrategy {

	public void start(VirtualMachine virtualMachine) throws Exception;

	public void stop(String vMName) throws Exception;

	public void createSharedFolder(String vMName) throws Exception;

	public void takeSnapshot(String vMName, String snapshotName)
			throws Exception;

	public void restoreSnapshot(String vMName, String snapshotName)
			throws Exception;

	public ExecutionResult exec(VirtualMachine virtualMachine, String command)
			throws Exception;
	
}

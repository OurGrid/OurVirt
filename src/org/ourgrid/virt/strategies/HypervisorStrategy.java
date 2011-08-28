package org.ourgrid.virt.strategies;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;


public interface HypervisorStrategy {

	public void start(VirtualMachine virtualMachine) throws Exception;

	public void stop(VirtualMachine virtualMachine) throws Exception;

	public void createSharedFolder(VirtualMachine virtualMachine, String hostPath, String guestPath) throws Exception;

	public void takeSnapshot(String vMName, String snapshotName)
			throws Exception;

	public void restoreSnapshot(String vMName, String snapshotName)
			throws Exception;

	public ExecutionResult exec(VirtualMachine virtualMachine, String command)
			throws Exception;

	public void create(VirtualMachine virtualMachine) throws Exception;
	
	public void destroy(VirtualMachine virtualMachine) throws Exception;
	
}

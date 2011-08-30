package org.ourgrid.virt.strategies;

import java.util.List;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineStatus;


public interface HypervisorStrategy {

	public void start(VirtualMachine virtualMachine) throws Exception;

	public void stop(VirtualMachine virtualMachine) throws Exception;

	public VirtualMachineStatus status(VirtualMachine virtualMachine) throws Exception;
	
	public void createSharedFolder(VirtualMachine virtualMachine, String shareName, 
			String hostPath, String guestPath) throws Exception;

	public void createSharedFolder(VirtualMachine virtualMachine, String shareName,
			String hostPath) throws Exception;
	
	public void takeSnapshot(String vMName, String snapshotName)
			throws Exception;

	public void restoreSnapshot(String vMName, String snapshotName)
			throws Exception;

	public ExecutionResult exec(VirtualMachine virtualMachine, String command)
			throws Exception;

	public void create(VirtualMachine virtualMachine) throws Exception;
	
	public void destroy(VirtualMachine virtualMachine) throws Exception;
	
	public List<String> listVMs() throws Exception;
	
	public List<String> listSnapshots(VirtualMachine virtualMachine) throws Exception;
	
	public List<String> listSharedFolders(VirtualMachine virtualMachine) throws Exception;
	
	public boolean isSupported();

	void mountSharedFolder(VirtualMachine virtualMachine, String name,
			String guestPath) throws Exception;

}

package org.ourgrid.virt.strategies;

import java.util.List;
import java.util.Map;

import org.ourgrid.virt.model.DiskStats;
import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.NetworkStats;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineStatus;

/**
 * Hypervisor strategies interface. This interface provides 
 * the signature of methods which will be implemented by specific
 * hypervisor strategies.
 */
public interface HypervisorStrategy {

	/**
	 * Starts this virtual machine, if it is not started yet.
	 * @param virtualMachine the related virtual machine 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to start the related virtual machine 
	 */
	public void start(VirtualMachine virtualMachine) throws Exception;

	/**
	 * Stops the specified virtual machine, if it is not stopped yet.
	 * @param virtualMachine the related virtual machine 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to stop the related virtual machine 
	 */
	public void stop(VirtualMachine virtualMachine) throws Exception;
	
	
	/**
	 * Reboots the specified virtual machine, if it is already started.
	 * @param virtualMachine the related virtual machine
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to reboot the related virtual machine
	 */
	public void reboot(VirtualMachine virtualMachine) throws Exception;
	
	/**
	 * Retrieves the status of the specified virtual machine.
	 * @param virtualMachine the related virtual machine 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to retrieve the status of the related virtual machine 
	 */
	public VirtualMachineStatus status(VirtualMachine virtualMachine) throws Exception;
	
	
	/**
	 * Creates a configuration for a new shared folder within the hypervisor.
	 * If a shared folder with given name already exists, this method only updates it.
	 * @param virtualMachine the related virtual machine 
	 * @param shareName the name identifier of the shared folder to be created
	 * @param hostPath the path in the host to be shared
	 * @param guestPath the path in the guest to be shared
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to create the shared folder within the related virtual machine
	 */
	public void createSharedFolder(VirtualMachine virtualMachine,
			String shareName, String hostPath, String guestPath) throws Exception;

	/**
	 * Takes a snapshot of the current state of the specified virtual machine, with given snapshot name.
	 * <b>This method expects the virtual machine to be stopped.</b>
	 * @param virtualMachine the related virtual machine
	 * @param snapshotName the name identifier of the snapshot to be taken
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to take the snapshot of the related virtual machine 
	 */
	public void takeSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception;

	/**
	 * Restores the specified virtual machine to the state of taken snapshot, specified by the snapshot name.
	 * <b>This method expects the virtual machine to be stopped. If not, it will stop it first.</b>
	 * @param virtualMachine the related virtual machine
	 * @param snapshotName the name identifier of the snapshot to be restored
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to restore the snapshot of the related virtual machine 
	 */
	public void restoreSnapshot(VirtualMachine virtualMachine, String snapshotName)
			throws Exception;

	/**
	 * Executes the specified command within the specified virtual machine.
	 * <b>This method expects the virtual machine to be started.</b>
	 * @param virtualMachine the related virtual machine 
	 * @param command the command to be executed
	 * @return the execution result containing its output and exit value
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to execute the specified command within the related virtual machine 
	 */
	public ExecutionResult exec(VirtualMachine virtualMachine, String command)
			throws Exception;

	/**
	 * Makes the hypervisor create the specified virtual machine, if it does not exist yet.
	 * @param virtualMachine the related virtual machine 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to create the related virtual machine 
	 */
	public void create(VirtualMachine virtualMachine) throws Exception;
	
	/**
	 * Destroys the registered virtual machine. After this method is called, the <i>create</i> method
	 * <b>must be</b> called in order to make the related virtual machine  available for use again,
	 * although its configuration still remains in the OurVirt volatile memory.
	 * This means that the <i>register</i> method <b>is not</b> needed again during the current execution.
	 * <b>This method expects the virtual machine to be stopped. If not, it will stop it first.</b>
	 * @param virtualMachine the related virtual machine 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to destroy the related virtual machine 
	 */
	public void destroy(VirtualMachine virtualMachine) throws Exception;
	
	/**
	 * Lists the hypervisor existing registered virtual machines.
	 * Only the related virtual machine s which have been created by the hypervisor will be listed.
	 * @return the virtual machines names list 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to list the virtual machines
	 */
	public List<String> listVMs() throws Exception;
	
	
	/**
	 * Lists the existing snapshots of the specified virtual machine.
	 * This method only lists the snapshots which were taken by OurVirt.
	 * @param virtualMachine the related virtual machine 
	 * @return the virtual machines names list 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to list the snapshots of the specified virtual machine
	 */
	public List<String> listSnapshots(VirtualMachine virtualMachine) throws Exception;
	
	/**
	 * Lists the existing shared folders of the specified virtual machine.
	 * This method only lists the shared folders which were created by OurVirt.
	 * @param virtualMachine the related virtual machine 
	 * @return the shared folders names list 
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to list the shared folders of the specified virtual machine
	 */
	public List<String> listSharedFolders(VirtualMachine virtualMachine) throws Exception;
	
	/**
	 * Check whether the hypervisor is supported by the host <i>(If it is installed)</i>.
	 * @return <i><b>true</b></i> if the hypervisor is supported, <i><b>false</b></i> otherwise. 
	 */
	public boolean isSupported();
	
	/**
	 * @param virtualMachine the related virtual machine
	 * @return the CPUTime for the specified virtual machine process.
	 * @throws Exception if the hypervisor does not support this method 
	 * or if some problem occurs while trying to get the CPUTime for the specified virtual machine process.
	 */
	public long getCPUStats(VirtualMachine virtualMachine) throws Exception;

	/**
	 * Mounts the specified shared folder in the related virtual machine .
	 * <b>This method expects the virtual machine to be started.</b>
	 * @param virtualMachine the related virtual machine 
	 * @param shareName the name identifier of the shared folder to be mounted
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to mount the shared folder within the specified virtual machine
	 */
	void mountSharedFolder(VirtualMachine virtualMachine, String shareName,
			String hostPath, String guestPath) throws Exception;
	
	/**
	 * Unmounts the specified shared folder in the related virtual machine .
	 * After this method is called, it <b>is not</b> necessary to call the 
	 * <i>createSharedFolder</i> method again in order to be able to mount it.
	 * <b>This method expects the virtual machine to be started.</b>
	 * @param virtualMachine the related virtual machine 
	 * @param shareName the name identifier of the shared folder to be unmounted
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to unmount the shared folder within the specified virtual machine
	 */
	void unmountSharedFolder(VirtualMachine virtualMachine, String shareName, 
			String hostPath, String guestPath) throws Exception;

	/**
	 * Prepares the environment for ourvirt user to be able to manage virtual machines using this hypervisor.
	 */
	void prepareEnvironment(Map<String, String> props) throws Exception;

	/**
	 * Deletes the specified shared folder.
	 * 
	 * @param registeredVM
	 * @param shareName
	 */
	void deleteSharedFolder(VirtualMachine registeredVM, String shareName) throws Exception;
	
	void clone(String sourceDevice, String destDevice) throws Exception;

	Object getProperty(VirtualMachine registeredVM, String propertyName) throws Exception;

	void setProperty(VirtualMachine registeredVM, String propertyName,
			Object propertyValue) throws Exception;

	public DiskStats getDiskStats(VirtualMachine registeredVM) throws Exception;

}

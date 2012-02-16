package org.ourgrid.virt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ourgrid.virt.model.ExecutionResult;
import org.ourgrid.virt.model.HypervisorType;
import org.ourgrid.virt.model.VirtualMachine;
import org.ourgrid.virt.model.VirtualMachineStatus;
import org.ourgrid.virt.strategies.HypervisorStrategyFactory;


/**
 * OurVirt facade.
 * The purpose of this library is to provide management of virtual machines from
 * the many different existing hypervisors with ease.
 * This class contains all the necessary methods to manage virtual machines,
 * such as starting and stopping them, as well as other advanced features as taking snapshots of
 * a machine state, creating and mounting shared folders.
 */
public class OurVirt {

	private Map<String, VirtualMachine> vMCache = new HashMap<String, VirtualMachine>();
	private HypervisorStrategyFactory factory = new HypervisorStrategyFactory();
	
	/**
	 * Registers a new virtual machine with the specified name and configuration in OurVirt volatile memory.
	 * It does not make the specified hypervisor create the virtual machine.
	 * To create it, the <i>create</i> method must be called.
	 * @param vmName the name identifier of the virtual machine
	 * @param configuration the configuration map
	 * @see OurVirt#create(HypervisorType, String)
	 */
	public void register(String vmName, Map<String, String> configuration) {
		VirtualMachine vm = new VirtualMachine(vmName);
		vm.setConfiguration(configuration);
		vMCache.put(vmName, vm);
	}
	
	/**
	 * Creates the registered virtual machine, if it does not exist yet.
 	 * This method must be called <b>after</b> the <i>register</i> method and is necessary in order 
	 * to actually make the specified hypervisor create the virtual machine.
	 * The register method just specified the configuration
	 * to be applied to the virtual machine when it is created.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to create the virtual machine
	 * @see OurVirt#register(String, Map)
	 */
	public void create(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).create(getRegisteredVM(vmName));
	}
	
	/**
	 * Starts the registered virtual machine.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to start the virtual machine
	 */
	public void start(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).start(getRegisteredVM(vmName));
	}

	private VirtualMachine getRegisteredVM(String vmName) throws Exception{
		VirtualMachine virtualMachine = vMCache.get(vmName);
		if ( virtualMachine == null ){
			throw new Exception("Virtual machine [ " + vmName + " ] is unregistered.");
		}
		return virtualMachine;
	}

	/**
	 * Stops the registered virtual machine.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to stop the virtual machine
	 */
	public void stop(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).stop(getRegisteredVM(vmName));
	}

	/**
	 * Retrieves the status of the registered virtual machine.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to retrieve the status of the virtual machine
	 */
	public VirtualMachineStatus status(HypervisorType hypervisor, String vmName) throws Exception {
		VirtualMachine virtualMachine = vMCache.get(vmName);
		if (virtualMachine == null) {
			return VirtualMachineStatus.NOT_REGISTERED;
		}
		
		return factory.get(hypervisor).status(virtualMachine);
	}
	
	/**
	 * Executes the specified command within the registered virtual machine.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param command the command to be executed
	 * @return the execution result containing its output and exit value
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to execute the command within the virtual machine
	 */
	public ExecutionResult exec(HypervisorType hypervisor, String vmName, String command) throws Exception {
		return factory.get(hypervisor).exec(getRegisteredVM(vmName), command);
	}
	
	/**
	 * Takes a snapshot of the current state of the registered virtual machine, with given snapshot name.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param snapshotName the name identifier of this virtual machine snapshot
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to take a snapshot of the virtual machine
	 */
	public void takeSnapshot(HypervisorType hypervisor, 
			String vmName, String snapshotName) throws Exception {
		factory.get(hypervisor).takeSnapshot(getRegisteredVM(vmName), snapshotName);
	}
	
	/**
	 * Restores the registered virtual machine to the state of taken snapshot, specified by the snapshot name.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param snapshotName the name identifier of this virtual machine snapshot
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to restore a snapshot of the virtual machine
	 */
	public void restoreSnapshot(HypervisorType hypervisor, 
			String vmName, String snapshotName) throws Exception {
		factory.get(hypervisor).restoreSnapshot(getRegisteredVM(vmName), snapshotName);
	}
	
	/**
	 * Destroys the registered virtual machine. After this method is called, the <i>create</i> method
	 * <b>must be</b> called in order to make the virtual machine available for use again,
	 * although its configuration still remains in the OurVirt volatile memory.
	 * This means that the <i>register</i> method <b>is not</b> needed again during the current execution.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to destroy the virtual machine
	 */
	public void destroy(HypervisorType hypervisor, String vmName) throws Exception {
		factory.get(hypervisor).destroy(getRegisteredVM(vmName));
	}
	
	/**
	 * Lists the existing registered virtual machines for the given hypervisor.
	 * Only the virtual machines which have been created by the hypervisor will be listed.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @return the list of virtual machine names
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to list the existing registered virtual machines
	 */
	public List<String> list(HypervisorType hypervisor) throws Exception {
		return factory.get(hypervisor).listVMs();
	}
	
	/**
	 * Check whether the specified hypervisor is supported by the host <i>(If it is installed)</i>.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @return <i><b>true</b></i> if hypervisor is supported, <i><b>false</i></b> otherwise.
	 * @throws Exception if some problem occurs while trying to check whether the host supports this hypervisor
	 */
	public boolean isSupported(HypervisorType hypervisor) throws Exception {
		return factory.get(hypervisor).isSupported();
	}
	
	/**
	 * Lists the existing snapshots of the registered virtual machine.
	 * This method only lists the snapshots which were taken by OurVirt.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @return the list of snapshot names
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to list the existing snapshots
	 * of the registered virtual machine
	 */
	public List<String> listSnapshots(HypervisorType hypervisor, String vmName) throws Exception {
		return factory.get(hypervisor).listSnapshots(getRegisteredVM(vmName));
	}
	
	/**
	 * Lists the existing shared folders of the registered virtual machine.
	 * This method only lists the shared folders which were created by OurVirt.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @return the list of shared folders names
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to list the existing shared folders
	 * of the registered virtual machine
	 */
	public List<String> listSharedFolders(HypervisorType hypervisor, String vmName) throws Exception {
		return factory.get(hypervisor).listSharedFolders(getRegisteredVM(vmName));
	}
	
	/**
	 * Registers a shared folder with given name in the OurVirt volatile memory and creates a configuration
	 * for a new shared folder within the specified hypervisor.
	 * If a shared folder with given name already exists, this method only updates it.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param shareName the name identifier of the shared folder
	 * @param hostPath the path of a folder in the host to be shared
	 * @param guestPath the path of a folder in the guest to be shared
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to create the shared folder
	 * for the registered virtual machine
	 */
	public void createSharedFolder(HypervisorType hypervisor, String vmName, 
			String shareName, String hostPath, String guestPath) throws Exception {
		factory.get(hypervisor).createSharedFolder(getRegisteredVM(vmName), shareName, hostPath, guestPath);
	}
	
	/**
	 * Removes a shared folder with given name in the OurVirt volatile memory and deletes it 
	 * from the configuration within the specified hypervisor.
	 * If the shared folder with given name does not exist, this method will return silently.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param shareName the name identifier of the shared folder
	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to delete the shared folder
	 * for the registered virtual machine
	 */
	public void deleteSharedFolder(HypervisorType hypervisor, String vmName, 
			String shareName) throws Exception {
		factory.get(hypervisor).deleteSharedFolder(getRegisteredVM(vmName), shareName);
	}
	
	/**
	 * Mounts the specified shared folder in the virtual machine.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param shareName the name identifier of the shared folder
 	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to mount the shared folder
	 * for the registered virtual machine
	 */
	public void mountSharedFolder(HypervisorType hypervisor, String vmName, String shareName) throws Exception {
		factory.get(hypervisor).mountSharedFolder(getRegisteredVM(vmName), shareName);
	}
	
	/**
	 * Unmounts the specified shared folder in the virtual machine.
	 * After this method is called, it <b>is not</b> necessary to call the 
	 * <i>createSharedFolder</i> method again in order to be able to mount it.
	 * @param hypervisor the hypervisor used to manage the virtual machine
	 * @param vmName the name identifier of the virtual machine
	 * @param shareName the name identifier of the shared folder
 	 * @throws Exception if the hypervisor does not support this method
	 * or if some problem occurs while trying to unmount the shared folder
	 * for the registered virtual machine
	 */
	public void unmountSharedFolder(HypervisorType hypervisor, String vmName, String shareName) throws Exception {
		factory.get(hypervisor).unmountSharedFolder(getRegisteredVM(vmName), shareName);
	}
	
	/**
	 * Prepares the environment for the user indicated by the user parameter
	 * to use the hypervisor type. You will probably need admin privileges
	 * to run this method. 
	 * @param hypervisor the hypervisor you want to prepare the environment for
	 * @param userName the user that will be ready to use the hypervisor methods
	 * @throws Exception probably if you don't have admin privileges or the underlying
	 * syetem is not supported by OurVirt.
	 */
	public void prepareEnvironment(HypervisorType hypervisor, String userName) throws Exception {
		factory.get(hypervisor).prepareEnvironment(userName);
	}
	
	public void clone(HypervisorType hypervisor, String sourceDevice, String destDevice) throws Exception {
		factory.get(hypervisor).clone(sourceDevice, destDevice);
	}
}

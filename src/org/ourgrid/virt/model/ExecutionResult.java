package org.ourgrid.virt.model;

import java.util.List;

/**
 * The objects of this class are used to hold the execution result of a command, 
 * including its return value, standard output and standard error.
 */
public class ExecutionResult {

	public static final int OK = 0;
	
	private int returnValue;
	private List<String> stdOut;
	private List<String> stdErr;
	
	/**
	 * @return the return value of the execution result
	 */
	public int getReturnValue() {
		return returnValue;
	}
	
	/**
	 * Sets the return value of the execution result
	 * @param returnValue the return value to be set
	 */
	public void setReturnValue(int returnValue) {
		this.returnValue = returnValue;
	}
	
	/**
	 * @return the standard output of the execution result
	 */
	public List<String> getStdOut() {
		return stdOut;
	}
	
	public void setStdOut(List<String> stdOut) {
		this.stdOut = stdOut;
	}
	
	public List<String> getStdErr() {
		return stdErr;
	}
	
	public void setStdErr(List<String> stdErr) {
		this.stdErr = stdErr;
	}
}

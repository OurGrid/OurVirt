package org.ourgrid.virt;

import java.util.List;

public class ExecutionResult {

	private int returnValue;
	private List<String> stdOut;
	private List<String> stdErr;
	
	public int getReturnValue() {
		return returnValue;
	}
	
	public void setReturnValue(int returnValue) {
		this.returnValue = returnValue;
	}
	
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

package org.ourgrid.virt.strategies;


public interface HypervisorStrategy {

	public void start(String vMName) throws Exception;
	
	public void stop(String vMName) throws Exception;
	
}

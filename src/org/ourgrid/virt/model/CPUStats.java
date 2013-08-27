package org.ourgrid.virt.model;

public class CPUStats {
	
	private long timestamp;
	private long cpuTime;
	
	public CPUStats() {}
	
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public long getCpuTime() {
		return cpuTime;
	}
	public void setCpuTime(long cpuTime) {
		this.cpuTime = cpuTime;
	}
}

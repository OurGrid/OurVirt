package org.ourgrid.virt.model;

public class DiskStats {
	
	private String deviceName;
	
	private long readTotalTime;
	private long readOps;
	private long readBytes;
	private long writeTotalTime;
	private long writeOps;
	private long writeBytes;
	
	
	public DiskStats() {}
	
	public long getReadTotalTime() {
		return readTotalTime;
	}
	public void setReadTotalTime(long readTotalTime) {
		this.readTotalTime = readTotalTime;
	}
	public long getReadOps() {
		return readOps;
	}
	public void setReadOps(long readOps) {
		this.readOps = readOps;
	}
	public long getReadBytes() {
		return readBytes;
	}
	public void setReadBytes(long readBytes) {
		this.readBytes = readBytes;
	}
	public long getWriteTotalTime() {
		return writeTotalTime;
	}
	public void setWriteTotalTime(long writeTotalTime) {
		this.writeTotalTime = writeTotalTime;
	}
	public long getWriteOps() {
		return writeOps;
	}
	public void setWriteOps(long writeOps) {
		this.writeOps = writeOps;
	}
	public long getWriteBytes() {
		return writeBytes;
	}
	public void setWriteBytes(long writeBytes) {
		this.writeBytes = writeBytes;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}
}

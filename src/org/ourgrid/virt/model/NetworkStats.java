package org.ourgrid.virt.model;

public class NetworkStats {
	
	private String deviceName;
	private long timestamp;

	private long receivedBytes;
	private long receivedPackets;
	private long receivedErrors;
	private long receivedDropped;
	private long receivedFIFOErrors;
	private long receivedPktFramingErrors;
	private long receivedCompressed;
	private long receivedMulticast;
	
	private long transferredBytes;
	private long transferredPackets;
	private long transferredErrors;
	private long transferredDropped;
	private long transferredFIFOErrors;
	private long transferredCollisions;
	private long transferredCarrierLosses;
	private long transferredCompressed;
	
	public NetworkStats() {
		super();
	}

	public String getDeviceName() {
		return deviceName;
	}

	public void setDeviceName(String deviceName) {
		this.deviceName = deviceName;
	}

	public long getReceivedBytes() {
		return receivedBytes;
	}

	public void setReceivedBytes(long receivedBytes) {
		this.receivedBytes = receivedBytes;
	}

	public long getTransferredBytes() {
		return transferredBytes;
	}

	public void setTransferredBytes(long transferredBytes) {
		this.transferredBytes = transferredBytes;
	}

	public long getReceivedPackets() {
		return receivedPackets;
	}

	public void setReceivedPackets(long receivedPackets) {
		this.receivedPackets = receivedPackets;
	}

	public long getReceivedErrors() {
		return receivedErrors;
	}

	public void setReceivedErrors(long receivedErrors) {
		this.receivedErrors = receivedErrors;
	}

	public long getReceivedDropped() {
		return receivedDropped;
	}

	public void setReceivedDropped(long receivedDropped) {
		this.receivedDropped = receivedDropped;
	}

	public long getReceivedFIFOErrors() {
		return receivedFIFOErrors;
	}

	public void setReceivedFIFOErrors(long receivedFIFOErrors) {
		this.receivedFIFOErrors = receivedFIFOErrors;
	}

	public long getReceivedPktFramingErrors() {
		return receivedPktFramingErrors;
	}

	public void setReceivedPktFramingErrors(long receivedPktFramingErrors) {
		this.receivedPktFramingErrors = receivedPktFramingErrors;
	}

	public long getReceivedCompressed() {
		return receivedCompressed;
	}

	public void setReceivedCompressed(long receivedCompressed) {
		this.receivedCompressed = receivedCompressed;
	}

	public long getReceivedMulticast() {
		return receivedMulticast;
	}

	public void setReceivedMulticast(long receivedMulticast) {
		this.receivedMulticast = receivedMulticast;
	}

	public long getTransferredPackets() {
		return transferredPackets;
	}

	public void setTransferredPackets(long transferredPackets) {
		this.transferredPackets = transferredPackets;
	}

	public long getTransferredErrors() {
		return transferredErrors;
	}

	public void setTransferredErrors(long transferredErrors) {
		this.transferredErrors = transferredErrors;
	}

	public long getTransferredDropped() {
		return transferredDropped;
	}

	public void setTransferredDropped(long transferredDropped) {
		this.transferredDropped = transferredDropped;
	}

	public long getTransferredFIFOErrors() {
		return transferredFIFOErrors;
	}

	public void setTransferredFIFOErrors(long transferredFIFOErrors) {
		this.transferredFIFOErrors = transferredFIFOErrors;
	}

	public long getTransferredCollisions() {
		return transferredCollisions;
	}

	public void setTransferredCollisions(long transferredCollisions) {
		this.transferredCollisions = transferredCollisions;
	}

	public long getTransferredCarrierLosses() {
		return transferredCarrierLosses;
	}

	public void setTransferredCarrierLosses(long transferredCarrierLosses) {
		this.transferredCarrierLosses = transferredCarrierLosses;
	}

	public long getTransferredCompressed() {
		return transferredCompressed;
	}

	public void setTransferredCompressed(long transferredCompressed) {
		this.transferredCompressed = transferredCompressed;
	}
	
	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

}

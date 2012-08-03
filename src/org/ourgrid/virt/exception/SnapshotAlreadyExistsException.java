package org.ourgrid.virt.exception;

public class SnapshotAlreadyExistsException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3716356414047476740L;

	public SnapshotAlreadyExistsException(String message){
		super( message );
	}
	
}

package com.nbarraille.jjsonrpc;

/**
 * A remote error represents an error that happened remotely when trying to interpret a JSON RCP request.
 * It contains an error code, an error message and some additional data about the error.
 * @author nbarraille
 *
 */
public class RemoteError {
	private int _code; // The code of the error.
	private String _message; // The message of the error
	private Object _data; // Some additional data about this error.
	
	/**
	 * Creates a Remote Error with the code, the message and the data.
	 * @param code the code of the error.
	 * @param message the message of the error. Should not be null.
	 * @param data some additional data about this error. May be null.
	 */
	protected RemoteError(int code, String message, Object data) {
		_code = code;
		_message = message;
		_data = data;
	}
	
	public int getCode() {
		return _code;
	}
	
	public String getMessage() {
		return _message;
	}
	
	public Object getObject() {
		return _data;
	}
}

package com.nbarraille.jjsonrpc;


public class CallbackPendingRequest extends PendingRequest {
	private CallbackMethod _callback; // The callback method
	
	public CallbackPendingRequest(long id, CallbackMethod callback) {
		super(id);
		_callback = callback;
	}
	
	public CallbackMethod getCallback() {
		return _callback;
	}

}

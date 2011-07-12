package com.nbarraille.jjsonrpc;

public class WaitingPendingRequest extends PendingRequest {
	private Object _result; // The result arrived for this request (null : not arrived yet)
	private RemoteError _error; // Did a error response arrive for this request.
	
	public WaitingPendingRequest(long id) {
		super(id);
		_error = null;
	}
	
	public void setResult(Object res) {
		_result = res;
	}
	
	public Object getResult() {
		return _result;
	}
	
	public void setError(RemoteError error) {
		_error = error;
	}
	
	public boolean isError() {
		return _error != null;
	}
	
	public RemoteError getError() {
		return _error;
	}

}

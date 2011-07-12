package com.nbarraille.jjsonrpc;


/**
 * A pending request represents a request that the local peer has made, but didn't receive or didn't use
 * the response yet.
 * It is considered waiting, if the request was made synchronously and the thread making this request is
 * waiting for the answer.
 * If the pending request is waiting, the response will be saved to the _response field when received, and
 * the thread waiting for it will have to poll.
 * If the pending request is not waiting (request made asynchronously), the response will be sent to the _caller
 * when received.
 * 
 * @author nbarraille
 *
 */
public abstract class PendingRequest {
	private long _id; // The ID of the request
	private long _time; // The time at which the request was made (for cleanup purposes)

	protected PendingRequest(long id) {
		_id = id;
		_time = System.currentTimeMillis();
	}
	
	public long getId() {
		return _id;
	}
	
	public long getTime() {
		return _time;
	}
	
	/**
	 * Overrides the equals method, so that two PendingRequest with the same ID are considered equals.
	 */
	@Override
	public boolean equals(Object o) {
		if(o instanceof PendingRequest) {
			PendingRequest pr = (PendingRequest) o;
			return _id == pr.getId();
		}
		return false;
	}
	
}

package com.nbarraille.jjsonrpc;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Notification;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

/**
 * A JJsonPeer is an entity that can both send and receive JSON-RCP formatted requests,responses and notifications
 * through the socket it is attached to.
 * Implementation choices:
 *    + The ID of the requests are represented by longs.
 *    + The Peer takes care of casting compatible parameters types so you don't have to use Wrapper types in the API.
 *    + It uses a method cache for a faster method lookup.
 *    + The parameters are passed as a List of objects (not a Map).
 *    + It is possible to pass null parameters (equivalent to empty list)
 *    + It is possible to make both synchronous and asynchronous calls (requests)
 *    + The maximum number of concurrent requests is configurable (MAX_PENDING_REQUESTS)
 *    + There is a different Timeout for synchronous and asynchronous requests.
 *    + It is (supposed to be) thread-safe.
 *    + The methods the peer can execute on the local servers are limited to the ones in the API. 
 *    
 * @author nbarraille <nathan.barraille@gmail.com>
 *
 */
public class JJsonPeer extends Thread {
	private final static int TIMEOUT_SYNC = 3000; // Request timeout in ms for synchronous calls.
	private final static int TIMEOUT_ASYNC = 10000; // Request timeout in ms for asynchronous calls. 
	private final static int ERROR_CODE_PARSE_ERROR = -32700;
	private final static int ERROR_CODE_INVALID_REQUEST = -32600;
	private final static int ERROR_CODE_METHOD_NOT_FOUND = -32601;
	private final static int ERROR_CODE_INVALID_PARAMS = -32602;
	//private final static int ERROR_CODE_INTERNAL_ERROR = -32603;
	private final static int ERROR_CODE_SERVER_ERROR = -32099;
	
	private final static int END_OF_MESSAGE_CHAR = 10;
	private final static long MAX_PENDING_REQUESTS = 100;

	
	private Logger _log = Logger.getLogger(this.getClass().getCanonicalName()); // The logger object.
	private Map<String, Set<Method>> _methodsCache;
	private List<PendingRequest> _pendingRequests;
	
	private Socket _socket; // The socket used by the peer to communicate.
	private InputStream _in; // The InputStream of the socket.
	private PrintWriter _out; // The OutputStream of the socket.
	private Class<?> _apiClass;
	
	/**
	 * Creates a new Peer.
	 * @param socket the socket this Peer will use to communicate.
	 * @throws IOException if the Socket is closed or not connected.
	 * @throws ClassNotFoundException if the API Class is invalid.
	 */
	public JJsonPeer(Socket socket, Class<?> apiClass) throws IOException {
		_socket = socket;
		_in = _socket.getInputStream();
		_out = new PrintWriter(_socket.getOutputStream(), true);
		_apiClass = apiClass;
		_pendingRequests = Collections.synchronizedList(new ArrayList<PendingRequest>());
		buildMethodsCache();
	}
	
	/**
	 * Creates a new PendingRequest with the smallest unused ID, and adds it to the pending requests list.
	 * If the callback method is null, the call will be considered synchronous and a WaitingPendingRequest will be
	 * created. The result will be updated in the PendingRequest object when received.
	 * If the callback method is not null, the request will be considered asynchronous, and a CallbackPendingRequest
	 * will be created. The callback method will be called when the response is received.
	 * 
	 * @param callback the callback method to call when the response arrives. If null, will not notify.
	 * @return the id assigned to this request. Returns -1 if the pending request list is full.
	 */
	private long registerRequest(CallbackMethod callback) {
		long id = -1;
		
		synchronized(_pendingRequests) {
			for(long i = 0; i < MAX_PENDING_REQUESTS; i++) {
				if(getPendingRequest(i) == null) {
					id = i;
					break;
				}
			}
			
			if(id != -1) {
				if(callback == null) {
					_pendingRequests.add(new WaitingPendingRequest(id));
				} else {
					_pendingRequests.add(new CallbackPendingRequest(id, callback));
				}
			}
		}
		
		return id;
	}
	
	/**
	 * Removes the Request with the given id, if it is in the list.
	 * Thread-safe.
	 * @param id the id of the request to remove.
	 */
	private void removeRequest(long id) {
		synchronized(_pendingRequests) {
			for(int i = 0; i < _pendingRequests.size(); i++) {
				if(_pendingRequests.get(i).getId() == id)
					_pendingRequests.remove(i);
			}
		}
	}
	
	/**
	 * Retrieves the PendingRequest with the given id, if it exists, or null if it doesn't.
	 * Thread-safe.
	 * @param id the ID.
	 * @return the pending request, or null.
	 */
	private PendingRequest getPendingRequest(long id) {
		synchronized(_pendingRequests) {
			for(PendingRequest pr : _pendingRequests) {
				if(pr.getId() == id) {
					return pr;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Removes the expired Pending Requests from the list (older than TIMEOUT_ASYNC)
	 * Thread-safe.
	 */
	private void cleanupPendingRequests() {
		synchronized(_pendingRequests) {
			long time = System.currentTimeMillis();
			for(int i = 0; i < _pendingRequests.size(); i++) {
				if(_pendingRequests.get(i).getTime() + TIMEOUT_ASYNC < time) {
					_pendingRequests.remove(i);
				}
			}
		}
	}
	
	/**
	 * Builds a cache of methods available for fast lookup when looking for compatible methods.
	 * @throws ClassNotFoundException if the _apiClass is not a valid class.
	 */
	private void buildMethodsCache() {
		_methodsCache = new Hashtable<String, Set<Method>>();
		Method[] methods = _apiClass.getMethods();
		for(Method m : methods) {
			if(_methodsCache.containsKey(m.getName())) {
				_methodsCache.get(m.getName()).add(m);
			} else {
				Set<Method> s = new HashSet<Method>();
				s.add(m);
				_methodsCache.put(m.getName(), s);
			}
		}
	}
	
	/**
	 * Returns the first method with the provided name and compatible parameters.
	 * Returns null if no methods match.
	 * @param name The name of the method.
	 * @param params An array containing the parameters of the method.
	 */
	private Method getCompatibleMethods(String name, Object[] params) {
		Set<Method> methods = _methodsCache.get(name);
		
		if(methods == null)
			return null;
		
		for(Method candidate : methods) {
			if(Helper.areCompatible(params, candidate.getParameterTypes())) {
				return candidate;
			}
		}
		
		return null;
	}

	/**
	 * Thread onStart event. Listening for incoming data through the socket.
	 */
	public void run() {
		try {
			_log.log(Level.INFO, "JJSON Peer listening...");
			while(true){
				int b;
				StringBuffer sb = new StringBuffer();
				if(_in.available() != 0) {
					// New data incoming, reading
					while ((b = _in.read()) != END_OF_MESSAGE_CHAR) {
					     sb.append((char) b);
					}
					String data = sb.toString();
					//_log.log(Level.INFO, "Peer on port:" + _socket.getPort() + " said: " + data);
					// Process data
					routeIncomingData(data);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Processes an incomming message from the InputStream. Tries to determine which kind of
	 * message it is, and route it to the appropriate methods for processing.
	 * @param data
	 */
	private void routeIncomingData(String data) {
		// Parsing the JSON-RPC data
		try {
			JSONRPC2Request req = JSONRPC2Request.parse(data);
			processRequest(req);
		} catch(JSONRPC2ParseException e) {
			// Data received is not a valid JSON-RPC Request, trying to parse it to a Response
			try {
				JSONRPC2Response rep = JSONRPC2Response.parse(data);
				processResponse(rep);
			} catch(JSONRPC2ParseException e2) {
				// Data received is not a valid JSON-RPC Response, trying to parse it to a Notification
				try {
					JSONRPC2Notification not = JSONRPC2Notification.parse(data);
					processNotification(not);
				} catch(JSONRPC2ParseException e3) {
					// Data received is not a valid JSON-RPC Notification, sending an error response.
					_log.log(Level.INFO, "The data received cannot be parsed : " + data);
					sendErrorResponse(ERROR_CODE_PARSE_ERROR, "Parse Error");
				}
			}
			return;
		}
	}
	
	/**
	 * Processes a received request. Parses it, executes the call and returns the response.
	 * If something wrong happens during the processing, an error response will be sent.
	 * 
	 * @param req the received request.
	 */
	private void processRequest(JSONRPC2Request req) {
		String method = req.getMethod();
		Object argsObj = req.getParams();
		Object idObj = req.getID();
		long id = 0;
		try {
			id = Integer.valueOf(String.valueOf(idObj));
		} catch (NumberFormatException e) {
			// Wrong request, sending error
			_log.log(Level.INFO, "Invalid Request: Cannot retrieve ID");
			sendErrorResponse(ERROR_CODE_INVALID_REQUEST, "Invalid Request");
			return;
		}
		
		Object[] params = null;
		Class<?>[] paramsTypes = null;
		if(argsObj == null) { // Support for null params
			params = new Object[0];
			paramsTypes = new Class<?>[0];
		} else if(argsObj instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> argsList = (List<Object>) argsObj;
			params = new Object[argsList.size()];
			paramsTypes = new Class<?>[argsList.size()];
			int i = 0;
			for(Object o : argsList) {
				params[i] = o;
				paramsTypes[i++] = o.getClass();
			}
		} else {
			// Wrong request, sending error
			_log.log(Level.INFO, "Invalid Request: Cannot retrieve List params");
			sendErrorResponse(ERROR_CODE_INVALID_REQUEST, "Invalid Request", id);
			return;
		}
		
		// Locating and executing the method statically
		Object methodResponse = null;
		try {
			Method m = getCompatibleMethods(method, params);
			if(m == null) {
				// Called wrong method, sending Error Response
				sendErrorResponse(ERROR_CODE_METHOD_NOT_FOUND, "Method Not Found", id);
				return;
			}
			methodResponse = m.invoke(null, Helper.castParameters(params, m));
		} catch (SecurityException e) {
			// A Security Manager prevents the access to this method
			// Sending Error Response
			sendErrorResponse(ERROR_CODE_SERVER_ERROR, "Server Error", id);
			return;
		} catch (IllegalArgumentException e) {
			// The method was called with the wrong number/types of arguments
			sendErrorResponse(ERROR_CODE_INVALID_PARAMS, "Invalid params", id);
			return;
		} catch (IllegalAccessException e) {
			// Java Language Access prevented the invocation of this method
			// Sending Error Response
			sendErrorResponse(ERROR_CODE_SERVER_ERROR, "Server Error", id);
			return;
		} catch (InvocationTargetException e) {
			// The method has thrown an exception
			// Sending Error Response
			sendErrorResponse(ERROR_CODE_SERVER_ERROR, "Server Error", id);
			return;
		}
		
		// Send Response
		sendResponse(id, methodResponse);
	}
	
	/**
	 * Processes a received notification.
	 * @param not the received notification.
	 */
	private void processNotification(JSONRPC2Notification not) {
		String method = not.getMethod();
		Object argsObj = not.getParams();
		
		Object[] params = null;
		Class<?>[] paramsTypes = null;
		if(argsObj == null) {
			// Support for null params
			params = new Object[0];
			paramsTypes = new Class<?>[0];
		} else if(argsObj instanceof List) {
			@SuppressWarnings("unchecked")
			List<Object> argsList = (List<Object>) argsObj;
			params = new Object[argsList.size()];
			paramsTypes = new Class<?>[argsList.size()];
			int i = 0;
			for(Object o : argsList) {
				params[i] = o;
				paramsTypes[i++] = o.getClass();
			}
		} else {
			// Wrong request, ignoring
			_log.log(Level.INFO, "Invalid Request: Cannot retrieve List params");
			return;
		}
		
		// Locating and executing the method statically
		Method m = getCompatibleMethods(method, params);
		if(m == null) {
			// Called wrong method, ignoring
			_log.log(Level.INFO, "Method not found : " + method);
			return;
		}
		
		try {
			m.invoke(null, Helper.castParameters(params, m));
		} catch (SecurityException e) {
			// A Security Manager prevented the access to this method
			_log.log(Level.INFO, "A Security Manager prevented the access to this method");
			return;
		} catch (IllegalArgumentException e) {
			// The method was called with the wrong number/types of arguments, SHOULDNT HAPPEN
			e.printStackTrace();
			return;
		} catch (IllegalAccessException e) {
			// Java Language Access prevented the invocation of this method
			_log.log(Level.INFO, "Java Language Access prevented the invocation of this method");
			return;
		} catch (InvocationTargetException e) {
			// The method has thrown an exception
			return;
		}
	}
	

	/**
	 * Processes a received response message.
	 * @param rep the received response.
	 */
	private void processResponse(JSONRPC2Response resp) {
		// Retrieving response ID
		long id = 0;
		try {
			id = Integer.valueOf(String.valueOf(resp.getID()));
		} catch (NumberFormatException e) {
			// Cannot retrieve ID (probably a parsing error on the other side)
			_log.log(Level.INFO, "Invalid Response: Cannot retrieve ID");
			return;
		}
		
		// Retrieve the error if there was one
		JSONRPC2Error error = resp.getError();
		
		synchronized(_pendingRequests) {
			PendingRequest pr = getPendingRequest(id);
			if(pr != null) {
				if(pr instanceof WaitingPendingRequest) {
					// It is a waiting request, update the result or error field.
					if(error == null) {
						((WaitingPendingRequest) pr).setResult(resp.getResult());
					} else {
						RemoteError re = new RemoteError(error.getCode(), error.getMessage(), error.getData());
						((WaitingPendingRequest) pr).setError(re);
					}
				} else if(pr instanceof CallbackPendingRequest){
					// It is a non waiting request, call the callback method
					CallbackMethod callback = ((CallbackPendingRequest) pr).getCallback();
					if(error == null) {
						callback.run(resp.getResult());
					} else {
						RemoteError re = new RemoteError(error.getCode(), error.getMessage(), error.getData());
						callback.run(re);
					}
					// Remove the request from the pending list.
					removeRequest(id);
				}
			}
		}
	}
	
	
	/**
	 * Returns the Socket through which this peer communicates.
	 * @return the Socket of this peer.
	 */
	public Socket getSocket() {
		return _socket;
	}
	
	/**
	 * Formats a JSON-RPC 2.0 request, gives it an ID, sends it through the Socket
	 * and waits for the response to arrive.
	 * This call blocks the current thread until the response arrives (or timeout).
	 * @param methodName the name of the method to execute on the remote server.
	 * @param args a Map of arguments to execute the method with.
	 */
	public Object sendSyncRequest(String methodName, List<Object> args, boolean forceWait) {
		// Registers a waiting pending request (no callback)
		long id = registerRequest(null);
		// Handling list full
		if(id == -1) {
			cleanupPendingRequests();
			id = registerRequest(null);
			if(forceWait) {
				while(id == -1) {
					cleanupPendingRequests();
					id = registerRequest(null);
				}
			} else {
				return null;
			}
		}
		
		JSONRPC2Request req = new JSONRPC2Request(methodName, args, id);
		String s = req.toString();
		
		_log.log(Level.INFO, "Sending request:" + s);
		synchronized(_out) {
			_out.println(s);
			_out.flush();
		}
		
		return waitForResponse(id);
	}
	
	/**
	 * Formats a JSON-RPC 2.0 request, gives it an ID, sends it through the Socket and registers the callback
	 * to be notified when the response will arrive.
	 * It should not be used to make a call of which you don't want the response. In this case, use sendNotification.
	 * This call does not block the current thread.
	 * Thread-safe.
	 * @param methodName the name of the method to execute on the remote server.
	 * @param args a List of arguments to execute the method with.
	 * @param callback The callback method to send the response to.
	 * @param forceWait if the list of pending requests is full, and this is set to true, blocks the thread and retries until
	 * this request can be sent.
	 * @return True if the request was sent successfully, false else (if the pending request list was full)
	 */
	public boolean sendAsyncRequest(String methodName, List<Object> args, CallbackMethod callback, boolean forceWait) {
		long id = registerRequest(callback);
		// Handling list full
		if(id == -1) {
			cleanupPendingRequests();
			id = registerRequest(callback);
			if(forceWait) {
				while(id == -1) {
					cleanupPendingRequests();
					id = registerRequest(callback);
				}
			} else {
				return false;
			}
		}
		
		JSONRPC2Request req = new JSONRPC2Request(methodName, args, id);
		String s = req.toString();
		
		_log.log(Level.INFO, "Sending request:" + s);
		synchronized(_out) {
			_out.println(s);
			_out.flush();
		}
		
		return true;
	}
	
	/**
	 * Formats a JSON-RPC 2.0 notification and sends it through the Socket. A notification
	 * is the same as a request except that it does not have an ID and does not require
	 * any response from the server.
	 * This call does not block the thread.
	 * @param methodName the name of the method to execute on the remote server.
	 * @param args a Map of arguments to execute the method with.
	 */
	public void sendNotification(String methodName, List<Object> args) {
		JSONRPC2Notification not = new JSONRPC2Notification(methodName, args);
		String s = not.toString();
		_log.log(Level.INFO, "Sending Notification:" + s);
		synchronized(_out) {
			_out.println(s);
			_out.flush();
		}
	}
	
	/**
	 * Formats a JSON-RPC 2.0 error response, with the id of the request, and send
	 * it through the socket.
	 * @param code the error code.
	 * @param message the error message.
	 * @param reqId the ID of the request that caused the error.
	 */
	public void sendErrorResponse(int code, String message, Long reqId) {
		JSONRPC2Response resp = new JSONRPC2Response(new JSONRPC2Error(code, message), reqId);
		String s = resp.toString();
		_log.log(Level.INFO, "Sending Error Response:" + s);
		synchronized(_out) {
			_out.println(s);
			_out.flush();
		}
	}
	
	/**
	 * Formats a JSON-RPC 2.0 error response with no ID, and sends it through the socket.
	 * @param code the error code.
	 * @param message the error message.
	 */
	public void sendErrorResponse(int code, String message) {
		sendErrorResponse(code, message, null);
	}
	
	/**
	 * Format a JSON-RPC 2.0 response containing the ID of the request and the Object returned
	 * by the method called, and send it through the socket.
	 * @param id the ID of the corresponding request.
	 * @param o the object returned by the method called by the corresponding request.
	 */
	public void sendResponse(long id, Object o) {
		JSONRPC2Response r = new JSONRPC2Response(o, id);
		String s = r.toString();
		_log.log(Level.INFO, "Sending Response:" + s);
		synchronized(_out) {
			_out.println(s);
			_out.flush();
		}
	}
	
	/**
	 * Waits for the response of the request with the given id until it arrives or the timeout is reached.
	 * Returns null if the timeout is reached or the id is invalid (shouldn't happen).
	 * @param id the id of the request we are waiting for.
	 * @return the response, or null.
	 */
	private Object waitForResponse(long id) {
		long startTime = System.currentTimeMillis();
		
		PendingRequest pr = getPendingRequest(id);
		if(pr != null && pr instanceof WaitingPendingRequest) {
			while(((WaitingPendingRequest) pr).getResult() == null && !((WaitingPendingRequest) pr).isError()
					&& System.currentTimeMillis() - startTime < TIMEOUT_SYNC) {
			}
			
			removeRequest(id);
			return ((WaitingPendingRequest) pr).isError() ? 
					((WaitingPendingRequest) pr).getError() : ((WaitingPendingRequest) pr).getResult();
		}
		
		return null;
		
	}
}

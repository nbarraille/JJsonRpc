package com.nbarraille.jjsonrpc;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class TcpClient {
	private Logger _log = Logger.getLogger(this.getClass().getCanonicalName()); // The logger object.
	
	private JJsonPeer _peer;
	
	public TcpClient(String serverAddress, int serverListenerPort, Class<?> apiClass) throws UnknownHostException, IOException {
		_peer = new JJsonPeer(new Socket(serverAddress, serverListenerPort), apiClass);
		_log.log(Level.INFO, "TCP Client started");
		_peer.start();
	}
	
	public JJsonPeer getPeer() {
		return _peer;
	}

}

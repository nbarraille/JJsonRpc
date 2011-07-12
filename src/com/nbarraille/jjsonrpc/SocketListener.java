package com.nbarraille.jjsonrpc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocketListener extends Thread {
	private Logger _log = Logger.getLogger(this.getClass().getCanonicalName()); // The logger object.
	
	private int _port;
	private ServerSocket _socket;
	private TcpServer _server;
	private Class<?> _apiClass;
	
	public SocketListener(int port, TcpServer server, Class<?> apiClass) {
		_port = port;
		_socket = null;
		_server = server;
		_apiClass = apiClass;
	}
	
	public void run() {
		try {
			_socket = new ServerSocket(_port);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		while(true) {
			try {
				Socket connected = _socket.accept();
				JJsonPeer jp = new JJsonPeer(connected, _apiClass);
				_log.log(Level.INFO, "New client connected on port " + connected.getPort());
				_server.addPeer(jp);
				jp.start();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}

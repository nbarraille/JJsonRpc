package com.nbarraille.jjsonrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A TCP Server that creates a SocketListener in an other thread. This socket listener will automatically create JJsonPeers 
 * on any socket that it opens. The TCP Server offers access to all these peers.
 * @author nbarraille
 *
 */
public class TcpServer {
	private Logger _log = Logger.getLogger(this.getClass().getCanonicalName()); // The logger object.
	private SocketListener _listener;
	private ArrayList<JJsonPeer> _peers;
	
	/**
	 * Creates a new Server that will listen for connections on the given port.
	 * @param listenerPort the port to listen for connections on.
	 */
	public TcpServer(int listenerPort, Class<?> apiClass) {
		_peers = new ArrayList<JJsonPeer>();
		_listener = new SocketListener(listenerPort, this, apiClass);
	}
	
	/**
	 * Starts the Server: Starts the listener in an other thread.
	 * This method does not block the current thread.
	 */
	public void start() {
		_listener.start();
		_log.log(Level.INFO, "TCP Server started.");
	}
	
	/**
	 * Adds a Peer to the Peers list if it is not already in the list.
	 * @param peer the Peer to add to the list.
	 */
	protected void addPeer(JJsonPeer peer) {
		int port = -1;
		for(JJsonPeer p : _peers) {
			if(port == p.getSocket().getPort()) {
				return;
			}
		}
		_peers.add(peer);
	}
	
	/**
	 * Removes the Peer at the given port, if there is one.
	 * @param port the port of the Peer to remove.
	 */
	protected void removePeer(int port) {
		for(int i = 0; i < _peers.size(); i++) {
			if(port == _peers.get(i).getSocket().getPort()) {
				_peers.remove(i);
				return;
			}
		}
	}
	
	/**
	 * Returns the peer at the given index.
	 * @param index the index.
	 * @return the Peer at this position.
	 */
	public JJsonPeer getPeer(int index) {
		return _peers.get(index);
	}
	
	
	/**
	 * Sends a notification to all the peers connected to this server.
	 * @param methodName the name of the method to execute on the remote peer.
	 * @param args the arguments to execute the method with.
	 */
	public void sendBroadcastNotification(String methodName, List<Object> args) {
		for(JJsonPeer jp : _peers) {
			jp.sendNotification(methodName, args);
		}
	}
}

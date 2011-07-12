package com.nbarraille.jjsonrpc.example.server;

import com.nbarraille.jjsonrpc.TcpServer;

public class ExampleServer {

	public static void main(String[] args) throws InterruptedException {
		TcpServer server = new TcpServer(5512, ServerApi.class);
		server.start();
		Thread.sleep(3000);
		server.sendBroadcastNotification("hello", null);
	}
}

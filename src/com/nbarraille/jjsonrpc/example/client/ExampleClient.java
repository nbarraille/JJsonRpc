package com.nbarraille.jjsonrpc.example.client;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.nbarraille.jjsonrpc.CallbackMethod;
import com.nbarraille.jjsonrpc.InvalidMethodException;
import com.nbarraille.jjsonrpc.JJsonPeer;
import com.nbarraille.jjsonrpc.RemoteError;
import com.nbarraille.jjsonrpc.TcpClient;


public class ExampleClient {

	public static void main(String[] args) throws Exception {
		try {
			JJsonPeer peer = new TcpClient("localhost", 5512, ClientApi.class).getPeer();
			
			List<Object> a = new ArrayList<Object>();
			a.add(1.4);
			a.add(2.0);
			Thread.sleep(1000);
			System.out.println("The result of the multiplication is " + (Double) peer.sendSyncRequest("multiply", a, true));
			Thread.sleep(1000);
			List<Object> b = new ArrayList<Object>();
			b.add(987);
			b.add(1234);
			
			ExampleClient ec = new ExampleClient();
			Class<?>[] params = {Object.class};
			try {
				CallbackMethod addCb = new CallbackMethod(ec.getClass().getMethod("handleAddition", Object.class), null, null);
				peer.sendAsyncRequest("add", b, addCb, true);
				
				Thread.sleep(500);
				
				params = new Class<?>[2];
				params[0] = String.class;
				params[1] = Object.class;
				Object[] p = {"Toulouse"};
				CallbackMethod timeCb = new CallbackMethod(ec.getClass().getMethod("handleTime", params), ec, p);
				peer.sendAsyncRequest("gimmTheTime", null, timeCb, false);
			} catch(InvalidMethodException e) {
				e.printStackTrace();
			}
			
			return;
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	///////////////////// CALLBACK METHODS ////////////////
	
	/**
	 * Example of static callback method.
	 * When the request's response will be received, this will be called with o as the response (or RemoteError if something went wrong)
	 * @param o the Object returned by the 
	 */
	public static void handleAddition(Object o) {
		if(o instanceof Long) {
			System.out.println("The result of the addition is " + o);
		} else if(o instanceof RemoteError) {
			System.out.println("Remote Error while processing this: " + ((RemoteError) o).getMessage());
		}
	}
	
	/**
	 * Example of non static callback method with several arguments
	 */
	public void handleTime(String where, Object o) {
		if(o instanceof Long) {
			System.out.println("The current time in " + where + " is " + o);
		} else if(o instanceof RemoteError) {
			System.out.println("Remote Error while processing this: " + ((RemoteError) o).getMessage());
		}
	}
}

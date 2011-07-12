package com.nbarraille.jjsonrpc.example.server;

public class ServerApi {

	/**
	 * Test method with arguments
	 * @param a
	 * @param b
	 * @return
	 */
	public static long add(long a, long b) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return a + b;
	}
	
	public static double multiply(double a, double b) {
		return a * b;
	}
	
	/**
	 * Test method with no argument but return value
	 */
	public static long gimmeTheTime() {
		return System.currentTimeMillis();
	}
	
	/**
	 * Test method with no argument and no return value
	 */
	public static void plop() {
		System.out.println("plop plop plop");
	}
}

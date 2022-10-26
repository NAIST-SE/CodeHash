package jp.naist.se.codehash.util;

public class Counter {

	private int count;
	
	public Counter() {
		count = 0;
	}
	
	public Counter(int initialValue) {
		count = initialValue;
	}

	public void increment() {
		count++;
	}
	
	public int get() {
		return count;
	}
}

package jp.naist.se.codehash.util;

public class Counter {

	private int count;
	
	/**
	 * Create a counter whose value is zero.
	 */
	public Counter() {
		count = 0;
	}
	
	/**
	 * Create a counter having a specified value. 
	 * @param initialValue specifies the initial value.
	 */
	public Counter(int initialValue) {
		count = initialValue;
	}

	/**
	 * Increment the counter value.
	 */
	public void increment() {
		count++;
	}
	
	/**
	 * Add a specified value to the counter.
	 * @param value
	 */
	public void increment(int value) {
		count+=value;
	}
	
	/**
	 * @return the current value of the counter.
	 */
	public int get() {
		return count;
	}
}

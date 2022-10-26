package jp.naist.se.codehash.util;

import java.util.HashMap;
import java.util.Set;

/**
 * StringMultiset allows multiple instances of a string.
 */
public class StringMultiset {

	
	private HashMap<String, Counter> counters;
	private int size;

	/**
	 * Create an empty multiset.
	 * @param capacity specifies the initial capacity of an internal collection.
	 */
	public StringMultiset(int capacity) {
		counters = new HashMap<>(capacity);
		size = 0;
	}
	
	/**
	 * Add a string to the multiset.
	 * @param string to be added
	 * @return the number of instances of the given string
	 */
	public int add(String s) { 
		Counter c = counters.get(s);
		if (c == null) {
			c = new Counter();
			counters.put(s, c);
		}
		c.increment();
		size++;
		return c.get();
	}
	
	/**
	 * @return the number of strings in the multiset. 
	 */
	public int size() {
		return size;
	}
	
	/**
	 * @param s specifies a string.
	 * @return the number of instances of the string.
	 */
	public int get(String s) {
		Counter c = counters.get(s);
		if (c != null) {
			return c.get();
		} else {
			return 0;
		}
	}
	
	/**
	 * @return an ordinary Set object without multiple instances.
	 */
	public Set<String> keySet() {
		return counters.keySet();
	}
	
	public int intersection(StringMultiset another) {
		int count = 0;
		for (String s: counters.keySet()) {
			count += Math.min(this.get(s), another.get(s));
		}
		return count;
	}
	
	/**
	 * This method returns a StringMultiset object.
	 * keySet() returns a Set object.
	 * @return a set removing duplicated elements. 
	 * In other words, this.toSet().get(s) == 1 for any string in this set.
	 */
	public StringMultiset toOrdinarySet() {
		StringMultiset ordinary = new StringMultiset(counters.size() * 2);
		for (String s: keySet()) {
			ordinary.add(s);
		}
		return ordinary;
	}
	
}

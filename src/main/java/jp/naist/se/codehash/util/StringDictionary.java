package jp.naist.se.codehash.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * StringDictionary assigns a unique integer ID to each distinct string.
 * This class is thread-safe, so that multiple threads can share a dictionary.
 * IDs depend on the order of calls; they are not intended to be written to an output.
 */
public class StringDictionary {

	private ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
	private AtomicInteger nextId = new AtomicInteger();

	/**
	 * @param s specifies a string.
	 * @return the ID of the string.  A new ID is assigned if the string is not registered yet.
	 */
	public int getId(String s) {
		return ids.computeIfAbsent(s, k -> nextId.getAndIncrement());
	}

	/**
	 * @return the number of distinct strings in the dictionary.
	 */
	public int size() {
		return ids.size();
	}

}

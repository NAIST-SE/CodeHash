package jp.naist.se.codehash.util;

import java.util.Arrays;

/**
 * IntMultiset is an immutable multiset of integers.
 * Distinct elements are stored in a sorted array with their counts,
 * so that the intersection of two multisets is computed by merging the arrays.
 * It is used to compare string multisets whose elements are translated into IDs by a shared {@link StringDictionary}.
 */
public class IntMultiset {

	/**
	 * Distinct elements in ascending order.
	 */
	private int[] elements;

	/**
	 * counts[i] is the number of instances of elements[i].
	 */
	private int[] counts;

	private int size;

	/**
	 * Create a multiset of IDs of strings.
	 * @param strings specifies a multiset to be translated.
	 * @param dictionary assigns IDs to the strings.
	 * Multisets to be compared must be created with the same dictionary.
	 */
	public IntMultiset(StringMultiset strings, StringDictionary dictionary) {
		// Sort pairs of an ID (upper 32 bits) and a count (lower 32 bits)
		long[] packed = new long[strings.keySet().size()];
		int index = 0;
		for (String s: strings.keySet()) {
			packed[index++] = ((long)dictionary.getId(s) << 32) | strings.get(s);
		}
		Arrays.sort(packed);

		elements = new int[packed.length];
		counts = new int[packed.length];
		for (int i=0; i<packed.length; i++) {
			elements[i] = (int)(packed[i] >>> 32);
			counts[i] = (int)packed[i];
		}
		size = strings.size();
	}

	/**
	 * @return the number of elements in the multiset, including duplicated ones.
	 */
	public int size() {
		return size;
	}

	/**
	 * @return the number of distinct elements in the multiset.
	 */
	public int distinctSize() {
		return elements.length;
	}

	/**
	 * @param another multiset to be compared.  It must be created with the same dictionary as this object.
	 * @return the number of common elements in the two multisets.
	 */
	public int intersection(IntMultiset another) {
		int[] e1 = this.elements;
		int[] e2 = another.elements;
		int i = 0;
		int j = 0;
		int count = 0;
		while (i < e1.length && j < e2.length) {
			if (e1[i] < e2[j]) {
				i++;
			} else if (e1[i] > e2[j]) {
				j++;
			} else {
				count += Math.min(this.counts[i], another.counts[j]);
				i++;
				j++;
			}
		}
		return count;
	}

}

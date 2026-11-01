package jp.naist.se.codehash.hash;

import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;

/**
 * Parameters of N-grams and b-bit minhash shared by the tools.
 * An object holds command line options to compute N-grams and minhash, shared by FileCodeHash and GitCodeHash.
 * The "-n:" option is also used by DirectComparisonMain.
 */
public class MinHashOptions {

	/**
	 * The number of bits in a minhash vector.
	 * Minhash vectors must have the same length to estimate similarity.
	 */
	public static final int BIT_COUNT = 2048;

	/**
	 * The default N for N-grams (trigrams).
	 */
	public static final int DEFAULT_NGRAM_SIZE = 3;

	/**
	 * An option to select N for N-grams.  It should be one of 1--{@value #MAX_NGRAM_SIZE}.  "-n:3" is the default.
	 */
	public static final String NGRAM_OPTION = "-n:";

	/**
	 * An option to use a set rather than a multiset of N-grams to compute minhash.
	 */
	public static final String IGNORE_DUPLICATION_OPTION = "-ignoreduplication";

	/**
	 * The maximum N for N-grams.
	 */
	public static final int MAX_NGRAM_SIZE = 16;

	private int N = DEFAULT_NGRAM_SIZE;
	private boolean ignoreDuplication = false;
	private boolean invalid = false;

	/**
	 * Parse an "-n:" option.
	 * @param arg is a command line argument starting with {@link #NGRAM_OPTION}.
	 * @return a parsed value.  -1 is returned if an invalid value is specified.
	 */
	public static int parseNgramSize(String arg) {
		String nString = arg.substring(NGRAM_OPTION.length());
		try {
			int newN = Integer.parseInt(nString);
			if (1 <= newN && newN <= MAX_NGRAM_SIZE) {
				return newN;
			} else {
				System.err.println("N is out of range: " + nString);
				return -1;
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid number: " + nString);
			return -1;
		}
	}

	/**
	 * Parse a command line argument if it is an option of this class.
	 * @return true if the argument is consumed by this object.
	 * Use {@link #isInvalid()} to check whether the option value is valid.
	 */
	public boolean parse(String arg) {
		if (arg.startsWith(NGRAM_OPTION)) {
			int newN = parseNgramSize(arg);
			if (newN > 0) {
				N = newN;
			} else {
				invalid = true;
			}
			return true;
		} else if (arg.equals(IGNORE_DUPLICATION_OPTION)) {
			ignoreDuplication = true;
			return true;
		}
		return false;
	}

	/**
	 * @return true if an invalid option value is specified.
	 */
	public boolean isInvalid() {
		return invalid;
	}

	/**
	 * @return N for N-grams.
	 */
	public int getN() {
		return N;
	}

	/**
	 * @return true if a set rather than a multiset of N-grams is used to compute minhash.
	 */
	public boolean isIgnoreDuplication() {
		return ignoreDuplication;
	}

	/**
	 * @param t is a minhash type selected by a user.
	 * @return Murmur3IgnoreDuplication if t is Murmur3 and the duplication is ignored.  Otherwise, t is returned as is.
	 */
	public MinHashType getMinHashType(MinHashType t) {
		if (t == MinHashType.Murmur3 && ignoreDuplication) {
			return MinHashType.Murmur3IgnoreDuplication;
		}
		return t;
	}

}

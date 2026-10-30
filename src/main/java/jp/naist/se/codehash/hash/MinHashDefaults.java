package jp.naist.se.codehash.hash;

/**
 * Parameters of b-bit minhash shared by the tools.
 */
public class MinHashDefaults {

	/**
	 * The number of bits in a minhash vector.
	 * Minhash vectors must have the same length to estimate similarity.
	 */
	public static final int BIT_COUNT = 2048;

	/**
	 * The default N for N-grams (trigrams).
	 */
	public static final int DEFAULT_NGRAM_SIZE = 3;

	private MinHashDefaults() {
	}

}

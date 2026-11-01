package jp.naist.se.codehash.hash;

import java.util.BitSet;

import jp.naist.se.codehash.util.HashStringUtil;

/**
 * A 1-bit minhash vector of an N-gram multiset with the number of N-grams.
 * It estimates the Jaccard index of two N-gram multisets.
 */
public class BitMinHash {

	private BitSet bits;
	private int bitCount;
	private int ngramCount;

	/**
	 * @param hex is a hexadecimal string of a 1-bit minhash vector,
	 * e.g. a value returned by {@link SourceCodeHash#getNormalizedMinHash()}.
	 * @param ngramCount is the number of N-grams including duplicated ones.
	 */
	public BitMinHash(String hex, int ngramCount) {
		byte[] bytes = HashStringUtil.hexToBytes(hex);
		this.bits = BitSet.valueOf(bytes);
		this.bitCount = bytes.length * 8;
		this.ngramCount = ngramCount;
	}

	/**
	 * @return the number of N-grams including duplicated ones.
	 */
	public int getNgramCount() {
		return ngramCount;
	}

	/**
	 * The Jaccard index of two multisets cannot exceed the ratio of their sizes.
	 * @param another is compared with this object.
	 * @return the upper bound of the similarity computed from the numbers of N-grams.
	 */
	public double getMaxSimilarity(BitMinHash another) {
		return Math.min(this.ngramCount, another.ngramCount) * 1.0 / Math.max(this.ngramCount, another.ngramCount);
	}

	/**
	 * A bit of two vectors matches with probability (1 + J) / 2, where J is the Jaccard index.
	 * @param another is compared with this object.  It must have the same number of bits.
	 * @return the estimated Jaccard index, limited by {@link #getMaxSimilarity(BitMinHash)}.
	 */
	public double estimateSimilarity(BitMinHash another) {
		assert this.bitCount == another.bitCount: "The number of bits must be the same";
		BitSet copy = (BitSet)bits.clone();
		copy.xor(another.bits);
		int differentBitCount = copy.cardinality();
		double sim = 1 - (differentBitCount * 1.0 / (bitCount / 2));
		return Math.min(sim, getMaxSimilarity(another));
	}

}

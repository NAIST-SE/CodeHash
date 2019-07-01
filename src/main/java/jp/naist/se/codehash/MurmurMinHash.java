package jp.naist.se.codehash;

import java.util.Arrays;

import jp.naist.se.codehash.util.StringMultiset;

public class MurmurMinHash {

	private byte[] minhash;
	private byte[] normalizedMinHash;
	private int ngramCount;
	
	/**
	 * 1-bit minhash using k hash functions for N-gram Jaccard Index.  
	 * @param k the number of bits.  It should be a multiple of 8.  
	 * @param N
	 * @param reader
	 */
	public MurmurMinHash(int k, int N, TokenReader reader) {
		if (k <= 0) throw new IllegalArgumentException("k must be a positive integer. " + k);

		// Initialize minhash
		NgramMultiset ngramMultiset = new NgramMultiset(new NgramReader(N, reader));
		minhash = computeMinHash(k, ngramMultiset.getRegular());
		normalizedMinHash = computeMinHash(k, ngramMultiset.getNormalized());
		ngramCount = ngramMultiset.getNgramCount();
	}
	
	private byte[] computeMinHash(int k, StringMultiset mset) {
		// Initialize minhash
		int[] hash = new int[k];
		Arrays.fill(hash, Integer.MAX_VALUE);

		// Compute hash
		for (String key: mset.keySet()) {
			String s = key + "\0" + Integer.toString(mset.get(key));
			int h = MurmurHash3.murmurhash3_x86_32(s, 0, s.length(), 0);
			for (int i=0; i<k; i++) {
				if (h < hash[i]) {
					hash[i] = h;
				}
				h = MurmurHash3.fmix32(h);
			}
		}
		
		// Translate b-bit minhash
		byte[] bitminhash = new byte[hash.length / 8];
		for (int i=0; i<hash.length; i++) {
			int j = i / 8;
			int idx = i % 8;
			if ((hash[i] & 1) == 1) bitminhash[j] |= (1 << (7-idx));
		}
		
		// Sanity checker
		for (int i=0; i<bitminhash.length; i++) {
			for (int j=0; j<8; j++) {
				if ((bitminhash[i] & (1 << (7-j))) != 0) {
					assert (hash[i * 8 + j] & 1) == 1; 
				} else {
					assert (hash[i * 8 + j] & 1) == 0;
				}
			}
		}
		return bitminhash;
	}
	
	/**
	 * @return 1-bit minhash array.
	 */
	public byte[] getHash() {
		return minhash;
	}
	
	public byte[] getNormalizedHash() {
		return normalizedMinHash;
	}
	
	public int getNgramCount() {
		return ngramCount;
	}
	
}

package jp.naist.se.codehash;

import java.util.Arrays;

import jp.naist.se.codehash.util.StringMultiset;

public class MurmurMinHash {

	private int[] minhash;
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
		minhash = new int[k];
		Arrays.fill(minhash, Integer.MAX_VALUE);
		
		StringMultiset mset = new StringMultiset(2048);
		NgramReader ngramReader = new NgramReader(N, reader);
		while (ngramReader.next()) {
			// Calculate a hash for the N-gram 
			StringBuilder builder = new StringBuilder(128);
			for (int i=0; i<N; i++) {
				if (ngramReader.getToken(i) != null) {
					builder.append(ngramReader.getToken(i));
				} else {
					builder.append((char)i);
				}
				builder.append((char)0);
			}
			String s = builder.toString();
			// Distinguish multiple occurrences of the same n-gram
			int count = mset.add(s);
			builder.append((char)count);
			s = builder.toString();
			
			// Update minhash 
			int h = MurmurHash3.murmurhash3_x86_32(s, 0, s.length(), 0);
			for (int i=0; i<k; i++) {
				if (h < minhash[i]) {
					minhash[i] = h;
				}
				h = MurmurHash3.fmix32(h);
			}
		}
		
		ngramCount = ngramReader.getNgramCount();
	}
	
	/**
	 * @return 1-bit minhash array.
	 */
	public byte[] getHash() {
		byte[] bitminhash = new byte[minhash.length / 8];
		for (int i=0; i<minhash.length; i++) {
			int j = i / 8;
			int k = i % 8;
			if ((minhash[i] & 1) == 1) bitminhash[j] |= (1 << (7-k));
		}
		
		// Sanity checker
		for (int i=0; i<bitminhash.length; i++) {
			for (int j=0; j<8; j++) {
				if ((bitminhash[i] & (1 << (7-j))) != 0) {
					assert (minhash[i * 8 + j] & 1) == 1; 
				} else {
					assert (minhash[i * 8 + j] & 1) == 0;
				}
			}
		}
		
		return bitminhash;
	}
	
	public int getNgramCount() {
		return ngramCount;
	}
	
}

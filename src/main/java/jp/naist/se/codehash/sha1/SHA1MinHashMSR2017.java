package jp.naist.se.codehash.sha1;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import jp.naist.se.codehash.NgramMultiset;
import jp.naist.se.codehash.NgramReader;
import jp.naist.se.codehash.TokenReader;
import jp.naist.se.codehash.util.StringMultiset;

/**
 * This class implements a b-bit minhash calculation used in the following publication.
 * Takashi Ishio, Yusuke Sakaguchi, Kaoru Ito and Katsuro Inoue:
 * Source File Set Search for Clone-and-Own Reuse Analysis, In Proc. of MSR 2017.
 */
public class SHA1MinHashMSR2017 {

	private static final String HASH_FUNCTION = "SHA-1"; // Chosen for performance

	private long seed = 88172645463325252L;
	private long[] randomValues;
	private NgramMultiset multiset;

	private int length;

	private MessageDigest digest;

	/**
	 * 1-bit minhash using k hash functions for N-gram Jaccard Index.  
	 * @param k the number of bits.  It should be a multiple of 8.  
	 * @param N
	 * @param reader
	 */
	public SHA1MinHashMSR2017(int k, int N, TokenReader reader) {
		try {
			if (k <= 0) throw new IllegalArgumentException("k must be a positive integer. " + k);

			digest = MessageDigest.getInstance(HASH_FUNCTION);
			
			// Prepare random values
			randomValues = prepareRandomValues(k);
			
			// Calculate multiset
			multiset = new NgramMultiset(new NgramReader(N, reader));
			
			this.length = k;
			
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
	
	private long[] getMinHash(StringMultiset mset) {
		// Initialize minhash
		long[] minhash = new long[length];
		Arrays.fill(minhash, Long.MAX_VALUE);

		for (String key: mset.keySet()) {
			int count = mset.get(key);
			
			byte[] h = digest.digest(key.getBytes());
			long baseHash = extractLongHash(h);
			for (int i=1; i<=count; i++) {
				for (int k=0; k<length; k++) {
					long hash = baseHash * i * randomValues[k];
					if (hash < minhash[k]) {
						minhash[k] = hash;
					}
				}
			}
		}
		
		return minhash;
	}

	private long[] prepareRandomValues(int count) {
		long[] randomValues = new long[count];
		for(int i=0;i<count;i++){
			randomValues[i] = xor64()|1;
		}
		return randomValues;
	}

	private long xor64() {
		seed = seed ^ (seed << 13);
		seed = seed ^ (seed >> 7);
		return seed = seed ^ (seed << 17);
	}
	
	private long extractLongHash(byte[] digest) {
		long hash = 0;
		for (int i=0; i<8; i++) {
			hash = (hash << 8) + digest[i];
		}
		return hash;
	}
	
	private byte[] packMinhash(long[] minhash) {
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
	
	/**
	 * @return 1-bit minhash array.
	 */
	public byte[] getHash() {
		 long[] minhash = getMinHash(multiset.getRegular());
		 return packMinhash(minhash);
	}

}

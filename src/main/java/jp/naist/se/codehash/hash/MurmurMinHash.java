package jp.naist.se.codehash.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import jp.naist.se.codehash.token.TokenReader;
import jp.naist.se.codehash.util.StringMultiset;

public class MurmurMinHash {

	private int k;
	private NgramMultiset ngramMultiset;
	
	/**
	 * 1-bit minhash using k hash functions for N-gram Jaccard Index.  
	 * @param k the number of bits.  It should be a multiple of 8.  
	 * @param N
	 * @param reader
	 */
	public MurmurMinHash(int k, int N, TokenReader reader) {
		if (k <= 0) throw new IllegalArgumentException("k must be a positive integer. " + k);

		this.k = k;
		this.ngramMultiset = new NgramMultiset(new NgramReader(N, reader));
	}
	
	private byte[] computeMinHash(int k, StringMultiset mset) {
		// Initialize minhash
		int[] hash = new int[k];
		Arrays.fill(hash, Integer.MAX_VALUE);

		// Compute hash
		for (String key: mset.keySet()) {
			int count = mset.get(key);
			for (int c=0; c<count; c++) {
				String s = key + "\0" + Integer.toString(c);
				int h = MurmurHash3.murmurhash3_x86_32(s, 0, s.length(), 0);
				for (int i=0; i<k; i++) {
					if (h < hash[i]) {
						hash[i] = h;
					}
					h = MurmurHash3.fmix32(h);
				}
			}
		}
		
		return packMinHash(hash);
	}
	
	private byte[] packMinHash(int[] hash) {
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
	 * Calculate k-bit minhash representing a given multiset using SHA-1 hash algorithm.
	 * @param k is the number of minhash bits.
	 * @param mset represents a multiset to be translated.
	 * @return a bit array.
	 */
	private byte[] getSHA1MinHash(int k, StringMultiset mset) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			
			// Initialize minhash
			int[] minhash = new int[k];
			Arrays.fill(minhash, Integer.MAX_VALUE);
	
			// Compute hash
			for (String key: mset.keySet()) {
				int count = mset.get(key);
				for (int c=0; c<count; c++) {
					String s = key + "\0" + Integer.toString(c);
					byte[] hash = digest.digest(s.getBytes());
					for (int i=0; i<k; i++) {
						int h = extractIntHash(hash);
						if (h < minhash[i]) {
							minhash[i] = h;
						}
						hash = digest.digest(hash);
					}
					
				}
			}
			
			return packMinHash(minhash);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private int extractIntHash(byte[] digest) {
		int hash = 0;
		for (int i=0; i<4; i++) {
			hash = (hash << 4) + digest[i];
		}
		return hash;
	}
	
	/**
	 * @return 1-bit minhash array.
	 */
	public byte[] getHash() {
		return computeMinHash(k, ngramMultiset.getRegular());
	}
	
	/**
	 * @return 1-bit minhash array of the normalized N-grams.
	 * Normalization ignores differences such as variable names, following the rules of each programming language.
	 */
	public byte[] getNormalizedHash() {
		return computeMinHash(k, ngramMultiset.getNormalized());
	}
	
	/**
	 * @return 1-bit minhash array computed using SHA-1 instead of MurmurHash3.
	 */
	public byte[] getSHA1MinHash() {
		return getSHA1MinHash(k, ngramMultiset.getRegular());
	}
	
	/**
	 * @return 1-bit minhash array of the normalized N-grams computed using SHA-1 instead of MurmurHash3.
	 */
	public byte[] getNormalizedSHA1MinHash() {
		return getSHA1MinHash(k, ngramMultiset.getNormalized());
	}
	
	/**
	 * @return the number of N-grams including duplicated ones.
	 * @see NgramMultiset#getNgramCount()
	 */
	public int getNgramCount() {
		return ngramMultiset.getNgramCount();
	}
	
	/**
	 * @return 1-bit minhash array of the set of N-grams, i.e., duplicated N-grams are regarded as a single element.
	 */
	public byte[] getHashIgnoreDuplicatedElements() {
		return computeMinHash(k, ngramMultiset.getRegular().toOrdinarySet());
	}

	/**
	 * @return 1-bit minhash array of the set of normalized N-grams, i.e., duplicated N-grams are regarded as a single element.
	 */
	public byte[] getNormalizedHashIgnoreDuplicatedElements() {
		return computeMinHash(k, ngramMultiset.getNormalized().toOrdinarySet());
	}

	/**
	 * @return the number of distinct N-grams (without normalization).
	 */
	public int getUniqueNgramCount() {
		return ngramMultiset.getUniqueNgramCount();
	}
	
	/**
	 * @return a multiset of N-grams composed of tokens as they are.
	 * @see NgramMultiset#getRegular()
	 */
	public StringMultiset getNgramMultiset() {
		return ngramMultiset.getRegular();
	}

	/**
	 * @return a multiset of N-grams composed of normalized tokens.
	 * @see NgramMultiset#getNormalized()
	 */
	public StringMultiset getNormalizedNgramMultiset() {
		return ngramMultiset.getNormalized();
	}

}

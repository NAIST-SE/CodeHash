package jp.naist.se.codehash;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class MinHash implements IHash {

	private static final String HASH_FUNCTION = "SHA-1"; // Chosen for performance

	private long[] minhash;
	private int tokenCount;
	
	/**
	 * 1-bit minhash using k hash functions for N-gram Jaccard Index.  
	 * @param k the number of bits.  It should be a multiple of 8.  
	 * @param N
	 * @param reader
	 */
	public MinHash(int k, int N, TokenReader reader) {
		try {
			if (k <= 0) throw new IllegalArgumentException("k must be a positive integer. " + k);

			MessageDigest digest = MessageDigest.getInstance(HASH_FUNCTION);
			
			// Initialize minhash
			minhash = new long[k];
			Arrays.fill(minhash, Long.MAX_VALUE);

			byte[][] tokens = new byte[N][];
			boolean hasElement = true;
			while (hasElement) {
				hasElement = false;

				// Shift tokens 
				for (int i=0; i<tokens.length-1; ++i) {
					tokens[i] = tokens[i+1];
					if (tokens[i] != null) hasElement = true;
				}
				
				// Read a next token
				if (reader.next()) {
					String t = reader.getText();
					tokens[tokens.length-1] = (t != null) ? t.getBytes("UTF-8"): null;
					hasElement = true;
					tokenCount++;
				} else {
					tokens[tokens.length-1] = null;
				}
				
				if (hasElement) {
					// Calculate a hash for the N-gram 
					for (int i=0; i<N; i++) {
						if (tokens[i] != null) {
							digest.update(tokens[i]);
						} else {
							digest.update((byte)i);
						}
						digest.update((byte)0);
					}
					
					// Update minhash 
					byte[] h = digest.digest();
					for (int i=0; i<k; i++) {
						if (i > 0) {
							h = digest.digest(h);
						}
						
						long hash = extractLongHash(h);
						if (hash < minhash[i]) {
							minhash[i] = hash;
						}
					}
				}
			}
			
			
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
		
	private long extractLongHash(byte[] digest) {
		long hash = 0;
		for (int i=0; i<8; i++) {
			hash = (hash << 8) + digest[i];
		}
		return hash;
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
	
	@Override
	public int getTokenCount() {
		return tokenCount;
	}

}

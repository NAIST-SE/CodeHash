package jp.naist.se.codehash.comparison;

import java.util.BitSet;


public class MinHashEntry {

	private String filename;
	private String sha1;
	private String lang;
	private BitSet minhash;
	private int size;
	
	public MinHashEntry(String filename, String sha1, String lang, String bits, int size) {
		this.filename = filename;
		this.sha1 = sha1;
		this.lang = lang;
		this.minhash = BitSet.valueOf(hexStringToByteArray(bits));
		this.size = size;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public String getSha1() {
		return sha1;
	}
	
	public boolean equivalent(MinHashEntry another) {
		return this.sha1.equals(another.sha1);
	}
	
	/**
	 * This code comes from StackOverflow, June 2019.
	 * https://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
	 * @param s
	 * @return
	 */
	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	public double estimateSimilarity(MinHashEntry another) {
		if (this.lang.equals(another.lang)) {
			BitSet copy = (BitSet)minhash.clone(); 
			copy.xor(another.minhash);
			int differentBitCount = copy.cardinality();
			double sim = 1 - (differentBitCount * 1.0 / (copy.length() / 2));
			// The upper bound of similarity can be computed from N-gram set size
			double maxSim = Math.min(this.size, another.size) * 1.0 / Math.max(this.size, another.size);
			return Math.min(sim, maxSim);
		} else {
			return 0;
		}
	}

}

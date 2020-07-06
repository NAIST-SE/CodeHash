package jp.naist.se.codehash.comparison;

import java.util.BitSet;

import jp.naist.se.codehash.HashStringUtil;


public class MinHashEntry {

	private String filename;
	private String sha1;
	private String lang;
	private String codehash;
	private BitSet minhash;
	private BitSet normalizedMinhash;
	private long fileLength;
	private int tokenLength;
	private int size;
	
	public MinHashEntry(String filename, String sha1, String lang, String codehash, String bits, String normalizedBits, long fileLength, int tokenLength, int size) {
		this.filename = filename;
		this.sha1 = sha1;
		this.lang = lang;
		this.codehash = codehash;
		this.minhash = BitSet.valueOf(HashStringUtil.hexToBytes(bits));
		this.normalizedMinhash = BitSet.valueOf(HashStringUtil.hexToBytes(normalizedBits));
		this.fileLength = fileLength;
		this.tokenLength = tokenLength;
		this.size = size;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public String getSha1() {
		return sha1;
	}
	
	public String getCodehash() {
		return codehash;
	}
	
	public boolean equivalent(MinHashEntry another) {
		return this.sha1.equals(another.sha1);
	}
	
	public long getFileLength() {
		return fileLength;
	}
	
	public int getTokenLength() {
		return tokenLength;
	}
	
	private double estimateSimilarity(MinHashEntry another, BitSet thisSet, BitSet anotherSet) {
		if (this.lang.equals(another.lang)) {
			BitSet copy = (BitSet)thisSet.clone(); 
			copy.xor(anotherSet);
			int differentBitCount = copy.cardinality();
			double sim = 1 - (differentBitCount * 1.0 / (copy.size() / 2));
			// The upper bound of similarity can be computed from N-gram set size
			double maxSim = Math.min(this.size, another.size) * 1.0 / Math.max(this.size, another.size);
			return Math.min(sim, maxSim);
		} else {
			return 0;
		}
	}
	
	public double estimateSimilarity(MinHashEntry another) {
		return estimateSimilarity(another, this.minhash, another.minhash);
	}

	public double estimateNormalizedSimilarity(MinHashEntry another) {
		return estimateSimilarity(another, this.normalizedMinhash, another.normalizedMinhash);
	}


}

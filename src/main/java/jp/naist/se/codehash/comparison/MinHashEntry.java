package jp.naist.se.codehash.comparison;

import jp.naist.se.codehash.hash.BitMinHash;


/**
 * A line of a minhash list produced by FileCodeHash.
 * {@link ComparisonMain} compares entries to estimate similarity.
 */
public class MinHashEntry {

	private String filename;
	private String sha1;
	private String lang;
	private String codehash;
	private BitMinHash minhash;
	private BitMinHash normalizedMinhash;
	private long fileLength;
	private int tokenLength;
	private int group;

	public MinHashEntry(String filename, String sha1, String lang, String codehash, String bits, String normalizedBits, long fileLength, int tokenLength, int size) {
		this(filename, sha1, lang, codehash, bits, normalizedBits, fileLength, tokenLength, size, 0);
	}

	/**
	 * @param group specifies a group of the entry, e.g. an index of an input file.
	 */
	public MinHashEntry(String filename, String sha1, String lang, String codehash, String bits, String normalizedBits, long fileLength, int tokenLength, int size, int group) {
		this.filename = filename;
		this.sha1 = sha1;
		this.lang = lang;
		this.codehash = codehash;
		this.minhash = new BitMinHash(bits, size);
		this.normalizedMinhash = new BitMinHash(normalizedBits, size);
		this.fileLength = fileLength;
		this.tokenLength = tokenLength;
		this.group = group;
	}

	/**
	 * @return the group of the entry.
	 */
	public int getGroup() {
		return group;
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

	public double getMaxSimilarity(MinHashEntry another) {
		return minhash.getMaxSimilarity(another.minhash);
	}

	public int compareTo(MinHashEntry another) {
		return minhash.getNgramCount() - another.minhash.getNgramCount();
	}

	public boolean isSameLanguage(MinHashEntry another) {
		return this.lang.equals(another.lang);
	}

	/**
	 * @return the estimated similarity.  0 if the files are written in different languages.
	 */
	public double estimateSimilarity(MinHashEntry another) {
		return isSameLanguage(another) ? minhash.estimateSimilarity(another.minhash) : 0;
	}

	/**
	 * @return the estimated similarity with normalization.  0 if the files are written in different languages.
	 */
	public double estimateNormalizedSimilarity(MinHashEntry another) {
		return isSameLanguage(another) ? normalizedMinhash.estimateSimilarity(another.normalizedMinhash) : 0;
	}

}

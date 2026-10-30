package jp.naist.se.codehash.hash;

import java.io.InputStream;

import jp.naist.se.codehash.sha1.SHA1MinHash;
import jp.naist.se.codehash.sha1.SHA1MinHashMSR2017;
import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.token.TokenReader;
import jp.naist.se.codehash.util.HashStringUtil;
import jp.naist.se.codehash.util.StringMultiset;

/**
 * Tokenizes a source file and computes its codehash and minhash.
 */
public class SourceCodeHash {

	public enum MinHashType {
		/** Compute codehash only */
		None,
		/** 1-bit minhash using MurmurHash3 */
		Murmur3,
		/** 1-bit minhash using MurmurHash3, using a set rather than a multiset of N-grams */
		Murmur3IgnoreDuplication,
		SHA1,
		SHA1InPaper
	};

	private String codehash;
	private String minhash;
	private String normalizedMinhash;
	private int tokenCount;
	private int ngramCount;
	private StringMultiset ngrams;
	private StringMultiset normalizedNgrams;

	/**
	 * Compute hash values using the default N-gram size.
	 * @see #SourceCodeHash(FileType, InputStream, long, MinHashType, int)
	 */
	public SourceCodeHash(FileType type, InputStream content, long size, MinHashType minhashType) {
		this(type, content, size, minhashType, MinHashDefaults.DEFAULT_NGRAM_SIZE);
	}

	/**
	 * Compute hash values for a source file.
	 * @param type specifies a programming language to parse the content.
	 * @param content is the content of a file.
	 * @param size is the byte length of the content.  It is used to allocate a buffer.
	 * @param minhashType specifies a minhash algorithm.
	 * @param N specifies the size of N-gram for minhash.
	 */
	public SourceCodeHash(FileType type, InputStream content, long size, MinHashType minhashType, int N) {
		TokenReader tokenReader = FileType.createReader(type, content);
		CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, size);
		switch (minhashType) {
		case Murmur3:
		case Murmur3IgnoreDuplication:
		{
			MurmurMinHash h = new MurmurMinHash(MinHashDefaults.BIT_COUNT, N, wrapper);
			if (minhashType == MinHashType.Murmur3IgnoreDuplication) {
				minhash = HashStringUtil.bytesToHex(h.getHashIgnoreDuplicatedElements());
				normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHashIgnoreDuplicatedElements());
			} else {
				minhash = HashStringUtil.bytesToHex(h.getHash());
				normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHash());
			}
			ngramCount = h.getNgramCount();
			ngrams = h.getNgramMultiset();
			normalizedNgrams = h.getNormalizedNgramMultiset();
			break;
		}
		case SHA1:
		{
			SHA1MinHash h = new SHA1MinHash(MinHashDefaults.BIT_COUNT, N, wrapper);
			minhash = HashStringUtil.bytesToHex(h.getHash());
			break;
		}
		case SHA1InPaper:
		{
			SHA1MinHashMSR2017 h = new SHA1MinHashMSR2017(MinHashDefaults.BIT_COUNT, N, wrapper);
			minhash = HashStringUtil.bytesToHex(h.getHash());
			break;
		}
		case None:
			// Read all tokens to compute codehash only
			while (wrapper.next());
			break;
		}
		codehash = HashStringUtil.bytesToHex(wrapper.getHash());
		tokenCount = tokenReader.getTokenCount();
	}

	public String getCodeHash() {
		return codehash;
	}

	/**
	 * @return minhash value.  null if MinHashType.None is specified.
	 */
	public String getMinHash() {
		return minhash;
	}

	/**
	 * @return minhash value of normalized N-grams.  null unless a Murmur3 minhash is specified.
	 */
	public String getNormalizedMinHash() {
		return normalizedMinhash;
	}

	public int getTokenCount() {
		return tokenCount;
	}

	/**
	 * @return the number of N-grams.  0 unless a Murmur3 minhash is specified.
	 */
	public int getNgramCount() {
		return ngramCount;
	}

	/**
	 * @return N-grams of the file.  null unless a Murmur3 minhash is specified.
	 */
	public StringMultiset getNgramMultiset() {
		return ngrams;
	}

	/**
	 * @return normalized N-grams of the file.  null unless a Murmur3 minhash is specified.
	 */
	public StringMultiset getNormalizedNgramMultiset() {
		return normalizedNgrams;
	}

}

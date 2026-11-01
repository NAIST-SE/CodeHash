package jp.naist.se.codehash.hash;

import jp.naist.se.codehash.util.StringMultiset;

public class NgramMultiset {

	private int ngramCount;
	private StringMultiset regular;
	private StringMultiset normalized;
	
	public NgramMultiset(NgramReader ngramReader) {
		regular = new StringMultiset(2048);
		normalized = new StringMultiset(2048);
		
		while (ngramReader.next()) {
			// Calculate a hash for the N-gram 
			StringBuilder builder = new StringBuilder(128);
			for (int i=0; i<ngramReader.getN(); i++) {
				if (ngramReader.getToken(i) != null) {
					builder.append(ngramReader.getToken(i));
				} else {
					builder.append((char)i);
				}
				builder.append((char)0);
			}
			regular.add(builder.toString());

			// Calculate a hash for the N-gram 
			builder = new StringBuilder(128);
			for (int i=0; i<ngramReader.getN(); i++) {
				if (ngramReader.getNormalizedToken(i) != null) {
					builder.append(ngramReader.getNormalizedToken(i));
				} else {
					builder.append((char)i);
				}
				builder.append((char)0);
			}
			normalized.add(builder.toString());
		}
		
		ngramCount = ngramReader.getNgramCount();
	}
	
	/**
	 * @return a multiset of N-grams composed of tokens as they are.
	 * An element is a string concatenating the tokens, each followed by '\0'.
	 * A missing token at the beginning/end of a file is represented by a character whose code is the position in the N-gram.
	 */
	public StringMultiset getRegular() {
		return regular;
	}
	
	/**
	 * @return a multiset of N-grams composed of normalized tokens.
	 * Normalization ignores differences such as variable names, following the rules of each programming language.
	 * The format of an element is the same as {@link #getRegular()}.
	 */
	public StringMultiset getNormalized() {
		return normalized;
	}
	
	/**
	 * @return the number of N-grams including duplicated ones.
	 * It includes N-grams containing missing tokens at the beginning/end of a file.
	 */
	public int getNgramCount() {
		return ngramCount;
	}

	/**
	 * @return the number of distinct N-grams (without normalization).
	 */
	public int getUniqueNgramCount() {
		return regular.size();
	}

}

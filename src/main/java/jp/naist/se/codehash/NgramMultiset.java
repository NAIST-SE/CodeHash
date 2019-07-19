package jp.naist.se.codehash;

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
	
	public StringMultiset getRegular() {
		return regular;
	}
	
	public StringMultiset getNormalized() {
		return normalized;
	}
	
	public int getNgramCount() {
		return ngramCount;
	}

	public int getUniqueNgramCount() {
		return regular.size();
	}

}

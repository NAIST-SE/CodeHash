package jp.naist.se.codehash;

import java.nio.charset.StandardCharsets;

public class NgramReader {

	private int ngramCount;

	private TokenReader reader;
	private byte[][] tokens;
	
	public NgramReader(int N, TokenReader reader) {
		this.tokens = new byte[N][];
		this.reader = reader;
	}
	
	/**
	 * Proceed to the next n-gram.
	 * @return true if the next n-gram is available.
	 */
	public boolean next() {
		boolean hasElement = false;

		// Shift tokens 
		for (int i=0; i<tokens.length-1; ++i) {
			tokens[i] = tokens[i+1];
			if (tokens[i] != null) hasElement = true;
		}
			
		// Read a next token
		if (reader.next()) {
			String t = reader.getText();
			tokens[tokens.length-1] = (t != null) ? t.getBytes(StandardCharsets.UTF_8): null;
			hasElement = true;
		} else {
			tokens[tokens.length-1] = null;
		}
		
		if (hasElement) ngramCount++;

		return hasElement;
	}

	/**
	 * @param i specifies an index (0 <= i < N).
	 * @return the content of i-th token.  It may be null if the token is unavailable (at the begin/end of a file). 
	 */
	public byte[] getToken(int i) { 
		return tokens[i];
	}
	
	/**
	 * @return The number of n-grams returned by the reader.
	 */
	public int getNgramCount() {
		return ngramCount;
	}
}

package jp.naist.se.codehash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A token reader that computes codehash (ignoring comments and whitespace) during the process of tokens.
 */
public class CodeHashTokenReader implements TokenReader {

	private static final String HASH_ALGORITHM = "SHA-1";
	private TokenReader parent;
	private StringBuilder recorder;
	
	/**
	 * 
	 * @param parent
	 * @param filesize specifies the file size processed by the given token reader.  
	 * This is used to allocate an appropriate size of buffer just for efficiency. 
	 */
	public CodeHashTokenReader(TokenReader parent, long filesize) {
		this.parent = parent;
		this.recorder = new StringBuilder((int)filesize);
	}

	@Override
	public boolean next() {
		boolean hasNext = parent.next();
		if (hasNext) {
			String token = parent.getText();
			// PHPLexer may return null
			if (token != null) { 
				recorder.append(token);
				recorder.append('\0');
			}
		}
		return hasNext;
	}
	
	@Override
	public String getText() {
		return parent.getText();
	}
	
	@Override
	public String getNormalizedText() {
		return parent.getNormalizedText();
	}

	@Override
	public int getCharPositionInLine() {
		return parent.getCharPositionInLine();
	}
	
	@Override
	public int getLine() {
		return parent.getLine();
	}
	
	/**
	 * @return the number of tokens processed by the reader.
	 */
	@Override
	public int getTokenCount() {
		return parent.getTokenCount();
	}
	
	/**
	 * @return a resultant hash of source code content . 
	 */
	public byte[] getHash() {
		try {
			MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
			return digest.digest(recorder.toString().getBytes());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to compute SHA-1 hash", e);
		}
	}
	
}

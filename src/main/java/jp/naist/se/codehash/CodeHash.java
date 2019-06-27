package jp.naist.se.codehash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A hash computation that ignores comments and white space in the content
 */
public class CodeHash {

	private byte[] codehash;
	
	public CodeHash(TokenReader tokenReader, long size) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			StringBuilder builder = new StringBuilder((int)size);
			while (tokenReader.next()) {
				String token = tokenReader.getText();
				// PHPLexer may return null
				if (token != null) { 
					builder.append(token);
					builder.append('\0');
				}
			}
			codehash = digest.digest(builder.toString().getBytes());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to compute SHA-1 hash", e);
		}
	}
	
	public byte[] getHash() {
		return codehash;
	}
	
}

package jp.naist.se.codehash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CodeHash implements IHash {

	private byte[] codehash;
	private int tokenCount;
	
	public CodeHash(TokenReader tokenReader, long size) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			StringBuilder builder = new StringBuilder((int)size);
			tokenCount = 0;
			while (tokenReader.next()) {
				String token = tokenReader.getText();
				// PHPLexer may return null
				if (token != null) { 
					builder.append(token);
					builder.append('\0');
					tokenCount++;
				}
			}
			codehash = digest.digest(builder.toString().getBytes());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to compute SHA-1 hash", e);
		}
	}
	
	@Override
	public byte[] getHash() {
		return codehash;
	}
	
	@Override
	public int getTokenCount() {
		return tokenCount;
	}
}

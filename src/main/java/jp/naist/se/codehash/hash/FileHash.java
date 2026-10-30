package jp.naist.se.codehash.hash;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jp.naist.se.codehash.util.HashStringUtil;

/**
 * A hash of a file content (including comments and white space).
 */
public class FileHash {

	private static final String HASH_ALGORITHM = "SHA-1";

	/**
	 * @param content is the content of a file.
	 * @return a hexadecimal string of the SHA-1 hash of the content.
	 */
	public static String compute(byte[] content) {
		try {
			MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
			return HashStringUtil.bytesToHex(digest.digest(content));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to compute SHA-1 hash", e);
		}
	}

}

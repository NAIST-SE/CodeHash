package jp.naist.se.codehash;

public class HashStringUtil {

	
	public static byte[] hexToBytes(String s) {
	    byte[] bytes = new byte[s.length()/2];
	    for (int i=0; i<bytes.length; i++) {
	    	bytes[i] = (byte)Integer.parseInt(s.substring(i*2, i*2+2), 16);
	    }
	    return bytes;
	}
	
	
	public static String bytesToHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(bytes.length * 2);
	    for (int i=0; i<bytes.length; i++) {
	    	int value = Byte.toUnsignedInt(bytes[i]);
	        builder.append(Character.forDigit(value / 16, 16));
	        builder.append(Character.forDigit(value % 16, 16));
	    }
	    return builder.toString();
	}


}

package jp.naist.se.codehash;

import org.junit.Assert;
import org.junit.Test;


public class HashStringUtilTest {

	@Test
	public void testHexStringToByteArray() { 
		String s = "00010210ff";
		byte[] a = HashStringUtil.hexToBytes(s);
		Assert.assertEquals(5, a.length);
		Assert.assertEquals(0, a[0]);
		Assert.assertEquals(1, a[1]);
		Assert.assertEquals(2, a[2]);
		Assert.assertEquals(16, a[3]);
		Assert.assertEquals(-1, a[4]);
		Assert.assertEquals(s, HashStringUtil.bytesToHex(a));
	
		String s2 = "7aab";
		byte[] a2 = HashStringUtil.hexToBytes(s2);
		Assert.assertEquals(2, a2.length);
		Assert.assertEquals(122, a2[0]);
		Assert.assertEquals(-85, a2[1]);
		Assert.assertEquals(s2, HashStringUtil.bytesToHex(a2));
	}
	
	@Test
	public void testByteArrayToHexString() {
		byte[] b = new byte[] { 0, -128, 64, 90, 32, 15, 16 };
		String s = HashStringUtil.bytesToHex(b);
		Assert.assertEquals(14, s.length());
		Assert.assertEquals("0080405a200f10", s);
		Assert.assertArrayEquals(b, HashStringUtil.hexToBytes(s));
	}
}

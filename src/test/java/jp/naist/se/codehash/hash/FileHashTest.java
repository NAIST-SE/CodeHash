package jp.naist.se.codehash.hash;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class FileHashTest {

	@Test
	public void testCompute() {
		// Test vectors of SHA-1 (FIPS 180)
		Assert.assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", FileHash.compute("abc".getBytes(StandardCharsets.US_ASCII)));
		Assert.assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", FileHash.compute(new byte[0]));
	}
}

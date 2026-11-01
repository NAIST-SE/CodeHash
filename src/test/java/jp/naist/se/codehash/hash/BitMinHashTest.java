package jp.naist.se.codehash.hash;

import org.junit.Assert;
import org.junit.Test;


public class BitMinHashTest {

	@Test
	public void testIdentical() {
		BitMinHash h1 = new BitMinHash("f0f0", 10);
		BitMinHash h2 = new BitMinHash("f0f0", 10);
		Assert.assertEquals(1.0, h1.estimateSimilarity(h2), 0);
	}

	@Test
	public void testDifferentBits() {
		// 4 of 16 bits are different: 1 - 4 / (16 / 2)
		BitMinHash h1 = new BitMinHash("f0f0", 10);
		BitMinHash h2 = new BitMinHash("00f0", 10);
		Assert.assertEquals(0.5, h1.estimateSimilarity(h2), 0);
		Assert.assertEquals(0.5, h2.estimateSimilarity(h1), 0);
	}

	@Test
	public void testTrailingZeroBytes() {
		// The number of bits is determined by the string length, even if trailing bytes are zero
		BitMinHash h1 = new BitMinHash("ff00", 10);
		BitMinHash h2 = new BitMinHash("0f00", 10);
		Assert.assertEquals(0.5, h1.estimateSimilarity(h2), 0);
	}

	@Test
	public void testMaxSimilarity() {
		// Identical bits, but the similarity is limited by the numbers of N-grams
		BitMinHash h1 = new BitMinHash("f0f0", 10);
		BitMinHash h2 = new BitMinHash("f0f0", 40);
		Assert.assertEquals(0.25, h1.getMaxSimilarity(h2), 0);
		Assert.assertEquals(0.25, h1.estimateSimilarity(h2), 0);
		Assert.assertEquals(10, h1.getNgramCount());
	}

}

package jp.naist.se.codehash;

import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.sha1.SHA1MinHash;
import jp.naist.se.codehash.util.StringMultiset;

public class MinHashTest {

	private static final int MINHASH_BITCOUNT = 2048;
	private static final int NGRAM = 3;
	
	@Test
	public void testSHA1Minhash() {
		TestTokenReader t1 = new TestTokenReader(0, 100);
		SHA1MinHash h1 = new SHA1MinHash(MINHASH_BITCOUNT, NGRAM, t1);

		TestTokenReader t2 = new TestTokenReader(0, 100);
		SHA1MinHash h2 = new SHA1MinHash(MINHASH_BITCOUNT, NGRAM, t2);

		// The same n-gram must be the same hash
		Assert.assertArrayEquals(h1.getHash(), h2.getHash());

		TestTokenReader t3 = new TestTokenReader(0, 99);
		SHA1MinHash h3 = new SHA1MinHash(MINHASH_BITCOUNT, NGRAM, t3);

		double estimated = estimateSimilarlity(h1.getHash(), h3.getHash());
		Assert.assertEquals(0.99, estimated, 0.05);

		TestTokenReader t4 = new TestTokenReader(10, 90);
		SHA1MinHash h4 = new SHA1MinHash(MINHASH_BITCOUNT, NGRAM, t4);
		estimated = estimateSimilarlity(h1.getHash(), h4.getHash());
		Assert.assertEquals(0.80, estimated, 0.10);

		TestTokenReader t5 = new TestTokenReader(20, 90);
		SHA1MinHash h5 = new SHA1MinHash(MINHASH_BITCOUNT, NGRAM, t5);
		estimated = estimateSimilarlity(h1.getHash(), h5.getHash());
		Assert.assertEquals(0.70, estimated, 0.10);

		// Commented out due to its poor performance
//		for (int i=0; i<30; i++) {
//			TestTokenReader t6 = new TestTokenReader(i, 100-i);
//			MinHash h6 = new MinHash(MINHASH_BITCOUNT, 3, t6);
//			estimated = estimateSimilarlity(h1.getHash(), h6.getHash());
//			Assert.assertEquals(1-i*0.01, estimated, 0.05);
//		}
//
//		for (int i=0; i<30; i++) {
//			TestTokenReader t6 = new TestTokenReader(0, 100+i);
//			MinHash h6 = new MinHash(MINHASH_BITCOUNT, 3, t6);
//			estimated = estimateSimilarlity(h1.getHash(), h6.getHash());
//			Assert.assertEquals(100.0/(100+i), estimated, 0.05);
//		}
	}

	@Test
	public void testMinhash() {
		TestTokenReader t1 = new TestTokenReader(0, 100);
		MurmurMinHash h1 = new MurmurMinHash(MINHASH_BITCOUNT, NGRAM, t1);
		TestTokenReader t2 = new TestTokenReader(0, 100);
		MurmurMinHash h2 = new MurmurMinHash(MINHASH_BITCOUNT, NGRAM, t2);
		Assert.assertArrayEquals(h1.getHash(), h2.getHash());

		StringMultiset ngrams1 = getNgramSet(0, 100);
		for (int i=0; i<30; i++) {
			for (int j=80; j<130; j++) {
				// estimate similarity
				TestTokenReader t3 = new TestTokenReader(i, j);
				MurmurMinHash h3 = new MurmurMinHash(MINHASH_BITCOUNT, NGRAM, t3);
				double estimated = estimateSimilarlity(h1.getHash(), h3.getHash());

				// compute actual
				StringMultiset ngrams3 = getNgramSet(i, j);
				int intersection = ngrams1.intersection(ngrams3);
				double actualSim = intersection * 1.0 / (ngrams1.size() + ngrams3.size() - intersection);

				Assert.assertEquals(actualSim, estimated, 0.04);
			}
		}
	}
	
	private StringMultiset getNgramSet(int i, int count) {
		StringMultiset ngrams = new StringMultiset(count);
		NgramReader r = new NgramReader(3, new TestTokenReader(i, count));
		while (r.next()) {
			ngrams.add(r.getToken(0) + "\0" + r.getToken(1) + "\0" + r.getToken(2) + "\0");
		}
		return ngrams;
	}

	/**
	 * Estimate a similarity value from 1-bit minhash (k=2048).
	 * @param hash1
	 * @param hash2
	 * @return
	 */
	public double estimateSimilarlity(byte[] hash1, byte[] hash2) {
		BitSet b1 = BitSet.valueOf(hash1);
		BitSet b2 = BitSet.valueOf(hash2);
		b1.xor(b2);
		int differentBitCount = b1.cardinality();
		return 1 - (differentBitCount * 1.0 / (hash1.length * 8 / 2));
	}
	
	
	public class TestTokenReader implements TokenReader {
		
		private int index;
		private int start;
		private int count;
		
		public TestTokenReader(int start, int count) {
			this.start = start;
			this.count = count;
			this.index = 0;
		}
		
		@Override
		public boolean next() {
			index++;
			return index < count;
		}
		
		@Override
		public String getText() {
			return Integer.toString(start + index);
		}
		
		@Override
		public int getLine() {
			return index;
		}
		
		@Override
		public int getCharPositionInLine() {
			return index;
		}
		
		@Override
		public int getTokenCount() {
			return index;
		}
		
		
	}
}

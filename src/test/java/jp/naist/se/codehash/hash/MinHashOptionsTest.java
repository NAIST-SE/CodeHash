package jp.naist.se.codehash.hash;

import java.io.OutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;


public class MinHashOptionsTest {

	@Test
	public void testDefault() {
		MinHashOptions options = new MinHashOptions();
		Assert.assertFalse(options.parse("file.c"));
		Assert.assertFalse(options.isInvalid());
		Assert.assertEquals(MinHashOptions.DEFAULT_NGRAM_SIZE, options.getN());
		Assert.assertFalse(options.isIgnoreDuplication());
		Assert.assertEquals(MinHashType.Murmur3, options.getMinHashType(MinHashType.Murmur3));
	}

	@Test
	public void testOptions() {
		MinHashOptions options = new MinHashOptions();
		Assert.assertTrue(options.parse("-n:5"));
		Assert.assertTrue(options.parse("-ignoreduplication"));
		Assert.assertFalse(options.isInvalid());
		Assert.assertEquals(5, options.getN());
		// The minimum and maximum values are accepted
		Assert.assertTrue(options.parse("-n:1"));
		Assert.assertEquals(1, options.getN());
		Assert.assertTrue(options.parse("-n:" + MinHashOptions.MAX_NGRAM_SIZE));
		Assert.assertEquals(MinHashOptions.MAX_NGRAM_SIZE, options.getN());
		Assert.assertFalse(options.isInvalid());
		Assert.assertTrue(options.isIgnoreDuplication());
		Assert.assertEquals(MinHashType.Murmur3IgnoreDuplication, options.getMinHashType(MinHashType.Murmur3));
		// Other types are not affected
		Assert.assertEquals(MinHashType.None, options.getMinHashType(MinHashType.None));
		Assert.assertEquals(MinHashType.SHA1, options.getMinHashType(MinHashType.SHA1));
	}

	@Test
	public void testInvalidNgramSize() {
		PrintStream originalErr = System.err;
		try {
			System.setErr(new PrintStream(OutputStream.nullOutputStream()));
			for (String arg: new String[] { "-n:0", "-n:17", "-n:abc" }) {
				MinHashOptions options = new MinHashOptions();
				Assert.assertTrue(options.parse(arg));
				Assert.assertTrue(arg, options.isInvalid());
				Assert.assertEquals(MinHashOptions.DEFAULT_NGRAM_SIZE, options.getN());
			}
		} finally {
			System.setErr(originalErr);
		}
	}

}

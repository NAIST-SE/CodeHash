package jp.naist.se.codehash.normalizer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.NgramReader;
import jp.naist.se.codehash.TokenReader;

public class NormalizationTest {

	@Test
	public void testCpp() {
		String sample = "int x; int y; int x = foo();";
		TokenReader r = FileType.createReader(FileType.CPP, new ByteArrayInputStream(sample.getBytes(StandardCharsets.UTF_8)));
		Assert.assertTrue(r.next());
		Assert.assertEquals("int", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(CPP14Normalizer.NORMALIZED_TOKEN, r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(";", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals("int", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(CPP14Normalizer.NORMALIZED_TOKEN, r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(";", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals("int", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(CPP14Normalizer.NORMALIZED_TOKEN, r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals("=", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(CPP14Normalizer.NORMALIZED_TOKEN, r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals("(", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(")", r.getNormalizedText());
		Assert.assertTrue(r.next());
		Assert.assertEquals(";", r.getNormalizedText());
		Assert.assertFalse(r.next());
	}
	
	@Test
	public void testCppNgrams() {
		String sample = "int x; int y; int x = foo();";
		TokenReader r = FileType.createReader(FileType.CPP, new ByteArrayInputStream(sample.getBytes(StandardCharsets.UTF_8)));
		NgramReader ngrams = new NgramReader(3, r);
		Assert.assertTrue(ngrams.next());
		Assert.assertEquals(null, ngrams.getToken(0));
		Assert.assertEquals(null, ngrams.getToken(1));
		Assert.assertEquals("int", ngrams.getToken(2));
		Assert.assertEquals(null, ngrams.getNormalizedToken(0));
		Assert.assertEquals(null, ngrams.getNormalizedToken(1));
		Assert.assertEquals("int", ngrams.getNormalizedToken(2));

		Assert.assertTrue(ngrams.next());
		Assert.assertEquals(null, ngrams.getToken(0));
		Assert.assertEquals("int", ngrams.getToken(1));
		Assert.assertEquals("x", ngrams.getToken(2));
		Assert.assertEquals(null, ngrams.getNormalizedToken(0));
		Assert.assertEquals("int", ngrams.getNormalizedToken(1));
		Assert.assertEquals(CPP14Normalizer.NORMALIZED_TOKEN, ngrams.getNormalizedToken(2));

		Assert.assertTrue(ngrams.next());
		Assert.assertEquals("int", ngrams.getToken(0));
		Assert.assertEquals("x", ngrams.getToken(1));
		Assert.assertEquals(";", ngrams.getToken(2));
		Assert.assertEquals("int", ngrams.getNormalizedToken(0));
		Assert.assertEquals(CPP14Normalizer.NORMALIZED_TOKEN, ngrams.getNormalizedToken(1));
		Assert.assertEquals(";", ngrams.getNormalizedToken(2));

		Assert.assertTrue(ngrams.next()); // "int"
		Assert.assertTrue(ngrams.next()); // "y"
		Assert.assertTrue(ngrams.next()); // ";"
		Assert.assertTrue(ngrams.next()); // "int"
		Assert.assertTrue(ngrams.next()); // "x"
		Assert.assertTrue(ngrams.next()); // "="
		Assert.assertTrue(ngrams.next()); // "foo"
		Assert.assertTrue(ngrams.next()); // "("
		Assert.assertTrue(ngrams.next()); // ")"
		Assert.assertTrue(ngrams.next()); // ";"
		Assert.assertTrue(ngrams.next()); // ")", ";", null
		Assert.assertTrue(ngrams.next()); // ";", null, null
		Assert.assertEquals(";", ngrams.getToken(0));
		Assert.assertEquals(null, ngrams.getToken(1));
		Assert.assertEquals(null, ngrams.getToken(2));
		Assert.assertEquals(";", ngrams.getNormalizedToken(0));
		Assert.assertEquals(null, ngrams.getNormalizedToken(1));
		Assert.assertEquals(null, ngrams.getNormalizedToken(2));
		Assert.assertFalse(ngrams.next());

	}

}

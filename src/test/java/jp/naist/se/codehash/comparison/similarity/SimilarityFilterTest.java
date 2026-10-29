package jp.naist.se.codehash.comparison.similarity;

import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.file.FileEntity;


public class SimilarityFilterTest {

	private static SimilarityRecord createRecord() {
		FileEntity e1 = new FileEntity("A.java", FileType.JAVA, "class A { int x = 1; int y = 2; }".getBytes(StandardCharsets.UTF_8), 3);
		FileEntity e2 = new FileEntity("B.java", FileType.JAVA, "class B { int x = 1; int z = 3; }".getBytes(StandardCharsets.UTF_8), 3);
		return new SimilarityRecord(new FilePair(e1, e2));
	}

	private static double value(SimilarityRecord r, String name) {
		return r.getValue(SimilarityMetrics.get(name));
	}

	@Test
	public void testBuiltinMetrics() {
		SimilarityRecord r = createRecord();
		FilePair p = r.getPair();
		int n1 = p.getFirst().getNgramCount();
		int n2 = p.getSecond().getNgramCount();
		int i = p.getIntersection();
		int ni = p.getNormalizedIntersection();
		Assert.assertEquals(i * 1.0 / (n1 + n2 - i), value(r, SimilarityMetrics.EXACT_JACCARD), 1e-9);
		Assert.assertEquals(i * 1.0 / Math.min(n1, n2), value(r, SimilarityMetrics.EXACT_OVERLAP_COEFFICIENT), 1e-9);
		Assert.assertEquals(i * 1.0 / Math.max(n1, n2), value(r, SimilarityMetrics.EXACT_OVERLAP_SIMILARITY), 1e-9);
		Assert.assertEquals(ni * 1.0 / (n1 + n2 - ni), value(r, SimilarityMetrics.JACCARD), 1e-9);
		Assert.assertEquals(ni * 1.0 / Math.min(n1, n2), value(r, SimilarityMetrics.OVERLAP_COEFFICIENT), 1e-9);
		Assert.assertEquals(ni * 1.0 / Math.max(n1, n2), value(r, SimilarityMetrics.OVERLAP_SIMILARITY), 1e-9);
		// Identifier normalization makes the files more similar
		Assert.assertTrue(value(r, SimilarityMetrics.JACCARD) > value(r, SimilarityMetrics.EXACT_JACCARD));
		Assert.assertTrue(value(r, SimilarityMetrics.EXACT_JACCARD) > 0);
	}

	private static boolean eval(String expr, SimilarityRecord r) {
		Predicate<SimilarityRecord> p = SimilarityFilter.parse(expr);
		return p.test(r);
	}

	@Test
	public void testComparison() {
		SimilarityRecord r = createRecord();
		double j = value(r, SimilarityMetrics.JACCARD);
		Assert.assertTrue(eval("jaccard >= " + j, r));
		Assert.assertFalse(eval("jaccard > " + j, r));
		Assert.assertTrue(eval("jaccard <= " + j, r));
		Assert.assertFalse(eval("jaccard < " + j, r));
		Assert.assertTrue(eval("jaccard == " + j, r));
		Assert.assertTrue(eval("jaccard = " + j, r));
		Assert.assertFalse(eval("jaccard != " + j, r));
		Assert.assertTrue(eval("0<=jaccard", r));
		Assert.assertTrue(eval("jaccard>=exact-jaccard", r));
		Assert.assertTrue(eval("jaccard>=1e-3", r));
	}

	@Test
	public void testLogicalOperators() {
		SimilarityRecord r = createRecord();
		Assert.assertTrue(eval("jaccard>=0 && overlap-coefficient>=0", r));
		Assert.assertFalse(eval("jaccard>=0 and overlap-coefficient>1", r));
		Assert.assertTrue(eval("jaccard>1 || overlap-coefficient>=0", r));
		Assert.assertTrue(eval("jaccard>1 OR overlap-coefficient>=0", r));
		Assert.assertFalse(eval("!(jaccard>=0)", r));
		Assert.assertTrue(eval("not jaccard>1", r));
		// "and" has a higher precedence than "or"
		Assert.assertTrue(eval("jaccard>=0 or jaccard>1 and jaccard>1", r));
		Assert.assertFalse(eval("(jaccard>=0 or jaccard>1) and jaccard>1", r));
	}

	@Test
	public void testCustomMetric() {
		SimilarityMetrics.register(new SimilarityMetric() {
			@Override
			public String getName() {
				return "test-constant";
			}
			@Override
			public double calculate(FilePair pair) {
				return 0.5;
			}
		});
		SimilarityRecord r = createRecord();
		Assert.assertTrue(eval("test-constant == 0.5", r));
		Assert.assertTrue(SimilarityMetrics.getNames().contains("test-constant"));
	}

	private static void assertInvalid(String expr) {
		try {
			SimilarityFilter.parse(expr);
			Assert.fail("Expected an error: " + expr);
		} catch (IllegalArgumentException e) {
			// OK
		}
	}

	@Test
	public void testInvalidExpressions() {
		assertInvalid("");
		assertInvalid("jaccard");
		assertInvalid("unknown-metric>=0.5");
		assertInvalid("jaccard>=");
		assertInvalid("(jaccard>=0.5");
		assertInvalid("jaccard>=0.5)");
		assertInvalid("jaccard>=0.5 and");
		assertInvalid("jaccard>=0.5.1");
		assertInvalid("jaccard>=0.5 # comment");
	}

}

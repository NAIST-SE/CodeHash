package jp.naist.se.codehash.util;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;


public class IntMultisetTest {

	@Test
	public void testIntersection() {
		StringDictionary dictionary = new StringDictionary();
		StringMultiset ms = new StringMultiset();
		ms.add("a");
		ms.add("a");
		ms.add("b");
		StringMultiset ms2 = new StringMultiset();
		ms2.add("a");
		ms2.add("c");
		ms2.add("c");

		IntMultiset i1 = new IntMultiset(ms, dictionary);
		IntMultiset i2 = new IntMultiset(ms2, dictionary);
		Assert.assertEquals(3, i1.size());
		Assert.assertEquals(2, i1.distinctSize());
		Assert.assertEquals(1, i1.intersection(i2));
		Assert.assertEquals(1, i2.intersection(i1));
		Assert.assertEquals(3, i1.intersection(i1));
		Assert.assertEquals(3, dictionary.size());

		IntMultiset empty = new IntMultiset(new StringMultiset(), dictionary);
		Assert.assertEquals(0, empty.size());
		Assert.assertEquals(0, empty.intersection(i1));
		Assert.assertEquals(0, i1.intersection(empty));
	}

	@Test
	public void testSameAsStringMultiset() {
		// The intersection is the same as StringMultiset regardless of the order of ID assignment
		Random r = new Random(0);
		StringDictionary dictionary = new StringDictionary();
		for (int t=0; t<10; t++) {
			StringMultiset ms = new StringMultiset();
			StringMultiset ms2 = new StringMultiset();
			for (int i=0; i<1000; i++) {
				ms.add(Integer.toString(r.nextInt(300)));
				ms2.add(Integer.toString(r.nextInt(500)));
			}
			Assert.assertEquals(ms.intersection(ms2), new IntMultiset(ms, dictionary).intersection(new IntMultiset(ms2, dictionary)));
		}
	}

}

package jp.naist.se.codehash.util;

import org.junit.Assert;
import org.junit.Test;


public class StringMultisetTest {

	@Test
	public void testMultiset() {
		StringMultiset ms = new StringMultiset(10);
		ms.add("");
		Assert.assertEquals(1, ms.size());
		ms.add("");
		Assert.assertEquals(2, ms.size());
		Assert.assertEquals(2, ms.get(""));
		ms.add("");
		ms.add("a");
		ms.add("aa");
		ms.add("aa");
		Assert.assertEquals(6, ms.size());
		Assert.assertEquals(3, ms.get(""));
		Assert.assertEquals(2, ms.get("aa"));
	}
	
	@Test
	public void testIntersection() {
		StringMultiset ms = new StringMultiset(100);
		for (int i=0; i<50; i++) {
			ms.add(Integer.toString(i));
		}
		StringMultiset ms2 = new StringMultiset(100);
		for (int i=0; i<30; i++) {
			ms2.add(Integer.toString(i));
		}
		Assert.assertEquals(30, ms.intersection(ms2));
		Assert.assertEquals(30, ms2.intersection(ms));
		for (int i=0; i<30; i++) {
			ms2.add(Integer.toString(i));
		}
		Assert.assertEquals(30, ms.intersection(ms2));
		Assert.assertEquals(30, ms2.intersection(ms));
		for (int i=0; i<50; i++) {
			ms.add(Integer.toString(i));
		}
		Assert.assertEquals(60, ms.intersection(ms2));
		Assert.assertEquals(60, ms2.intersection(ms));
	}
	
	@Test
	public void testSubtract() {
		// Construct a multiset
		StringMultiset minuend = new StringMultiset(100);
		minuend.add("0");
		minuend.add("0");
		minuend.add("1");
		minuend.add("1");
		minuend.add("2");
		minuend.add("2");
		minuend.add("4");
		// Construct another multiset
		StringMultiset subtrahend = new StringMultiset(100);
		subtrahend.add("0");
		subtrahend.add("0");
		subtrahend.add("0");
		subtrahend.add("1");
		subtrahend.add("1");
		subtrahend.add("2");
		subtrahend.add("3");
		
		StringMultiset diff = minuend.subtract(subtrahend);
		Assert.assertEquals(0, diff.get("0"));
		Assert.assertEquals(0, diff.get("1"));
		Assert.assertEquals(1, diff.get("2"));
		Assert.assertEquals(0, diff.get("3"));
		Assert.assertEquals(1, diff.get("4"));
	}
	
}

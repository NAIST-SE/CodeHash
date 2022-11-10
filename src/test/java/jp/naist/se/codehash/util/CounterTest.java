package jp.naist.se.codehash.util;

import org.junit.Assert;
import org.junit.Test;

public class CounterTest {

	@Test
	public void testConstructors() {
		Assert.assertEquals(0, new Counter().get());
		Assert.assertEquals(1, new Counter(1).get());
		Assert.assertEquals(2, new Counter(2).get());
	}

	@Test
	public void testIncrement() {
		Counter c = new Counter(0);
		c.increment();
		Assert.assertEquals(1, c.get());
		c.increment();
		Assert.assertEquals(2, c.get());
	}


	@Test
	public void testIncrementNumber() {
		Counter c = new Counter(0);
		c.increment(3);
		Assert.assertEquals(3, c.get());
		c.increment(5);
		Assert.assertEquals(8, c.get());
	}

}

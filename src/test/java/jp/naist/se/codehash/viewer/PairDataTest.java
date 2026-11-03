package jp.naist.se.codehash.viewer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import jp.naist.se.codehash.viewer.PairData.Cluster;


public class PairDataTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	/**
	 * Files 10-14 and pairs (10, 11), (11, 12), (13, 14), (10, 12) with different values.
	 * The last pair lacks "exact-jaccard".
	 */
	static final String JSON = "{ \"Similarity\": \"estimated-by-minhash\",\n"
			+ "\"Files\": [\n"
			+ " {\"group\": \"g\", \"index\": 10, \"path\": \"/a.c\", \"lang\": \"CPP\", \"byte-sha1\": \"x\", \"token-length\": 1},\n"
			+ " {\"index\": 11, \"path\": \"/b.c\"}, {\"index\": 12, \"path\": \"/c.c\"}, {\"index\": 13, \"path\": \"/d.c\"}, {\"index\": 14, \"path\": \"/e.c\"} ],\n"
			+ "\"Pairs\": [\n"
			+ " {\"index1\": 10, \"index2\": 11, \"exact-jaccard\": 0.5, \"jaccard\": 0.9},\n"
			+ " {\"index1\": 11, \"index2\": 12, \"exact-jaccard\": 0.4, \"jaccard\": 0.8},\n"
			+ " {\"index1\": 13, \"index2\": 14, \"exact-jaccard\": 0.9, \"jaccard\": 0.95},\n"
			+ " {\"index1\": 10, \"index2\": 12, \"jaccard\": 0.5} ] }";

	static PairData read(TemporaryFolder temp) throws Exception {
		File f = temp.newFile("result.json");
		Files.write(f.toPath(), JSON.getBytes(StandardCharsets.UTF_8));
		return PairData.read(f);
	}

	@Test
	public void testRead() throws Exception {
		PairData data = read(temp);
		Assert.assertEquals("estimated-by-minhash", data.getSimilarity());
		Assert.assertEquals(5, data.getFiles().size());
		Assert.assertEquals("/a.c", data.getFile(10).getPath());
		Assert.assertEquals("g", data.getFile(10).getGroup());
		Assert.assertEquals("CPP", data.getFile(10).getLanguage());
		Assert.assertEquals("x", data.getFile(10).getSha1());
		Assert.assertNull(data.getFile(11).getSha1());
		Assert.assertNull(data.getFile(0));

		Assert.assertEquals(4, data.getPairCount());
		Assert.assertEquals(List.of("exact-jaccard", "jaccard"), data.getMetrics());
		Assert.assertEquals(-1, data.getMetricIndex("unknown"));
		int exact = data.getMetricIndex("exact-jaccard");
		Assert.assertEquals(11, data.getFirst(1));
		Assert.assertEquals(12, data.getSecond(1));
		Assert.assertEquals(0.4, data.getValue(exact, 1), 0);
		Assert.assertTrue(Double.isNaN(data.getValue(exact, 3)));
	}

	@Test
	public void testSelect() throws Exception {
		PairData data = read(temp);
		int jaccard = data.getMetricIndex("jaccard");
		Assert.assertArrayEquals(new int[] {2, 0, 1}, data.select(jaccard, 0.8));
		Assert.assertArrayEquals(new int[] {2, 0, 1, 3}, data.select(jaccard, 0));
		Assert.assertArrayEquals(new int[0], data.select(jaccard, 0.99));
		// A missing value is not selected
		Assert.assertArrayEquals(new int[] {2, 0, 1}, data.select(data.getMetricIndex("exact-jaccard"), 0));
	}

	@Test
	public void testCluster() throws Exception {
		PairData data = read(temp);
		int jaccard = data.getMetricIndex("jaccard");

		// 10-11-12 are connected even though (10, 12) is below the threshold
		List<Cluster> clusters = data.cluster(jaccard, 0.8);
		Assert.assertEquals(2, clusters.size());
		Assert.assertArrayEquals(new int[] {10, 11, 12}, clusters.get(0).getFiles());
		Assert.assertArrayEquals(new int[] {0, 1}, clusters.get(0).getPairs());
		Assert.assertArrayEquals(new int[] {13, 14}, clusters.get(1).getFiles());
		Assert.assertArrayEquals(new int[] {2}, clusters.get(1).getPairs());

		clusters = data.cluster(jaccard, 0.85);
		Assert.assertEquals(2, clusters.size());
		Assert.assertArrayEquals(new int[] {10, 11}, clusters.get(0).getFiles());
		Assert.assertArrayEquals(new int[] {13, 14}, clusters.get(1).getFiles());

		Assert.assertEquals(0, data.cluster(jaccard, 0.99).size());
	}

}

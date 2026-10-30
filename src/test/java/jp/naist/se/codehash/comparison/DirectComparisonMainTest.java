package jp.naist.se.codehash.comparison;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import jp.naist.se.codehash.comparison.similarity.SimilarityMetrics;


/**
 * Tests for command line options of {@link DirectComparisonMain} using C files in src/test/resources/c-sample.
 * - arithmetic.c: an arithmetic progression computed by a loop.
 * - arithmetic_renamed.c: a copy of arithmetic.c with renamed identifiers and a different comment.
 * - geometric.c: a geometric progression computed by a similar loop.
 * - arithmetic_formula.c: an arithmetic progression computed by formulas (structurally different).
 */
public class DirectComparisonMainTest {

	private static final String ARITHMETIC = "arithmetic.c";
	private static final String ARITHMETIC_RENAMED = "arithmetic_renamed.c";
	private static final String GEOMETRIC = "geometric.c";
	private static final String ARITHMETIC_FORMULA = "arithmetic_formula.c";

	private static final String[] SAMPLE_FILES = { ARITHMETIC, ARITHMETIC_RENAMED, GEOMETRIC, ARITHMETIC_FORMULA };

	/**
	 * A pair of file names and similarity values (in the output order) reported by the tool.
	 */
	private static class Pair {
		private String file1;
		private String file2;
		private LinkedHashMap<String, Double> values = new LinkedHashMap<>();

		public boolean isPairOf(String f1, String f2) {
			return (file1.equals(f1) && file2.equals(f2)) || (file1.equals(f2) && file2.equals(f1));
		}

		public double get(String metric) {
			Assert.assertTrue("Missing metric " + metric, values.containsKey(metric));
			return values.get(metric);
		}
	}

	private static String[] createArgs(String... options) throws URISyntaxException {
		Path dir = Paths.get(DirectComparisonMainTest.class.getResource("/c-sample").toURI());
		ArrayList<String> args = new ArrayList<>(Arrays.asList(options));
		for (String f: SAMPLE_FILES) {
			args.add(dir.resolve(f).toString());
		}
		return args.toArray(new String[args.size()]);
	}

	/**
	 * Execute the tool with options and sample files.
	 * @return the standard output.
	 */
	private static String execute(String... options) throws URISyntaxException {
		String[] args = createArgs(options);
		PrintStream original = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
			DirectComparisonMain.main(args);
		} finally {
			System.setOut(original);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Execute the tool and parse the JSON output.
	 * @return pairs in the output.
	 */
	private static List<Pair> run(String... options) throws IOException, URISyntaxException {
		String output = execute(options);
		HashMap<Integer, String> files = new HashMap<>();
		ArrayList<Pair> pairs = new ArrayList<>();
		try (JsonParser parser = new JsonFactory().createParser(output)) {
			Assert.assertEquals(JsonToken.START_OBJECT, parser.nextToken());
			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				String section = parser.getCurrentName();
				Assert.assertEquals(JsonToken.START_ARRAY, parser.nextToken());
				while (parser.nextToken() == JsonToken.START_OBJECT) {
					LinkedHashMap<String, String> fields = new LinkedHashMap<>();
					while (parser.nextToken() == JsonToken.FIELD_NAME) {
						String name = parser.getCurrentName();
						parser.nextToken();
						fields.put(name, parser.getText());
					}
					if (section.equals("Files")) {
						String filename = Paths.get(fields.get("path")).getFileName().toString();
						files.put(Integer.parseInt(fields.get("index")), filename);
					} else if (section.equals("Pairs")) {
						Pair p = new Pair();
						p.file1 = files.get(Integer.parseInt(fields.remove("index1")));
						p.file2 = files.get(Integer.parseInt(fields.remove("index2")));
						for (String name: fields.keySet()) {
							p.values.put(name, Double.parseDouble(fields.get(name)));
						}
						pairs.add(p);
					}
				}
			}
		}
		Assert.assertEquals(SAMPLE_FILES.length, files.size());
		return pairs;
	}

	private static Pair find(List<Pair> pairs, String f1, String f2) {
		for (Pair p: pairs) {
			if (p.isPairOf(f1, f2)) return p;
		}
		return null;
	}

	@Test
	public void testDefaultMetrics() throws Exception {
		List<Pair> pairs = run();
		Assert.assertEquals(6, pairs.size());
		for (Pair p: pairs) {
			Assert.assertEquals(new ArrayList<>(SimilarityMetrics.getNames()), new ArrayList<>(p.values.keySet()));
		}

		// Renamed identifiers do not affect normalized metrics
		Pair renamed = find(pairs, ARITHMETIC, ARITHMETIC_RENAMED);
		Assert.assertEquals(1.0, renamed.get(SimilarityMetrics.JACCARD), 0);
		Assert.assertEquals(1.0, renamed.get(SimilarityMetrics.OVERLAP_COEFFICIENT), 0);
		Assert.assertEquals(1.0, renamed.get(SimilarityMetrics.OVERLAP_SIMILARITY), 0);
		Assert.assertTrue(renamed.get(SimilarityMetrics.EXACT_JACCARD) < 1.0);

		// A loop-based program is more similar to another loop-based program than a formula-based one
		Pair geometric = find(pairs, ARITHMETIC, GEOMETRIC);
		Pair formula = find(pairs, ARITHMETIC, ARITHMETIC_FORMULA);
		Assert.assertTrue(geometric.get(SimilarityMetrics.JACCARD) > formula.get(SimilarityMetrics.JACCARD));
		Assert.assertTrue(geometric.get(SimilarityMetrics.JACCARD) < 1.0);
	}

	@Test
	public void testSelectMetrics() throws Exception {
		// The output follows the registration order regardless of the order in the option
		List<Pair> pairs = run("-metrics:jaccard,exact-overlap-coefficient");
		Assert.assertEquals(6, pairs.size());
		for (Pair p: pairs) {
			Assert.assertEquals(Arrays.asList(SimilarityMetrics.EXACT_OVERLAP_COEFFICIENT, SimilarityMetrics.JACCARD), new ArrayList<>(p.values.keySet()));
		}

		pairs = run("-metrics:overlap-similarity");
		for (Pair p: pairs) {
			Assert.assertEquals(Arrays.asList(SimilarityMetrics.OVERLAP_SIMILARITY), new ArrayList<>(p.values.keySet()));
		}
	}

	@Test
	public void testUnknownMetric() throws Exception {
		Assert.assertEquals("", execute("-metrics:jaccard,unknown-metric"));
	}

	@Test
	public void testFilter() throws Exception {
		List<Pair> pairs = run("-filter:jaccard>=0.99");
		Assert.assertEquals(1, pairs.size());
		Assert.assertTrue(pairs.get(0).isPairOf(ARITHMETIC, ARITHMETIC_RENAMED));

		// -thnj is equivalent to a filter on jaccard
		pairs = run("-thnj:0.99");
		Assert.assertEquals(1, pairs.size());
		Assert.assertTrue(pairs.get(0).isPairOf(ARITHMETIC, ARITHMETIC_RENAMED));

		// No pairs satisfy a contradiction
		Assert.assertEquals(0, run("-filter:jaccard>=0.99 and exact-jaccard>=1").size());
	}

	@Test
	public void testFilterExpression() throws Exception {
		List<Pair> all = run();
		double geometricJaccard = find(all, ARITHMETIC, GEOMETRIC).get(SimilarityMetrics.JACCARD);

		// Pairs except for the identical (after normalization) pair
		List<Pair> pairs = run("-filter:jaccard<1 and (exact-jaccard>=0 or jaccard>1)");
		Assert.assertEquals(5, pairs.size());
		Assert.assertNull(find(pairs, ARITHMETIC, ARITHMETIC_RENAMED));

		// A comparison between metrics
		pairs = run("-filter:exact-jaccard<jaccard");
		Assert.assertNotNull(find(pairs, ARITHMETIC, ARITHMETIC_RENAMED));

		// A threshold selecting pairs at least as similar as arithmetic.c and geometric.c
		pairs = run("-filter:jaccard>=" + geometricJaccard);
		Assert.assertNotNull(find(pairs, ARITHMETIC, ARITHMETIC_RENAMED));
		Assert.assertNotNull(find(pairs, ARITHMETIC, GEOMETRIC));
		Assert.assertNull(find(pairs, ARITHMETIC, ARITHMETIC_FORMULA));
	}

	@Test
	public void testMultipleFilters() throws Exception {
		List<Pair> all = run();
		double geometricJaccard = find(all, ARITHMETIC, GEOMETRIC).get(SimilarityMetrics.JACCARD);

		// A pair must satisfy all filters
		List<Pair> pairs = run("-filter:jaccard>=" + geometricJaccard, "-filter:jaccard<1");
		Assert.assertNull(find(pairs, ARITHMETIC, ARITHMETIC_RENAMED));
		Assert.assertNotNull(find(pairs, ARITHMETIC, GEOMETRIC));
		for (Pair p: pairs) {
			Assert.assertTrue(p.get(SimilarityMetrics.JACCARD) >= geometricJaccard);
			Assert.assertTrue(p.get(SimilarityMetrics.JACCARD) < 1);
		}
	}

	@Test
	public void testFilterWithUnselectedMetric() throws Exception {
		// A filter can use a metric that is not written to the output
		List<Pair> pairs = run("-metrics:exact-jaccard", "-filter:jaccard==1");
		Assert.assertEquals(1, pairs.size());
		Pair p = pairs.get(0);
		Assert.assertTrue(p.isPairOf(ARITHMETIC, ARITHMETIC_RENAMED));
		Assert.assertEquals(Arrays.asList(SimilarityMetrics.EXACT_JACCARD), new ArrayList<>(p.values.keySet()));
	}

	@Test
	public void testInvalidFilter() throws Exception {
		Assert.assertEquals("", execute("-filter:jaccard>="));
		Assert.assertEquals("", execute("-filter:unknown-metric>=0.5"));
	}

}

package jp.naist.se.codehash.comparison;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;


public class SimilarityMatrixMainTest {

	private static String convert(String metric, String json) throws Exception {
		SimilarityMatrixMain main = new SimilarityMatrixMain(metric);
		main.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		StringWriter buffer = new StringWriter();
		try (PrintWriter out = new PrintWriter(buffer)) {
			main.write(out);
		}
		return buffer.toString().replace("\r\n", "\n");
	}

	@Test
	public void testMatrix() throws Exception {
		String json = "{ \"Files\": [" +
				"{ \"group\": \"<default>\", \"index\": 0, \"path\": \"a.c\", \"lang\": \"CPP\" }," +
				"{ \"group\": \"<default>\", \"index\": 1, \"path\": \"b,c.c\", \"lang\": \"CPP\" }," +
				"{ \"group\": \"<default>\", \"index\": 2, \"path\": \"d.c\", \"lang\": \"CPP\" }" +
				"], \"Pairs\": [" +
				"{ \"index1\": 0, \"index2\": 1, \"jaccard\": 0.5, \"exact-jaccard\": 0.25 }," +
				"{ \"index1\": 2, \"index2\": 1, \"jaccard\": 0.75, \"exact-jaccard\": 0.125 }" +
				"] }";
		// The pair (0, 2) is missing; (2, 1) is placed above the diagonal
		Assert.assertEquals(
				"index,path,0,1,2\n" +
				"0,a.c,,0.5,\n" +
				"1,\"b,c.c\",,,0.75\n" +
				"2,d.c,,,\n",
				convert("jaccard", json));
		Assert.assertEquals(
				"index,path,0,1,2\n" +
				"0,a.c,,0.25,\n" +
				"1,\"b,c.c\",,,0.125\n" +
				"2,d.c,,,\n",
				convert("exact-jaccard", json));
	}

	@Test
	public void testDirectComparisonOutput() throws Exception {
		Path dir = Paths.get(SimilarityMatrixMainTest.class.getResource("/c-sample").toURI());
		String[] args = { "-metrics:jaccard", dir.resolve("arithmetic.c").toString(), dir.resolve("arithmetic_renamed.c").toString(), dir.resolve("geometric.c").toString() };
		PrintStream original = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
			DirectComparisonMain.main(args);
		} finally {
			System.setOut(original);
		}

		// Indices may not start from 0, since FileEntity numbers files across tests in the same JVM
		String[] lines = convert("jaccard", buffer.toString(StandardCharsets.UTF_8)).split("\n");
		Assert.assertEquals(4, lines.length);
		String[] header = lines[0].split(",", -1);
		Assert.assertEquals(5, header.length);
		String[] row0 = lines[1].split(",", -1);
		Assert.assertEquals(5, row0.length);
		Assert.assertEquals(header[2], row0[0]);
		Assert.assertTrue(row0[1].endsWith("arithmetic.c"));
		Assert.assertEquals("", row0[2]);
		Assert.assertEquals(1.0, Double.parseDouble(row0[3]), 0);
		Assert.assertTrue(Double.parseDouble(row0[4]) < 1.0);
		String[] row2 = lines[3].split(",", -1);
		Assert.assertEquals("", row2[2]);
		Assert.assertEquals("", row2[3]);
		Assert.assertEquals("", row2[4]);
	}

	@Test
	public void testUnknownMetric() throws Exception {
		PrintStream original = System.err;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
			SimilarityMatrixMain.main(new String[] { "unknown-metric", "-" });
		} finally {
			System.setErr(original);
		}
		Assert.assertTrue(buffer.toString(StandardCharsets.UTF_8).startsWith("Unknown metric"));
	}

}

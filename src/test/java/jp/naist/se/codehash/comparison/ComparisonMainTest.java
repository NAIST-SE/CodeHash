package jp.naist.se.codehash.comparison;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.FileCodeHash;


public class ComparisonMainTest {

	/**
	 * Lines of a minhash list in the FileCodeHash format.
	 * The estimated similarity of A-B and B-C is 0.75 (2 of 16 bits are different),
	 * and that of A-C is 0.5 (4 of 16 bits are different).
	 */
	private static final String[] LINES = {
		FileCodeHash.HEADER,
		"A.c\tsha1-a\tCPP\tcodehash-a\tffff\tffff\t100\t50\t50",
		"B.c\tsha1-b\tCPP\tcodehash-b\tfffc\tfffc\t100\t50\t50",
		// A header line in the middle of files concatenated by cat
		FileCodeHash.HEADER,
		"C.c\tsha1-c\tCPP\tcodehash-c\tfff0\tfff0\t100\t50\t50",
		// Lines without minhash vectors (GitCodeHash in codehash and sha1minhash modes) are skipped
		"D.c\tblob-d\tCPP\tcodehash-d\t\t\t100\t50\t0",
		"E.c\tblob-e\tCPP\tcodehash-e\tffff\t\t100\t50\t0",
		// Lines that are not recognized as data are skipped
		"F.c\tsha1-f\tCPP\tcodehash-f\tffff\tffff\t100\t50\tabc",
		"G.c\tsha1-g\tCPP\tcodehash-g\tfffz\tffff\t100\t50\t50",
		"",
	};

	/**
	 * Execute the tool with options and a minhash list.
	 * @return the number of reported pairs, or -1 if nothing is written.
	 */
	private static int run(String... options) throws Exception {
		Path list = Files.createTempFile("minhash", ".txt");
		try {
			Files.write(list, Arrays.asList(LINES), StandardCharsets.UTF_8);
			String[] args = Arrays.copyOf(options, options.length + 1);
			args[options.length] = list.toString();

			PrintStream originalOut = System.out;
			PrintStream originalErr = System.err;
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try {
				System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
				System.setErr(new PrintStream(OutputStream.nullOutputStream()));
				ComparisonMain.main(args);
			} finally {
				System.setOut(originalOut);
				System.setErr(originalErr);
			}
			String output = buffer.toString(StandardCharsets.UTF_8);
			if (output.isEmpty()) return -1;
			// Exclude the header line
			return output.split("\r?\n").length - 1;
		} finally {
			Files.delete(list);
		}
	}

	@Test
	public void testDefaultThreshold() throws Exception {
		Assert.assertEquals(2, run());
	}

	@Test
	public void testThreshold() throws Exception {
		Assert.assertEquals(3, run("-th:0.5"));
		Assert.assertEquals(2, run("-th:0.75"));
		Assert.assertEquals(0, run("-th:0.8"));
	}

	@Test
	public void testInvalidThreshold() throws Exception {
		Assert.assertEquals(-1, run("-th:abc"));
		Assert.assertEquals(-1, run("-th:1.5"));
	}

	/**
	 * The first group includes A and B.  The second group includes C and a copy of A.
	 */
	private static final String[][] GROUPS = {
		{ FileCodeHash.HEADER,
		  "A.c\tsha1-a\tCPP\tcodehash-a\tffff\tffff\t100\t50\t50",
		  "B.c\tsha1-b\tCPP\tcodehash-b\tfffc\tfffc\t100\t50\t50" },
		{ FileCodeHash.HEADER,
		  "C.c\tsha1-c\tCPP\tcodehash-c\tfff0\tfff0\t100\t50\t50",
		  "A2.c\tsha1-a\tCPP\tcodehash-a\tffff\tffff\t100\t50\t50" },
	};

	/**
	 * Execute the tool with options and minhash lists in {@link #GROUPS}.
	 * @return the output lines excluding the header line.
	 */
	private static List<String> runGroups(String... options) throws Exception {
		ArrayList<Path> lists = new ArrayList<>();
		try {
			ArrayList<String> args = new ArrayList<>(Arrays.asList(options));
			for (String[] lines: GROUPS) {
				Path list = Files.createTempFile("minhash", ".txt");
				lists.add(list);
				Files.write(list, Arrays.asList(lines), StandardCharsets.UTF_8);
				args.add(list.toString());
			}

			PrintStream originalOut = System.out;
			PrintStream originalErr = System.err;
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try {
				System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
				System.setErr(new PrintStream(OutputStream.nullOutputStream()));
				ComparisonMain.main(args.toArray(new String[args.size()]));
			} finally {
				System.setOut(originalOut);
				System.setErr(originalErr);
			}
			List<String> output = new ArrayList<>(Arrays.asList(buffer.toString(StandardCharsets.UTF_8).split("\r?\n")));
			output.remove(0);
			return output;
		} finally {
			for (Path list: lists) {
				Files.delete(list);
			}
		}
	}

	/**
	 * @return the file names in the output line comparing f1 and f2.  null if the pair is not found.
	 */
	private static String find(List<String> lines, String f1, String f2) {
		for (String line: lines) {
			String[] columns = line.split("\t");
			if (columns[6].equals(f1) && columns[7].equals(f2)) return line;
		}
		return null;
	}

	@Test
	public void testMultipleFiles() throws Exception {
		// Files having the same codehash are merged across input files
		List<String> lines = runGroups("-th:0.5");
		Assert.assertEquals(3, lines.size());
		Assert.assertTrue(lines.stream().anyMatch(line -> line.contains("A.c,A2.c")));
	}

	@Test
	public void testCrossGroup() throws Exception {
		List<String> lines = runGroups("-th:0.5", "-compare:crossgroup");
		// Pairs within a group (A-B and C-A2) are excluded
		Assert.assertEquals(4, lines.size());
		Assert.assertNotNull(find(lines, "A.c", "C.c"));
		Assert.assertNotNull(find(lines, "B.c", "C.c"));
		Assert.assertNotNull(find(lines, "B.c", "A2.c"));
		// The same codehash in different groups is reported as a pair
		String same = find(lines, "A.c", "A2.c");
		Assert.assertNotNull(same);
		Assert.assertTrue(same.startsWith("codehash-a\tcodehash-a\t50\t50\t1.0\t1.0\t"));

		Assert.assertEquals(3, runGroups("-th:0.75", "-compare:crossgroup").size());
	}

}

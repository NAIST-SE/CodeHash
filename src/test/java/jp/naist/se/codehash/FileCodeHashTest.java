package jp.naist.se.codehash;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.hash.SourceCodeHash;
import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;
import jp.naist.se.codehash.token.FileType;


public class FileCodeHashTest {

	private static Path sampleFile() throws Exception {
		return Paths.get(FileCodeHashTest.class.getResource("/c-sample/arithmetic.c").toURI());
	}

	/**
	 * Execute FileCodeHash with options for the sample file.
	 * @return the output lines.
	 */
	private static String[] run(String... options) throws Exception {
		String[] args = new String[options.length + 1];
		System.arraycopy(options, 0, args, 0, options.length);
		args[options.length] = sampleFile().toString();

		PrintStream originalOut = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
			FileCodeHash.main(args);
		} finally {
			System.setOut(originalOut);
		}
		String output = buffer.toString(StandardCharsets.UTF_8);
		return output.isEmpty() ? new String[0] : output.split("\r?\n");
	}

	/**
	 * @return the minhash columns and the number of N-grams computed by SourceCodeHash.
	 */
	private static String[] compute(MinHashType t, int N) throws Exception {
		byte[] content = Files.readAllBytes(sampleFile());
		SourceCodeHash h = new SourceCodeHash(FileType.CPP, new ByteArrayInputStream(content), content.length, t, N);
		return new String[] { h.getMinHash(), h.getNormalizedMinHash(), Integer.toString(h.getNgramCount()) };
	}

	private static String[] getMinHashColumns(String line) {
		String[] columns = line.split("\t");
		return new String[] { columns[4], columns[5], columns[8] };
	}

	@Test
	public void testHeader() throws Exception {
		String[] lines = run();
		Assert.assertEquals(2, lines.length);
		Assert.assertEquals(FileCodeHash.HEADER, lines[0]);
		Assert.assertEquals(FileCodeHash.HEADER.split("\t").length, lines[1].split("\t").length);
		Assert.assertTrue(lines[1].startsWith(sampleFile().toAbsolutePath().toString() + "\t"));
		Assert.assertArrayEquals(compute(MinHashType.Murmur3, 3), getMinHashColumns(lines[1]));
	}

	@Test
	public void testOptions() throws Exception {
		Assert.assertArrayEquals(compute(MinHashType.Murmur3, 1), getMinHashColumns(run("-n:1")[1]));
		Assert.assertArrayEquals(compute(MinHashType.Murmur3IgnoreDuplication, 3), getMinHashColumns(run("-ignoreduplication")[1]));
		Assert.assertArrayEquals(compute(MinHashType.Murmur3IgnoreDuplication, 1), getMinHashColumns(run("-n:1", "-ignoreduplication")[1]));
		// The options change the result
		Assert.assertNotEquals(run()[1], run("-n:1")[1]);
		Assert.assertNotEquals(run()[1], run("-ignoreduplication")[1]);
	}

	@Test
	public void testInvalidOption() throws Exception {
		PrintStream originalErr = System.err;
		try {
			System.setErr(new PrintStream(new ByteArrayOutputStream()));
			Assert.assertEquals(0, run("-n:0").length);
		} finally {
			System.setErr(originalErr);
		}
	}

}

package jp.naist.se.codehash;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import jp.naist.se.codehash.comparison.ComparisonMain;
import jp.naist.se.codehash.comparison.FileTokenizerMain;


public class CodeHashMainTest {

	/**
	 * The standard output and error of an execution.
	 */
	private static class Result {
		private String out;
		private String err;
	}

	private static Result execute(Consumer<String[]> main, String... args) {
		PrintStream originalOut = System.out;
		PrintStream originalErr = System.err;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
			main.accept(args);
		} finally {
			System.setOut(originalOut);
			System.setErr(originalErr);
		}
		Result r = new Result();
		r.out = out.toString(StandardCharsets.UTF_8);
		r.err = err.toString(StandardCharsets.UTF_8);
		return r;
	}

	private static String sampleFile() throws Exception {
		Path dir = Paths.get(CodeHashMainTest.class.getResource("/c-sample").toURI());
		return dir.resolve("arithmetic.c").toString();
	}

	@Test
	public void testCommand() throws Exception {
		String expected = execute(FileTokenizerMain::main, sampleFile()).out;
		Assert.assertFalse(expected.isEmpty());
		Assert.assertEquals(expected, execute(CodeHashMain::main, "tokenize", sampleFile()).out);
	}

	@Test
	public void testClassName() throws Exception {
		String expected = execute(FileTokenizerMain::main, sampleFile()).out;
		Assert.assertEquals(expected, execute(CodeHashMain::main, "FileTokenizerMain", sampleFile()).out);
	}

	@Test
	public void testUsage() {
		Result r = execute(CodeHashMain::main, "help");
		Assert.assertTrue(r.out.startsWith("Usage:"));
		for (String command: new String[] { "file-minhash", "minhash-compare", "minhash-compare-json", "file-compare", "json-to-matrix", "git-file-list", "git-minhash", "tokenize" }) {
			Assert.assertTrue(command, r.out.contains("  " + command + " "));
		}

		r = execute(CodeHashMain::main);
		Assert.assertEquals("", r.out);
		Assert.assertTrue(r.err.startsWith("Usage:"));
	}

	@Test
	public void testCommandWithOptions() throws Exception {
		Path list = Files.createTempFile("minhash", ".txt");
		try {
			Files.write(list, execute(FileCodeHash::main, sampleFile(), sampleFile()).out.getBytes(StandardCharsets.UTF_8));
			String expected = execute(ComparisonMain::main, ComparisonMain.JSON_OPTION, list.toString()).out;
			Assert.assertTrue(expected.startsWith("{"));
			Assert.assertEquals(expected, execute(CodeHashMain::main, "minhash-compare-json", list.toString()).out);
			// A class name refers to the command without additional options
			Assert.assertEquals(execute(ComparisonMain::main, list.toString()).out, execute(CodeHashMain::main, "ComparisonMain", list.toString()).out);
		} finally {
			Files.delete(list);
		}
	}

	@Test
	public void testUnknownCommand() {
		Result r = execute(CodeHashMain::main, "unknown-command");
		Assert.assertEquals("", r.out);
		Assert.assertTrue(r.err.startsWith("Unknown command: unknown-command"));
	}

}

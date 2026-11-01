package jp.naist.se.codehash;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;


public class FileCodeHashTest {

	@Test
	public void testHeader() throws Exception {
		Path sample = Paths.get(FileCodeHashTest.class.getResource("/c-sample/arithmetic.c").toURI());
		PrintStream originalOut = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
			FileCodeHash.main(new String[] { sample.toString() });
		} finally {
			System.setOut(originalOut);
		}
		String[] lines = buffer.toString(StandardCharsets.UTF_8).split("\r?\n");
		Assert.assertEquals(2, lines.length);
		Assert.assertEquals(FileCodeHash.HEADER, lines[0]);
		Assert.assertEquals(FileCodeHash.HEADER.split("\t").length, lines[1].split("\t").length);
		Assert.assertTrue(lines[1].startsWith(sample.toAbsolutePath().toString() + "\t"));
	}

}

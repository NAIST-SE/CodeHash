package jp.naist.se.codehash.token;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class FileTypeTest {

	@Test
	public void testFileType() {
		Assert.assertEquals(FileType.JAVA, FileType.getFileType("JAVA"));
		Assert.assertEquals(FileType.CPP, FileType.getFileType("C"));

		Assert.assertEquals(FileType.JAVA, FileType.getFileTypeFromName("MyClass.java"));
		Assert.assertEquals(FileType.CPP, FileType.getFileTypeFromName("MyClass.cpp"));

		Assert.assertEquals(FileType.PLAINTEXT, FileType.getFileType("PLAINTEXT"));
		Assert.assertEquals(FileType.PLAINTEXT, FileType.getFileTypeFromName("README.txt"));
	}

	@Test
	public void testPlainText() {
		String text = "x = a_1+b;  // ok\n\tf(x)";
		TokenReader r = FileType.createReader(FileType.PLAINTEXT, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
		ArrayList<String> tokens = new ArrayList<>();
		while (r.next()) {
			tokens.add(r.getText());
			Assert.assertEquals(r.getText(), r.getNormalizedText());
		}
		Assert.assertEquals(Arrays.asList("x", "=", "a_1", "+", "b", ";", "/", "/", "ok", "f", "(", "x", ")"), tokens);
	}
}

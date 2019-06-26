package jp.naist.se.codehash;

import org.junit.Assert;
import org.junit.Test;

public class FileTypeTest {

	@Test
	public void testFileType() {
		Assert.assertEquals(FileType.JAVA, FileType.getFileType("JAVA"));
		Assert.assertEquals(FileType.CPP, FileType.getFileType("C"));

		Assert.assertEquals(FileType.JAVA, FileType.getFileTypeFromName("MyClass.java"));
		Assert.assertEquals(FileType.CPP, FileType.getFileTypeFromName("MyClass.cpp"));
	}
}

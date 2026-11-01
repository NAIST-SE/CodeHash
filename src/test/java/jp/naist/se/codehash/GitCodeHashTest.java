package jp.naist.se.codehash;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import jp.naist.se.codehash.hash.FileHash;
import jp.naist.se.codehash.hash.SourceCodeHash;
import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;
import jp.naist.se.codehash.token.FileType;


public class GitCodeHashTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private static final String FILENAME = "arithmetic.c";

	/**
	 * Create a repository including a sample file.
	 * @return the blob ID of the file.
	 */
	private ObjectId createRepository(File dir) throws Exception {
		Path sample = Paths.get(GitCodeHashTest.class.getResource("/c-sample/" + FILENAME).toURI());
		Files.copy(sample, dir.toPath().resolve(FILENAME));
		try (Git git = Git.init().setDirectory(dir).call()) {
			// Store the file as is regardless of the user's core.autocrlf, so that the blob content equals the file content
			StoredConfig config = git.getRepository().getConfig();
			config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, false);
			config.save();
			git.add().addFilepattern(FILENAME).call();
			git.commit().setMessage("Add a file").setAuthor("test", "test@example.com").setCommitter("test", "test@example.com").setSign(false).call();
			return git.getRepository().resolve("HEAD:" + FILENAME);
		}
	}

	/**
	 * @return the expected line computed by FileCodeHash except for the first two columns.
	 */
	private static String[] computeFileCodeHash(File f, MinHashType hashType) throws Exception {
		byte[] content = Files.readAllBytes(f.toPath());
		SourceCodeHash h = new SourceCodeHash(FileType.CPP, new ByteArrayInputStream(content), content.length, hashType);
		return FileCodeHash.toLine(f.getAbsolutePath(), FileHash.compute(content), FileType.CPP, h, content.length).split("\t", -1);
	}

	private static String[] run(File dir, String filelist, MinHashType hashType) {
		GitCodeHash analyzer = new GitCodeHash();
		StringWriter buffer = new StringWriter();
		try (PrintWriter w = new PrintWriter(buffer)) {
			if (filelist == null) {
				analyzer.parseGitRepository(dir, w, hashType);
			} else {
				analyzer.parseGitRepository(dir, new LineNumberReader(new StringReader(filelist)), w, hashType);
			}
		}
		String[] lines = buffer.toString().split("\r?\n");
		Assert.assertEquals(1, lines.length);
		return lines[0].split("\t", -1);
	}

	@Test
	public void testSameFormatAsFileCodeHash() throws Exception {
		File dir = temp.newFolder("repo");
		ObjectId blob = createRepository(dir);
		String[] expected = computeFileCodeHash(new File(dir, FILENAME), MinHashType.Murmur3);

		String[] withoutList = run(dir, null, MinHashType.Murmur3);
		String[] twoColumns = run(dir, blob.getName() + "\t" + FILENAME + "\n", MinHashType.Murmur3);
		String[] threeColumns = run(dir, blob.getName() + "\t\tCPP\n", MinHashType.Murmur3);

		Assert.assertEquals(9, withoutList.length);
		Assert.assertEquals(blob.getName() + ":" + FILENAME, withoutList[0]);
		// The SHA-1 hash of the content, not the blob ID
		Assert.assertNotEquals(blob.getName(), withoutList[1]);
		Assert.assertEquals(Arrays.asList(expected).subList(1, 9), Arrays.asList(withoutList).subList(1, 9));
		Assert.assertArrayEquals(withoutList, twoColumns);

		// Only the blob ID is used if a file name is unavailable
		Assert.assertEquals(blob.getName(), threeColumns[0]);
		Assert.assertEquals(Arrays.asList(withoutList).subList(1, 9), Arrays.asList(threeColumns).subList(1, 9));
	}

	@Test
	public void testCodeHashOnly() throws Exception {
		File dir = temp.newFolder("repo");
		createRepository(dir);
		String[] expected = computeFileCodeHash(new File(dir, FILENAME), MinHashType.Murmur3);

		String[] actual = run(dir, null, MinHashType.None);
		Assert.assertEquals(9, actual.length);
		Assert.assertEquals(expected[3], actual[3]);
		Assert.assertEquals("", actual[4]);
		Assert.assertEquals("", actual[5]);
		Assert.assertEquals(expected[6], actual[6]);
		Assert.assertEquals(expected[7], actual[7]);
		Assert.assertEquals("0", actual[8]);
	}

}

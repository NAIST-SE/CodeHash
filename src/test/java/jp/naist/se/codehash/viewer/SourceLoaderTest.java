package jp.naist.se.codehash.viewer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import jp.naist.se.codehash.hash.FileHash;
import jp.naist.se.codehash.viewer.SourceLoader.Source;
import jp.naist.se.codehash.viewer.SourceLoader.Status;


public class SourceLoaderTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private static final byte[] CONTENT = "int main() { return 0; }\n".getBytes(StandardCharsets.UTF_8);

	private PairData readFiles(String... pathAndSha1) throws Exception {
		StringBuilder json = new StringBuilder("{\"Files\": [");
		for (int i=0; i<pathAndSha1.length; i+=2) {
			if (i > 0) json.append(",");
			json.append("{\"index\": " + i/2 + ", \"path\": \"" + pathAndSha1[i].replace("\\", "\\\\") + "\", \"byte-sha1\": \"" + pathAndSha1[i+1] + "\"}");
		}
		json.append("], \"Pairs\": []}");
		File f = temp.newFile();
		Files.write(f.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
		return PairData.read(f);
	}

	@Test
	public void testLocalFile() throws Exception {
		File source = temp.newFile("a.c");
		Files.write(source.toPath(), CONTENT);
		String sha1 = FileHash.compute(CONTENT);
		PairData data = readFiles(source.getAbsolutePath(), sha1, source.getAbsolutePath(), "0000", new File(temp.getRoot(), "missing.c").getAbsolutePath(), sha1);
		SourceLoader loader = new SourceLoader();

		Source s = loader.load(data.getFile(0));
		Assert.assertEquals(Status.OK, s.getStatus());
		Assert.assertEquals(new String(CONTENT, StandardCharsets.UTF_8), s.getText());

		// The file has been modified after comparison
		s = loader.load(data.getFile(1));
		Assert.assertEquals(Status.MODIFIED, s.getStatus());
		Assert.assertNotNull(s.getText());

		s = loader.load(data.getFile(2));
		Assert.assertEquals(Status.UNAVAILABLE, s.getStatus());
		Assert.assertNull(s.getText());
	}

	@Test
	public void testGitBlob() throws Exception {
		File dir = temp.newFolder("repo");
		ObjectId id;
		try (Git git = Git.init().setDirectory(dir).call(); ObjectInserter inserter = git.getRepository().newObjectInserter()) {
			id = inserter.insert(Constants.OBJ_BLOB, CONTENT);
			inserter.flush();
		}
		String sha1 = FileHash.compute(CONTENT);
		PairData data = readFiles(id.getName() + ":src/a.c", sha1, id.getName(), sha1, "0123456789012345678901234567890123456789:b.c", sha1);
		SourceLoader loader = new SourceLoader();

		// No repositories are registered
		Assert.assertEquals(Status.UNAVAILABLE, loader.load(data.getFile(0)).getStatus());

		loader.addRepository(dir.getAbsolutePath());
		loader.addRepository(dir.getAbsolutePath());
		Assert.assertEquals(1, loader.getRepositoryPaths().size());
		Source s = loader.load(data.getFile(0));
		Assert.assertEquals(Status.OK, s.getStatus());
		Assert.assertEquals(new String(CONTENT, StandardCharsets.UTF_8), s.getText());
		Assert.assertEquals(Status.OK, loader.load(data.getFile(1)).getStatus());
		Assert.assertEquals(Status.UNAVAILABLE, loader.load(data.getFile(2)).getStatus());
	}

	@Test(expected = java.io.IOException.class)
	public void testNotRepository() throws Exception {
		new SourceLoader().addRepository(temp.newFolder("empty").getAbsolutePath());
	}

}

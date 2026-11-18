package jp.naist.se.codehash.viewer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import jp.naist.se.codehash.hash.FileHash;
import jp.naist.se.codehash.util.GitRepositoryUtil;
import jp.naist.se.codehash.viewer.PairData.FileInfo;

/**
 * Loads the content of a file listed in a JSON file.
 * A path is regarded as a local file if it exists.
 * Otherwise, a path in the form of "[blob ID]:[path]" (written by GitCodeHash) is searched in the registered git repositories.
 */
public class SourceLoader {

	public enum Status {
		/** The content is loaded. */
		OK,
		/** The content is loaded, but it is different from the compared one (byte-sha1). */
		MODIFIED,
		/** The content is not available. */
		UNAVAILABLE
	}

	/**
	 * The content of a file.
	 */
	public static class Source {

		private Status status;
		private String message;
		private String text;

		private Source(Status status, String message, String text) {
			this.status = status;
			this.message = message;
			this.text = text;
		}

		public Status getStatus() {
			return status;
		}

		/**
		 * @return a message for a user.  null if the status is OK.
		 */
		public String getMessage() {
			return message;
		}

		/**
		 * @return the content decoded as UTF-8.  null if it is unavailable.
		 */
		public String getText() {
			return text;
		}
	}

	private static final Pattern BLOB_PATH = Pattern.compile("([0-9a-f]{40})(:.*)?", Pattern.DOTALL);

	private ArrayList<String> repositoryPaths = new ArrayList<>();
	private ArrayList<Repository> repositories = new ArrayList<>();

	/**
	 * Register a git repository to search blobs.
	 * @param path specifies a working directory or a .git directory.
	 * @throws IOException if the path is not a git repository.
	 */
	public synchronized void addRepository(String path) throws IOException {
		String canonical = new File(path).getCanonicalPath();
		if (repositoryPaths.contains(canonical)) return;
		repositories.add(GitRepositoryUtil.openRepository(new File(canonical)));
		repositoryPaths.add(canonical);
	}

	/**
	 * @return the paths of registered repositories.
	 */
	public synchronized List<String> getRepositoryPaths() {
		return new ArrayList<>(repositoryPaths);
	}

	private synchronized List<Repository> getRepositories() {
		return new ArrayList<>(repositories);
	}

	/**
	 * @param f specifies a file.
	 * @return the content of the file.
	 */
	public Source load(FileInfo f) {
		String path = f.getPath();
		if (path == null) {
			return new Source(Status.UNAVAILABLE, "The path is not available", null);
		}
		try {
			Path p = Paths.get(path);
			if (Files.isRegularFile(p)) {
				return toSource(f, Files.readAllBytes(p));
			}
		} catch (InvalidPathException | IOException e) {
			// Try a git repository
		}

		Matcher m = BLOB_PATH.matcher(path);
		if (!m.matches()) {
			return new Source(Status.UNAVAILABLE, "The file is not found", null);
		}
		ObjectId id = ObjectId.fromString(m.group(1));
		for (Repository r: getRepositories()) {
			try {
				return toSource(f, r.open(id, Constants.OBJ_BLOB).getCachedBytes(Integer.MAX_VALUE));
			} catch (MissingObjectException e) {
				// Try the next repository
			} catch (IOException e) {
				return new Source(Status.UNAVAILABLE, "Failed to read the blob: " + e.getMessage(), null);
			}
		}
		return new Source(Status.UNAVAILABLE, "The blob is not found in the git repositories", null);
	}

	private static Source toSource(FileInfo f, byte[] content) {
		String text = new String(content, StandardCharsets.UTF_8);
		if (f.getSha1() != null && !f.getSha1().equals(FileHash.compute(content))) {
			return new Source(Status.MODIFIED, "The content is different from the compared one", text);
		}
		return new Source(Status.OK, null, text);
	}

}

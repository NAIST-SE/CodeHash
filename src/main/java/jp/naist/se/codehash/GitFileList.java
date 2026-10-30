package jp.naist.se.codehash;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import jp.naist.se.codehash.util.GitRepositoryUtil;


/**
 * This is a main class to list all non-binary blobs in a specified git repository.
 * Command line parameter: A git repo directory.  The current directory is used by default
 */
public class GitFileList {

	public static final String DEFAULT_TARGET = "HEAD";

	public static void main(String[] args) {
		// Use the current directory by default
		if (args.length == 0) {
			args = new String[] {"."};
		}

		File f = new File(args[0]);
		try (Repository repo = GitRepositoryUtil.openRepository(f)) {
			boolean found = visitBlobs(repo, DEFAULT_TARGET, (obj, path) -> {
				ObjectLoader loader = repo.getObjectDatabase().open(obj);
				try (InputStream stream = loader.openStream()) {
					boolean result = RawText.isBinary(stream);
					if (!result) {
						System.out.println(obj.getName() + "\t" + path);
					}
				}
			});
			if (!found) {
				System.err.println("[Error] GitFileList: Failed to find [" + DEFAULT_TARGET + "] in the repository.");
			}
		} catch (IOException e) {
			System.err.print("[Error] GitFileList: Failed to open a git repo " + f.getAbsolutePath() + ". " + e.getMessage());
		}
	}

	/**
	 * A callback to receive blobs found by visitBlobs.
	 */
	public interface BlobVisitor {
		public void visit(AnyObjectId blobId, String path) throws IOException;
	}

	/**
	 * Visit all blobs reachable from a specified revision.
	 * Each blob is visited only once with one of its file paths.
	 * @param repo is a git repository.
	 * @param target specifies a revision, e.g. HEAD.
	 * @param visitor receives blobs.
	 * @return false if the target is not found in the repository.
	 */
	public static boolean visitBlobs(Repository repo, String target, BlobVisitor visitor) throws IOException {
		try (ObjectWalk rev = new ObjectWalk(repo)) {
			AnyObjectId commitId = repo.resolve(target);
			if (commitId == null) {
				return false;
			}

			RevCommit start = repo.parseCommit(commitId);
			rev.markStart(start);

			SkipSameEntryFilter filter = new SkipSameEntryFilter();
			for (RevCommit commit = rev.next(); commit != null; commit = rev.next()) {
				RevTree tree = commit.getTree();
				try (TreeWalk walk = new TreeWalk(repo)) {
					walk.addTree(tree);
					walk.setRecursive(true);
					walk.setFilter(filter);
					while (walk.next()) {
						AnyObjectId obj = walk.getObjectId(0);
						String path = new String(walk.getRawPath());
						visitor.visit(obj, path);
					}
				} catch (IOException e) {
				}
			}
		}
		return true;
	}

	public static class SkipSameEntryFilter extends TreeFilter {

		private HashSet<AnyObjectId> visited = new HashSet<>(65536);

		public SkipSameEntryFilter() {

		}

		@Override
		public boolean shouldBeRecursive() {
			return true;
		}

		@Override
		public boolean include(TreeWalk walker) throws MissingObjectException, IncorrectObjectTypeException, IOException {
			return visited.add(walker.getObjectId(0));
		}

		@Override
		public TreeFilter clone() {
			SkipSameEntryFilter another = new SkipSameEntryFilter();
			another.visited.addAll(this.visited);
			return another;
		}

	}

}

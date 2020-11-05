package jp.naist.se.codehash;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import org.eclipse.jgit.api.Git;
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


/**
 * This is a main class to list all non-binary blobs in a specified git repository.
 * Command line parameter: A git repo directory.  The current directory is used by default
 */
public class GitFileList {
	
	private static final String target = "HEAD";

	public static void main(String[] args) {
		// Use the current directory by default
		if (args.length == 0) {
			args = new String[] {"."};
		}
		
		File f = new File(args[0]);
		try (Git git = Git.open(f)) {
			Repository repo = git.getRepository();
			try (ObjectWalk rev = new ObjectWalk(repo)) {
				AnyObjectId commitId = repo.resolve(target);
				if (commitId == null) {
					System.err.println("[Error] GitFileList: Failed to find [" + target + "] in the repository.");
					return;
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
							ObjectLoader loader = repo.getObjectDatabase().open(obj);
							try (InputStream stream = loader.openStream()) {
								boolean result = RawText.isBinary(stream);
								if (!result) {
									String path = new String(walk.getRawPath());
									System.out.println(obj.getName() + "\t" + path);
								}
							}
						}
					} catch (IOException e) {
					}
				}
			}
		} catch (IOException e) {
			System.err.print("[Error] GitFileList: Failed to open a git repo " + f.getAbsolutePath() + ". " + e.getMessage());
		}
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

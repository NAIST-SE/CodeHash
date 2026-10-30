package jp.naist.se.codehash.util;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;

public class GitRepositoryUtil {

	/**
	 * Open a git repository.
	 * @param dir specifies either a working directory or a .git directory (including a bare repository).
	 * @return an opened repository.  The caller is responsible to close it.
	 * @throws IOException if dir is not a git repository.
	 */
	public static Repository openRepository(File dir) throws IOException {
		File gitDir = RepositoryCache.FileKey.resolve(dir, FS.DETECTED);
		if (gitDir == null) {
			gitDir = dir;
		}
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		b.setMustExist(true);
		return b.build();
	}

}

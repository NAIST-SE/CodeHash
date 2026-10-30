package jp.naist.se.codehash;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

import jp.naist.se.codehash.hash.SourceCodeHash;
import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;
import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.util.GitRepositoryUtil;


/**
 * The main class for this project.
 * @author ishio
 */
public class GitCodeHash {

	/**
	 * Extract hash values for source file contents excluding whitespace and comments from Git directories.
	 * @param args The first argument specifies a CSV file.
	 * The file must includes a repo path, a csv file path including blob hash and
	 * an output file path, and a hash type (codehash, minhash, or sha1minhash).
	 * If the first argument is a directory, it is regarded as a git repository.
	 * In this case, all blobs reachable from HEAD are processed and the result is written to STDOUT.
	 * The second argument optionally specifies a hash type (codehash by default).
	 */
	public static void main(String[] args) {
		GitCodeHash analyzer = new GitCodeHash();
		if (args.length == 0) {
			System.err.println("Usage: GitCodeHash input.csv");
			System.err.println("       GitCodeHash path/to/repo [codehash|minhash|sha1minhash]");
			return;
		}

		String inputFileName = args[0];
		if (new File(inputFileName).isDirectory()) {
			MinHashType t = args.length > 1 ? parseHashType(args[1]) : MinHashType.None;
			try (PrintWriter w = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out), 65536))) {
				analyzer.parseGitRepository(new File(inputFileName), w, t);
			}
			return;
		}

		try (LineNumberReader outcsv = new LineNumberReader(new FileReader(inputFileName), 65536)) {

			for (String line = outcsv.readLine(); line != null; line = outcsv.readLine()) {
				String[] tokens = line.split(",");
				try {
					String repoPath = tokens[0];
					String filelistPath = tokens[1];
					String outputFilePath = tokens[2];
					String hashtype = tokens[3];
					MinHashType t = parseHashType(hashtype);

					File gitDir = new File(repoPath);
					File outputFile = new File(outputFilePath);
					if (!outputFile.exists()) {
						File outputFileTemp = new File(outputFilePath + ".tmp");
						try (LineNumberReader reader = new LineNumberReader(new FileReader(filelistPath))) {
							try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(outputFileTemp), 65536))) {
								analyzer.parseGitRepository(gitDir, reader, w, t);
							}
							outputFileTemp.renameTo(outputFile);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * @param hashtype is one of codehash, minhash, sha1minhash, and sha1minhashInPaper.
	 * @return MinHashType.None (codehash only) for an unknown name.
	 */
	private static MinHashType parseHashType(String hashtype) {
		if (hashtype.equals("minhash")) {
			return MinHashType.Murmur3;
		} else if (hashtype.equals("sha1minhash")) {
			return MinHashType.SHA1;
		} else if (hashtype.equals("sha1minhashInPaper")) {
			return MinHashType.SHA1InPaper;
		} else {
			return MinHashType.None;
		}
	}

	public GitCodeHash() {
	}

	/**
	 * @param gitDir is a working directory or a .git directory.
	 * @param target is a list of source files in the repo (.tsv files including SHA1, file name, and LANGUAGE).
	 */
	public void parseGitRepository(File gitDir, LineNumberReader target, PrintWriter w, MinHashType hashType) {
		try (Repository repo = GitRepositoryUtil.openRepository(gitDir)) {
			try (ObjectReader r = repo.getObjectDatabase().newReader()) {

				for (String line = target.readLine(); line != null; line = target.readLine()) {
					int firstTabIndex = line.indexOf('\t');
					int lastTabIndex = line.lastIndexOf('\t');
					if (firstTabIndex < 0) continue; // Skip a bad format line

					FileType t;
					if (firstTabIndex == lastTabIndex) { // 2-columns format (blob-id,filename)
						String filename = line.substring(firstTabIndex+1, line.length());
						t = FileType.getFileTypeFromName(filename);
					} else { // 3-column format (blob-id,filename,lang)
						String filetype = line.substring(lastTabIndex+1, line.length());
						t = FileType.getFileType(filetype);
					}

					if (t != FileType.UNSUPPORTED) {
						String sha1 = line.substring(0, firstTabIndex);
						processBlob(r, ObjectId.fromString(sha1), t, w, hashType);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Process all blobs reachable from HEAD in a repository.
	 * The language of each blob is detected by its file name.
	 * @param gitDir is a working directory or a .git directory.
	 */
	public void parseGitRepository(File gitDir, PrintWriter w, MinHashType hashType) {
		try (Repository repo = GitRepositoryUtil.openRepository(gitDir)) {
			try (ObjectReader r = repo.getObjectDatabase().newReader()) {
				boolean found = GitFileList.visitBlobs(repo, GitFileList.DEFAULT_TARGET, (id, path) -> {
					FileType t = FileType.getFileTypeFromName(path);
					if (t != FileType.UNSUPPORTED) {
						processBlob(r, id, t, w, hashType);
					}
				});
				if (!found) {
					System.err.println("[Error] GitCodeHash: Failed to find [" + GitFileList.DEFAULT_TARGET + "] in the repository.");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Compute hash values for a blob and write them to w.
	 * Missing objects and I/O errors are ignored.
	 */
	private void processBlob(ObjectReader r, AnyObjectId id, FileType t, PrintWriter w, MinHashType hashType) {
		try {
			ObjectLoader l = r.open(id);
			long size = l.getSize();
			SourceCodeHash h = new SourceCodeHash(t, l.openStream(), size, hashType);

			StringBuilder result = new StringBuilder(256);
			result.append(id.getName());
			result.append("\t");
			result.append(t.name());
			result.append("\t");
			result.append(h.getCodeHash());
			result.append("\t");
			if (h.getMinHash() != null) {
				result.append(h.getMinHash());
				result.append("\t");
			}
			result.append(size);
			result.append("\t");
			result.append(h.getTokenCount());
			w.println(result.toString());

		} catch (MissingObjectException e) {
			// Ignore missing objects
		} catch (IOException e) {
			// Ignore
		}
	}

}

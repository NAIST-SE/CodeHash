package jp.naist.se.codehash;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;

import org.eclipse.jgit.errors.MissingObjectException;
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
	 */
	public static void main(String[] args) { 
		GitCodeHash analyzer = new GitCodeHash();
		String inputFileName = args[0];
		
		try (LineNumberReader outcsv = new LineNumberReader(new FileReader(inputFileName), 65536)) {

			for (String line = outcsv.readLine(); line != null; line = outcsv.readLine()) {
				String[] tokens = line.split(",");
				try {
					String repoPath = tokens[0];
					String filelistPath = tokens[1];
					String outputFilePath = tokens[2];
					String hashtype = tokens[3];
					MinHashType t = MinHashType.None;
					if (hashtype.equals("minhash")) {
						t = MinHashType.Murmur3;
					} else if (hashtype.equals("sha1minhash")) {
						t = MinHashType.SHA1;
					} else if (hashtype.equals("sha1minhashInPaper")) {
						t = MinHashType.SHA1InPaper;
					}
					
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
						ObjectId id = ObjectId.fromString(sha1);
						
						try {
							ObjectLoader l = r.open(id);
							long size = l.getSize();
							SourceCodeHash h = new SourceCodeHash(t, l.openStream(), size, hashType);

							StringBuilder result = new StringBuilder(256);
							result.append(sha1);
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
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

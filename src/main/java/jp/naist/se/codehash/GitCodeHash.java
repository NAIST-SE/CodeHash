package jp.naist.se.codehash;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import jp.naist.se.codehash.sha1.SHA1MinHash;
import jp.naist.se.codehash.sha1.SHA1MinHashMSR2017;


/**
 * The main class for this project.
 * @author ishio
 */
public class GitCodeHash {

	public static enum HashType { CodeHash, SHA1MinHash, Murmur3MinHash, SHA1MinHashInPaper };
	public static int BBITMINHASH_BITCOUNT = 2048;
	public static int BBITMINHASH_NGRAM_SIZE = 3;
	
	/**
	 * Extract hash values for source file contents excluding whitespace and comments from Git directories.
	 * @param args The first argument specifies a CSV file.
	 * The file must includes a repo path, a csv file path including blob hash and 
	 * language to be processed, an output file path, and a hash type (codehash, minhash, or sha1minhash).   
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
					HashType t = HashType.CodeHash;
					if (hashtype.equals("minhash")) {
						t = HashType.Murmur3MinHash;
					} else if (hashtype.equals("sha1minhash")) {
						t = HashType.SHA1MinHash;
					} else if (hashtype.equals("sha1minhashInPaper")) {
						t = HashType.SHA1MinHashInPaper;
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
	 * Check whether a specified directory is .git directory or not.   
	 * @param dir
	 * @return dir itself if it is a .git directory.
	 * If it includes .git as a subdirectory, the subdirectory is returned.  
	 * The method returns null if dir is not .git directory. 
	 */
	public static File ensureGitDir(File dir) {
		if (dir.isDirectory()) {
			if (dir.getName().equals(".git") || dir.getName().endsWith(".git")) {
				return dir;
			} else {
				File another = new File(dir, ".git");
				if (another.exists() && another.isDirectory()) {
					return another;
				}
				File[] candidates = dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith(".git");
					}
				});
				if (candidates.length > 0) {
					return candidates[0];
				}
			}
		}
		return null;
	}
	
	
	/**
	 * @param gitDir is a .git directory.
	 * @param target is a list of source files in the repo (.tsv files including SHA1, file name, and LANGUAGE).
	 */
	public void parseGitRepository(File gitDir, LineNumberReader target, PrintWriter w, HashType hashType) {
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
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
							TokenReader tokenReader = FileType.createReader(t, l.openStream());
							long size = l.getSize();

							String codehash, minhash;
							if (hashType == HashType.Murmur3MinHash) {
								CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, size);
								MurmurMinHash h = new MurmurMinHash(BBITMINHASH_BITCOUNT, BBITMINHASH_NGRAM_SIZE, wrapper);
								minhash = HashStringUtil.bytesToHex(h.getHash());
								codehash = HashStringUtil.bytesToHex(wrapper.getHash());
							} else if (hashType == HashType.SHA1MinHash) {
								CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, size);
								SHA1MinHash h = new SHA1MinHash(BBITMINHASH_BITCOUNT, BBITMINHASH_NGRAM_SIZE, wrapper);
								minhash = HashStringUtil.bytesToHex(h.getHash());
								codehash = HashStringUtil.bytesToHex(wrapper.getHash());
							} else if (hashType == HashType.SHA1MinHashInPaper) {
								CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, size);
								SHA1MinHashMSR2017 h = new SHA1MinHashMSR2017(BBITMINHASH_BITCOUNT, BBITMINHASH_NGRAM_SIZE, wrapper);
								minhash = HashStringUtil.bytesToHex(h.getHash());
								codehash = HashStringUtil.bytesToHex(wrapper.getHash());
							} else {
								CodeHash h = new CodeHash(tokenReader, size);
								codehash = HashStringUtil.bytesToHex(h.getHash());
								minhash = null;
							}
							
							StringBuilder result = new StringBuilder(256);
							result.append(sha1);
							result.append("\t");
							result.append(t.name());
							result.append("\t");
							result.append(codehash);
							result.append("\t");
							if (minhash != null) {
								result.append(minhash);
								result.append("\t");
							}
							result.append(size);
							result.append("\t");
							result.append(tokenReader.getTokenCount());
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

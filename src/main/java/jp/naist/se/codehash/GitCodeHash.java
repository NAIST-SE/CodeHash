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



public class GitCodeHash {

	//public static final String REPO_ROOT = "/data/repo-2019-hata/";
	private enum HashType { CodeHash, NgramMinHash };
	private int BBITMINHASH_BITCOUNT = 2048;
	private int BBITMINHASH_NGRAM_SIZE = 3;
	
	/**
	 * Extract all comments from Git directories.
	 * @param args The first argument specifies a CSV file.
	 * The file must includes a repo path, a csv file path including blob hash and 
	 * language to be processed, an output file path, and a hash type (codehash or minhash).   
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
					HashType t = hashtype.equals("minhash") ? HashType.NgramMinHash : HashType.CodeHash;
					
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
					int index = line.lastIndexOf('\t');
					String filetype = line.substring(index+1, line.length());
					FileType t = FileType.getFileType(filetype);
					if (t != FileType.UNSUPPORTED) {
						index = line.indexOf('\t');
						String sha1 = line.substring(0, index);
						ObjectId id = ObjectId.fromString(sha1);
						
						try {
							ObjectLoader l = r.open(id);
							TokenReader tokenReader = FileType.createReader(t, l.openStream());
							long size = l.getSize();

							IHash h;
							if (hashType == HashType.NgramMinHash) {
								h = new MinHash(BBITMINHASH_BITCOUNT, BBITMINHASH_NGRAM_SIZE, tokenReader);
							} else {
								h = new CodeHash(tokenReader, size);
							}
							String codehash = bytesToHex(h.getHash());
							
							StringBuilder result = new StringBuilder(256);
							result.append(sha1);
							result.append("\t");
							result.append(filetype);
							result.append("\t");
							result.append(codehash);
							result.append("\t");
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
	
	private final static char[] hexArray = "0123456789abcdef".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

}

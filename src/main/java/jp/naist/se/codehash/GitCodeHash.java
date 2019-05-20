package jp.naist.se.codehash;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;



public class GitCodeHash {

	public static final String REPO_ROOT = "/data/repo-2019-hata/";
	
	/**
	 * Extract all comments from Git directories.
	 * @param args specify a directory and a list of SHA1.
	 */
	public static void main(String[] args) { 
		GitCodeHash analyzer = new GitCodeHash();
		
		try (LineNumberReader outcsv = new LineNumberReader(new FileReader(args[0]))) {

			for (String line = outcsv.readLine(); line != null; line = outcsv.readLine()) {
				String[] tokens = line.split(",");
				String repoId = tokens[0];
				
				File gitDir = new File(REPO_ROOT + repoId);
				String filelist = "filelist/" + repoId + ".txt";
				String outputFile = "codehash/" + repoId + "-j.txt";
				try (LineNumberReader reader = new LineNumberReader(new FileReader(filelist))) {
					try (PrintWriter w = new PrintWriter(new FileWriter(outputFile))) {
						analyzer.parseGitRepository(gitDir, reader, w);
					} 
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private MessageDigest digest;

	public GitCodeHash() {
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to compute SHA-1 hash", e);
		}
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
	public void parseGitRepository(File gitDir, LineNumberReader target, PrintWriter w) {
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			try (ObjectReader r = repo.getObjectDatabase().newReader()) {

				for (String line = target.readLine(); line != null; line = target.readLine()) {
					String[] tokens = line.split("\t");
					
					FileType t = FileType.getFileType(tokens[2]);
					if (t != FileType.UNSUPPORTED) {
						ObjectId id = ObjectId.fromString(tokens[0]);
						
						try {
							ObjectLoader l = r.open(id);
							TokenReader tokenReader = FileType.createReader(t, l.openStream());
							long size = l.getSize();
							
							digest.reset();
							int tokenCount = 0;
							while (tokenReader.next()) {
								digest.update(tokenReader.getText().getBytes());
								digest.update((byte)0);
								tokenCount++;
							}
							byte[] codehashBytes = digest.digest();
							String codehash = bytesToHex(codehashBytes);
							
							w.print(tokens[0]);
							w.print("\t");
							w.print(tokens[2]);
							w.print("\t");
							w.print(codehash);
							w.print("\t");
							w.print(size);
							w.print("\t");
							w.print(tokenCount);
							w.println();
							
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

package jp.naist.se.codehash;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedList;

import jp.naist.se.codehash.hash.FileHash;
import jp.naist.se.codehash.hash.SourceCodeHash;
import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;
import jp.naist.se.codehash.token.FileType;


/**
 * A main class to calculate code hash and minhash  
 */
public class FileCodeHash {

	public static final String ARG_MINHASH_IGNORE_DUPLICATION = "-ignoreduplication";
	
	public static void main(String[] args) {
		boolean ignoreDuplication = false;
		LinkedList<File> files = new LinkedList<>();
		for (String arg: args) {
			if (arg.equals(ARG_MINHASH_IGNORE_DUPLICATION)) {
				ignoreDuplication = true;
			} else {
				files.add(new File(arg));
			}
		}
		
		if (!files.isEmpty()) {
			FileCodeHash h = new FileCodeHash(ignoreDuplication);
			System.out.println(HEADER);
			h.scan(files);
		}  else {
			System.err.println("No files are specified.");
		}
	}
	
	private boolean ignoreDuplication;
	
	/**
	 * 
	 * @param ignoreDuplication If true, this object uses a set rather than a multiset to manage N-grams. 
	 */
	public FileCodeHash(boolean ignoreDuplication) {
		this.ignoreDuplication = ignoreDuplication;
	}
	
	/**
	 * Scan given files.  This method recursively analyzes directories.
	 * @param files
	 */
	public void scan(LinkedList<File> files) {
		while (!files.isEmpty()) {
			File f = files.removeFirst();
			if (f.isDirectory() && f.canRead()) {
				File[] children = f.listFiles();
				for (File c: children) {
					if (c.isDirectory() &&
						!c.getName().equals(".") && 
						!c.getName().equals("..")) {
						files.add(c);
					} else if (c.isFile() && f.canRead()) {
						process(c);
					}
				}
			} else if (f.isFile() && f.canRead()) {
				process(f);
			}
		}
	}
	
	/**
	 * Calculate a code hash for a given file and output the result. 
	 * @param f
	 */
	public void process(File f) {
		try {
			String path = f.getAbsolutePath();
			FileType t = FileType.getFileTypeFromName(path);
			if (FileType.isSupported(t)) {
				byte[] content = Files.readAllBytes(f.toPath());
				String sha1 = FileHash.compute(content);
				SourceCodeHash h = new SourceCodeHash(t, new ByteArrayInputStream(content), f.length(),
						ignoreDuplication ? MinHashType.Murmur3IgnoreDuplication : MinHashType.Murmur3);

				System.out.println(toLine(path, sha1, t, h, f.length()));
			}

		} catch (IOException e) {
			// Ignore 
		}
		
	}

	/**
	 * The header line of a minhash list.  Column names correspond to {@link #toLine(String, String, FileType, SourceCodeHash, long)}.
	 */
	public static final String HEADER = "FileName\tSHA1\tLanguage\tCodeHash\tMinHash\tMinHashWithNormalization\tFileLength\tTokenLength\tNgramCount";

	/**
	 * Format a line of a minhash list.  {@link jp.naist.se.codehash.comparison.ComparisonMain} reads this format.
	 * GitCodeHash also uses this format.
	 * @param path is a file path (or a blob ID and a path in a git repository).
	 * @param sha1 is the SHA-1 hash of the file content.
	 * @param t is the language of the file.
	 * @param h is the hash values of the file.  A minhash value unavailable in h is written as an empty string.
	 * @param size is the byte length of the file.
	 * @return a tab-separated line comprising nine columns.
	 */
	public static String toLine(String path, String sha1, FileType t, SourceCodeHash h, long size) {
		StringBuilder result = new StringBuilder(256);
		result.append(path);
		result.append("\t");
		result.append(sha1);
		result.append("\t");
		result.append(t.name());
		result.append("\t");
		result.append(h.getCodeHash());
		result.append("\t");
		if (h.getMinHash() != null) result.append(h.getMinHash());
		result.append("\t");
		if (h.getNormalizedMinHash() != null) result.append(h.getNormalizedMinHash());
		result.append("\t");
		result.append(size);
		result.append("\t");
		result.append(h.getTokenCount());
		result.append("\t");
		result.append(h.getNgramCount());
		return result.toString();
	}
	
	
}

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
	
	public void process(File f) {
		try {
			String path = f.getAbsolutePath();
			FileType t = FileType.getFileTypeFromName(path);
			if (FileType.isSupported(t)) {
				byte[] content = Files.readAllBytes(f.toPath());
				String sha1 = FileHash.compute(content);
				SourceCodeHash h = new SourceCodeHash(t, new ByteArrayInputStream(content), f.length(),
						ignoreDuplication ? MinHashType.Murmur3IgnoreDuplication : MinHashType.Murmur3);

				StringBuilder result = new StringBuilder(256);
				result.append(path);
				result.append("\t");
				result.append(sha1);
				result.append("\t");
				result.append(t.name());
				result.append("\t");
				result.append(h.getCodeHash());
				result.append("\t");
				result.append(h.getMinHash());
				result.append("\t");
				result.append(h.getNormalizedMinHash());
				result.append("\t");
				result.append(f.length());
				result.append("\t");
				result.append(h.getTokenCount());
				result.append("\t");
				result.append(h.getNgramCount());
				System.out.println(result.toString());
			}

		} catch (IOException e) {
			// Ignore 
		}
		
	}
	
	
}

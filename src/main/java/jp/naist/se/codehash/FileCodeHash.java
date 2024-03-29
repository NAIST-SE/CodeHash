package jp.naist.se.codehash;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;


/**
 * A main class to calculate code hash and minhash  
 */
public class FileCodeHash {

	public static final String ARG_MINHASH_IGNORE_DUPLICATION = "-ignoreduplication";
	public static final String FILEHASH_ALGORITHM = "SHA-1";
	
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
				try {
					byte[] content = Files.readAllBytes(f.toPath());
					MessageDigest d = MessageDigest.getInstance(FILEHASH_ALGORITHM);
					String sha1 = HashStringUtil.bytesToHex(d.digest(content));
					String codehash = null;
					String minhash = null;
					String normalizedMinhash = null;
					int ngramCount = 0;
					TokenReader tokenReader = FileType.createReader(t, new ByteArrayInputStream(content));
					CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, f.length());
					MurmurMinHash h = new MurmurMinHash(GitCodeHash.BBITMINHASH_BITCOUNT, GitCodeHash.BBITMINHASH_NGRAM_SIZE, wrapper);
					if (ignoreDuplication) {
						minhash = HashStringUtil.bytesToHex(h.getHashIgnoreDuplicatedElements());
						normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHashIgnoreDuplicatedElements());
					} else {
						minhash = HashStringUtil.bytesToHex(h.getHash());
						normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHash());
					}
					codehash = HashStringUtil.bytesToHex(wrapper.getHash());
					ngramCount = h.getNgramCount();
					
					StringBuilder result = new StringBuilder(256);
					result.append(path);
					result.append("\t");
					result.append(sha1);
					result.append("\t");
					result.append(t.name());
					result.append("\t");
					result.append(codehash);
					result.append("\t");
					result.append(minhash);
					result.append("\t");
					result.append(normalizedMinhash);
					result.append("\t");
					result.append(f.length());
					result.append("\t");
					result.append(tokenReader.getTokenCount());
					result.append("\t");
					result.append(ngramCount);
					System.out.println(result.toString());
				} catch (NoSuchAlgorithmException e) {
				}
			}

		} catch (IOException e) {
			// Ignore 
		}
		
	}
	
	
}

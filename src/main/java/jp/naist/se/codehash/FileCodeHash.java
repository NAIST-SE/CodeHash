package jp.naist.se.codehash;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;


import jp.naist.se.codehash.GitCodeHash.HashType;


public class FileCodeHash {

	public static final String ARG_MINHASH = "-minhash";
	public static final String ARG_CODEHASH = "-codehash";
	public static final String FILEHASH_ALGORITHM = "SHA-1";
	
	public static void main(String[] args) {
		boolean generateCodehash = true; // for compatibility
		boolean generateMinhash = false;
		LinkedList<File> files = new LinkedList<>();
		for (String arg: args) {
			if (arg.equals(ARG_MINHASH)) {
				generateMinhash = true;
			} else if (arg.equals(ARG_CODEHASH)) {
				generateCodehash = true;
			} else {
				files.add(new File(arg));
			}
		}
		
		if (!files.isEmpty()) {
			FileCodeHash h = new FileCodeHash(generateCodehash, generateMinhash);
			h.scan(files);
		}  else {
			System.err.println("No files are specified.");
		}
	}
	
	private boolean outputCodeHash;
	private boolean outputMinHash;
	
	public FileCodeHash(boolean codehash, boolean minhash) {
		this.outputCodeHash = codehash;
		this.outputMinHash = minhash;
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
					String sha1 = GitCodeHash.bytesToHex(d.digest(content));
					String codehash = null;
					String minhash = null;
					TokenReader tokenReader = FileType.createReader(t, new ByteArrayInputStream(content));
					codehash = GitCodeHash.computeHash(tokenReader, f.length(), HashType.CodeHash);
					if (outputMinHash) {
						TokenReader anotherTokenReader = FileType.createReader(t, new ByteArrayInputStream(content));
						minhash = GitCodeHash.computeHash(anotherTokenReader, f.length(), HashType.NgramMinHash);
					}
					
					
					StringBuilder result = new StringBuilder(256);
					result.append(path);
					result.append("\t");
					result.append(sha1);
					result.append("\t");
					result.append(t.name());
					result.append("\t");
					if (outputCodeHash) {
						result.append(codehash);
						result.append("\t");
					}
					if (outputMinHash) {
						result.append(minhash);
						result.append("\t");
					}
					result.append(f.length());
					result.append("\t");
					result.append(tokenReader.getTokenCount());
					System.out.println(result.toString());
				} catch (NoSuchAlgorithmException e) {
				}
			}

		} catch (IOException e) {
			// Ignore 
		}
		
	}
	
	
}

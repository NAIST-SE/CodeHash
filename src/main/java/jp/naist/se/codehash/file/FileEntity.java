package jp.naist.se.codehash.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import jp.naist.se.codehash.CodeHashTokenReader;
import jp.naist.se.codehash.FileCodeHash;
import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.GitCodeHash;
import jp.naist.se.codehash.HashStringUtil;
import jp.naist.se.codehash.MurmurMinHash;
import jp.naist.se.codehash.TokenReader;
import jp.naist.se.codehash.comparison.MinHashEntry;
import jp.naist.se.codehash.util.StringMultiset;

public class FileEntity {
		
	private static int seqnum = 0;
	
	private int index;
	private String path;
	private FileType type;
	private int byteLength;
	private int tokenLength;
	private String filehash;
	private String codehash;
	private String minhash;
	private String normalizedMinhash;
	private int ngramCount;
	private StringMultiset ngrams;
	private StringMultiset normalizedNgrams;
	private MinHashEntry minhashEntry;
	
	/**
	 * Create a FileEntity object from a File.
	 * @param f specifies a file.  The content will be loaded.
	 * @param enforceLanguage specifies a programming language.  
	 * If null, the method automatically tries to recognize a programming language from the file extension.
	 * @param N specifies the size of N-gram to compare files.
	 * @return a created object.
	 */
	public static FileEntity parse(Path filePath, FileType enforceLanguage, int N) {
		String path = filePath.toAbsolutePath().toString();
		FileType type = enforceLanguage != null ? enforceLanguage : FileType.getFileTypeFromName(path);
		if (FileType.isSupported(type)) {
			try {
				byte[] content = Files.readAllBytes(filePath);
				return new FileEntity(path, type, content, N);
			} catch (IOException e) {
				return null;
			}
		} else {
			return null;
		}
	}
	
	/**
	 * Construct a FileEntity object.
	 * @param path specifies a file name.  The value is only used when writing a result.  
	 * @param type specifies a programming language to parse the content. 
	 * @param content is the content of a file.
	 * @param N specifies the size of N-gram to compare files.
	 */
	public FileEntity(String path, FileType type, byte[] content, int N) {
		assert type != null: "type must be nonnull";
		
		this.index = seqnum++;
		this.path = path;
		this.type = type;
		this.byteLength = content.length;
		try {
			MessageDigest d = MessageDigest.getInstance(FileCodeHash.FILEHASH_ALGORITHM);
			filehash = HashStringUtil.bytesToHex(d.digest(content));
			TokenReader tokenReader = FileType.createReader(type, new ByteArrayInputStream(content));
			CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, byteLength);
			MurmurMinHash h = new MurmurMinHash(GitCodeHash.BBITMINHASH_BITCOUNT, N, wrapper);
			minhash = HashStringUtil.bytesToHex(h.getHash());
			normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHash());
			codehash = HashStringUtil.bytesToHex(wrapper.getHash());
			ngramCount = h.getNgramCount();
			ngrams = h.getNgramMultiset();
			normalizedNgrams = h.getNormalizedNgramMultiset();
			tokenLength = tokenReader.getTokenCount();

			minhashEntry = new MinHashEntry(path, filehash, getLanguageName(), codehash, minhash, normalizedMinhash, byteLength, tokenLength, ngramCount);
		} catch (NoSuchAlgorithmException e) { 
			e.printStackTrace();
		}
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getPath() {
		return path;
	}
	
	public String getFileHash() {
		return filehash;
	}
	
	public String getCodeHash() {
		return codehash;
	}
	
	public FileType getLanguage() {
		return type;
	}
	
	public String getLanguageName() {
		return type.name();
	}
	
	public boolean isSameLanguage(FileEntity another) { 
		return this.type == another.type;
	}
	
	public int getByteLength() {
		return byteLength;
	}

	public int getTokenLength() {
		return tokenLength;
	}
	
	public int getNgramCount() {
		return ngramCount;
	}
	
	public Set<String> getNgrams() {
		return ngrams.keySet();
	}

	public Set<String> getNormalizedNgrams() {
		return normalizedNgrams.keySet();
	}

	public StringMultiset getNgramMultiset() {
		return ngrams;
	}

	public StringMultiset getNormalizedNgramMultiset() {
		return normalizedNgrams;
	}
	
	public double estimateNormalizedSimilarity(FileEntity another) {
		return this.minhashEntry.estimateNormalizedSimilarity(another.minhashEntry);
	}

}

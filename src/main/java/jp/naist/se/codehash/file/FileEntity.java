package jp.naist.se.codehash.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jp.naist.se.codehash.hash.BitMinHash;
import jp.naist.se.codehash.hash.FileHash;
import jp.naist.se.codehash.hash.SourceCodeHash;
import jp.naist.se.codehash.hash.SourceCodeHash.MinHashType;
import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.util.IntMultiset;
import jp.naist.se.codehash.util.StringDictionary;

public class FileEntity {

	private static int seqnum = 0;

	private int index = -1;
	private String path;
	private FileType type;
	private int byteLength;
	private int tokenLength;
	private String filehash;
	private String codehash;
	private int ngramCount;
	private IntMultiset ngrams;
	private IntMultiset normalizedNgrams;
	private BitMinHash minhash;
	private BitMinHash normalizedMinhash;

	/**
	 * Create a FileEntity object from a File.
	 * @param filePath specifies a file.  The content will be loaded.
	 * @param enforceLanguage specifies a programming language.
	 * If null, the method automatically tries to recognize a programming language from the file extension.
	 * @param N specifies the size of N-gram to compare files.
	 * @param dictionary translates N-grams into IDs.  Files to be compared must share the same dictionary.
	 * @param computeMinHash If true, a minhash vector of N-grams is computed for {@link #estimateSimilarity(FileEntity)}.
	 * @param computeNormalizedMinHash If true, a minhash vector of normalized N-grams is computed for {@link #estimateNormalizedSimilarity(FileEntity)}.
	 * @return a created object.  null if the file is not a supported source file or cannot be read.
	 */
	public static FileEntity parse(Path filePath, FileType enforceLanguage, int N, StringDictionary dictionary, boolean computeMinHash, boolean computeNormalizedMinHash) {
		String path = filePath.toAbsolutePath().toString();
		FileType type = enforceLanguage != null ? enforceLanguage : FileType.getFileTypeFromName(path);
		if (FileType.isSupported(type)) {
			try {
				byte[] content = Files.readAllBytes(filePath);
				return new FileEntity(path, type, content, N, dictionary, computeMinHash, computeNormalizedMinHash);
			} catch (IOException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	/**
	 * Construct a FileEntity object.
	 * The index of the object is not assigned until {@link #assignIndex()} is called.
	 * @param path specifies a file name.  The value is only used when writing a result.
	 * @param type specifies a programming language to parse the content.
	 * @param content is the content of a file.
	 * @param N specifies the size of N-gram to compare files.
	 * @param dictionary translates N-grams into IDs.  Files to be compared must share the same dictionary.
	 * @param computeMinHash If true, a minhash vector of N-grams is computed for {@link #estimateSimilarity(FileEntity)}.
	 * @param computeNormalizedMinHash If true, a minhash vector of normalized N-grams is computed for {@link #estimateNormalizedSimilarity(FileEntity)}.
	 */
	public FileEntity(String path, FileType type, byte[] content, int N, StringDictionary dictionary, boolean computeMinHash, boolean computeNormalizedMinHash) {
		assert type != null: "type must be nonnull";

		this.path = path;
		this.type = type;
		this.byteLength = content.length;
		filehash = FileHash.compute(content);
		SourceCodeHash h = new SourceCodeHash(type, new ByteArrayInputStream(content), byteLength, MinHashType.Murmur3, N);
		codehash = h.getCodeHash();
		ngramCount = h.getNgramCount();
		ngrams = new IntMultiset(h.getNgramMultiset(), dictionary);
		normalizedNgrams = new IntMultiset(h.getNormalizedNgramMultiset(), dictionary);
		tokenLength = h.getTokenCount();
		if (computeMinHash) {
			minhash = new BitMinHash(h.getMinHash(), ngramCount);
		}
		if (computeNormalizedMinHash) {
			normalizedMinhash = new BitMinHash(h.getNormalizedMinHash(), ngramCount);
		}
	}

	/**
	 * Assign a sequential number to this object.
	 * Numbers are assigned in the order of calls, starting from 0.
	 * This method is not thread-safe.
	 */
	void assignIndex() {
		this.index = seqnum++;
	}

	/**
	 * @return a sequential number of the file assigned by {@link #assignIndex()}.  -1 if it is not assigned.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @return the file path given to the constructor.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return a hexadecimal string of the SHA-1 hash of the file content.
	 */
	public String getFileHash() {
		return filehash;
	}

	/**
	 * @return a hexadecimal string of the SHA-1 hash of the tokens, ignoring whitespace and comments.
	 */
	public String getCodeHash() {
		return codehash;
	}

	/**
	 * @return the programming language used to parse the file.
	 */
	public FileType getLanguage() {
		return type;
	}

	/**
	 * @return the name of the programming language, e.g. "JAVA".
	 */
	public String getLanguageName() {
		return type.name();
	}

	public boolean isSameLanguage(FileEntity another) {
		return this.type == another.type;
	}

	/**
	 * @return the number of bytes of the file content.
	 */
	public int getByteLength() {
		return byteLength;
	}

	/**
	 * @return the number of tokens in the file.
	 */
	public int getTokenLength() {
		return tokenLength;
	}

	/**
	 * @return the number of N-grams in the file, including duplicated ones.
	 */
	public int getNgramCount() {
		return ngramCount;
	}

	/**
	 * @return a multiset of IDs of N-grams in the file.
	 */
	public IntMultiset getNgramMultiset() {
		return ngrams;
	}

	/**
	 * @return a multiset of IDs of N-grams in the file after identifier normalization.
	 */
	public IntMultiset getNormalizedNgramMultiset() {
		return normalizedNgrams;
	}

	/**
	 * Both objects must be created with computeMinHash = true.
	 * @param another is compared with this file.
	 * @return the Jaccard index of N-grams estimated by minhash.  0 if the files are written in different languages.
	 */
	public double estimateSimilarity(FileEntity another) {
		if (minhash == null || another.minhash == null) throw new IllegalStateException("Minhash is not computed");
		return isSameLanguage(another) ? minhash.estimateSimilarity(another.minhash) : 0;
	}

	/**
	 * Both objects must be created with computeNormalizedMinHash = true.
	 * @param another is compared with this file.
	 * @return the Jaccard index of normalized N-grams estimated by minhash.  0 if the files are written in different languages.
	 */
	public double estimateNormalizedSimilarity(FileEntity another) {
		if (normalizedMinhash == null || another.normalizedMinhash == null) throw new IllegalStateException("Normalized minhash is not computed");
		return isSameLanguage(another) ? normalizedMinhash.estimateSimilarity(another.normalizedMinhash) : 0;
	}

}

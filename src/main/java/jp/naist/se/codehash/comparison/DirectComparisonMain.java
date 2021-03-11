package jp.naist.se.codehash.comparison;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.CodeHashTokenReader;
import jp.naist.se.codehash.FileCodeHash;
import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.GitCodeHash;
import jp.naist.se.codehash.HashStringUtil;
import jp.naist.se.codehash.MurmurMinHash;
import jp.naist.se.codehash.TokenReader;
import jp.naist.se.codehash.util.StringMultiset;

public class DirectComparisonMain {

	private static final int MAX_METRICS = 10;

	private static String LANG_OPTION = "-lang:";
	private static String NGRAM_OPTION = "-n:";
	private static String THRESHOLD = "-th:";
	private static String THREHSHOLD_ESTIMATED_NORMALIZED_JACCARD = "-thenj:";

	/**
	 * A file name filter to select files for comparison
	 * (other files are included only for IDF)
	 */
	private static String FILENAME_SELECTOR = "-prefix:";

	private static String DIR_OPTION = "-dir:";

	
	/**
	 * Compare two source files.
	 * @param args
	 */
	public static void main(String[] args) {
		DirectComparisonMain main = new DirectComparisonMain(args);
		main.run();
	}
	

	private boolean invalid = false;

	private ArrayList<FileEntity> files = new ArrayList<>();
	private ArrayList<FileEntity> idfFiles = new ArrayList<>();
	private FileType t = null;
	private int N = GitCodeHash.BBITMINHASH_NGRAM_SIZE;
	private double threshold = 0;
	private double thresholdEstimatedNormalizedJaccard = -1;
	private String filePrefix;

	public DirectComparisonMain(String[] args) {
		TreeSet<String> filenames = new TreeSet<>();
		for (String s: args) {
			if (s.startsWith(LANG_OPTION)) {
				t = FileType.getFileType(s.substring(LANG_OPTION.length()));
			} else if (s.startsWith(NGRAM_OPTION)) {
				String nString = s.substring(NGRAM_OPTION.length());
				try {
					int newN = Integer.parseInt(nString);
					if (1 <= newN && newN <= 1024) { 
						N = newN;
					} else {
						System.err.println("N is out of range: " + nString);
						return;
					}
				} catch (NumberFormatException e) {
					System.err.println("Invalid number: " + nString);
					return;
				}
			} else if (s.startsWith(THREHSHOLD_ESTIMATED_NORMALIZED_JACCARD)) {
				thresholdEstimatedNormalizedJaccard = parseThreshold(THREHSHOLD_ESTIMATED_NORMALIZED_JACCARD, s);
				if (Double.isNaN(thresholdEstimatedNormalizedJaccard)) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(THRESHOLD)) {
				threshold = parseThreshold(THRESHOLD, s);
				if (Double.isNaN(threshold)) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(FILENAME_SELECTOR)) {
				filePrefix = s.substring(FILENAME_SELECTOR.length());
			} else if (s.startsWith(DIR_OPTION)) {
				String dirname = s.substring(DIR_OPTION.length());
				try {
					Files.walkFileTree(new File(dirname).toPath(), new FileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							filenames.add(file.toString());
							return FileVisitResult.CONTINUE;
						}
						@Override
						public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}
						@Override
						public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}
						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
							return FileVisitResult.CONTINUE;
						}
					});
				} catch (IOException e) {
				}
			} else {
				filenames.add(s);
			}
		}
		for (String s: filenames) {
			File f = new File(s);
			FileEntity entity = FileEntity.parse(f, t, N);
			if (entity != null) {
				if (filePrefix == null || s.startsWith(filePrefix)) {
					files.add(entity);
				}
				idfFiles.add(entity);
			}
		}
		if (files.size() <= 1) {
			System.err.println("Arguments: Two or more source file names should be specified.");
			invalid = true;
			return;
		}
	}
	
	public void run() {
		if (invalid) return;
		
		// Count the number of Ngrams
		StringMultiset normalizedNgramFrequencyInSelectedFiles = new StringMultiset(1024);
		StringMultiset normalizedNgramFrequencyInAllFiles = new StringMultiset(1024);
		for (FileEntity f: idfFiles) {
			for (String s: f.getNormalizedNgramMultiset().keySet()) {
				normalizedNgramFrequencyInAllFiles.add(s);
			}
		}
		for (FileEntity f: files) {
			for (String s: f.getNormalizedNgramMultiset().keySet()) {
				normalizedNgramFrequencyInSelectedFiles.add(s);
			}
		}

		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(System.out)) {
			gen.useDefaultPrettyPrinter();
			gen.writeStartObject();
			
			// Print a file list
			gen.writeArrayFieldStart("Files");
			for (int i=0; i<files.size(); i++) {
				FileEntity e1 = files.get(i);
				gen.writeStartObject();
				gen.writeNumberField("index", i);
				gen.writeStringField("path", e1.path);
				gen.writeStringField("lang", e1.getLanguageName());
				gen.writeStringField("byte-sha1", e1.filehash);
				gen.writeStringField("token-sha1", e1.codehash);
				gen.writeNumberField("byte-length", e1.byteLength);
				gen.writeNumberField("token-length", e1.tokenLength);
				gen.writeNumberField("ngram-count", e1.ngramCount);
				gen.writeEndObject();
			}
			gen.writeEndArray();

			gen.writeArrayFieldStart("Pairs");

			// Compare file pairs 
			for (int i=0; i<files.size(); i++) {
				FileEntity e1 = files.get(i);
				
				for (int j=i+1; j<files.size(); j++) {
					FileEntity e2 = files.get(j);
					
					// Compare them if they are written in the same language
					if (e1.isSameLanguage(e2)) {
						// skip actual calculation if estimated similarity is low
						if (thresholdEstimatedNormalizedJaccard > 0 && e1.minhashEntry.estimateNormalizedSimilarity(e2.minhashEntry) < thresholdEstimatedNormalizedJaccard) continue;
						
						SimilarityRecord similarityValues = calculateSimilarity(e1, e2, normalizedNgramFrequencyInAllFiles, normalizedNgramFrequencyInSelectedFiles);
						if (similarityValues.isLessThan(threshold)) continue;

						gen.writeStartObject();
						gen.writeNumberField("index1", i);
						gen.writeNumberField("index2", j);
						similarityValues.writeSimilarity(gen);
						gen.writeEndObject();
					}
				}
			}
			
			gen.writeEndArray();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * 
	 * @param key
	 * @param value
	 * @return a parsed value.  Double.NaN is returned if an invalid value is specified. 
	 */
	private static double parseThreshold(String key, String value) {
		String nString = value.substring(key.length());
		try {
			double newThreshold = Double.parseDouble(nString);
			if (0 <= newThreshold && newThreshold <= 1.0) { 
				return newThreshold;
			} else {
				System.err.println("Threshold is out of range (0-1): " + nString);
				return Double.NaN;
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid threshold: " + nString);
			return Double.NaN;
		}
		
	}
	
	
	
	public SimilarityRecord calculateSimilarity(FileEntity e1, FileEntity e2, StringMultiset frequencyInAllFiles, StringMultiset frequencyInSelectedFiles) {
		SimilarityRecord sim = new SimilarityRecord();
		
		int intersection = e1.ngrams.intersection(e2.ngrams);
		double jaccard = intersection * 1.0 / (e1.ngramCount + e2.ngramCount - intersection);
		sim.add("exact-jaccard", jaccard);
		double exactOverlap = intersection * 1.0 / Math.min(e1.ngramCount, e2.ngramCount);
		sim.add("exact-overlap", exactOverlap);

		intersection = e1.normalizedNgrams.intersection(e2.normalizedNgrams);
		double normalizedJaccard = intersection * 1.0 / (e1.ngramCount + e2.ngramCount - intersection);
		sim.add("jaccard", normalizedJaccard);

		double overlap = intersection * 1.0 / Math.min(e1.ngramCount, e2.ngramCount);
		sim.add("overlap", overlap);

		double v = getWeightedJaccard(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 0);
		sim.add("w-all-jaccard", v);

		double v5 = getWeightedOverlap(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 0);
		sim.add("w-all-overlap", v5);

		double v2 = getWeightedJaccard(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 1);
		sim.add("i-all-jaccard", v2);

		double v6 = getWeightedOverlap(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 1);
		sim.add("i-all-overlap", v6);

//		double v3 = getWeightedJaccard(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInSelectedFiles, files.size(), 0);
//		sim.add("w-sel-jaccard", v3);
//
//		double v4 = getWeightedJaccard(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInSelectedFiles, files.size(), 0);
//		sim.add("i-sel-jaccard", v4);
//
//		double v7 = getWeightedOverlap(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInSelectedFiles, files.size(), 0);
//		sim.add("w-sel-overlap", v7);
//
//		double v8 = getWeightedOverlap(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInSelectedFiles, files.size(), 0);
//		sim.add("i-sel-jaccard", v4);



		double cosineN = CosineSimilarity.getTFIDFCosineSimilarity(e1.getNormalizedNgramMultiset(), e2.getNormalizedNgramMultiset(), frequencyInAllFiles, idfFiles.size());
		sim.add("tfidf-all-cosine", cosineN);

		double cosineNIDF = CosineSimilarity.getIDFCosineSimilarity(e1.getNormalizedNgramMultiset(), e2.getNormalizedNgramMultiset(), frequencyInAllFiles, idfFiles.size());
		sim.add("idf-all-cosine", cosineNIDF);

//		cosineN = CosineSimilarity.getTFIDFCosineSimilarity(e1.getNormalizedNgramMultiset(), e2.getNormalizedNgramMultiset(), frequencyInSelectedFiles, files.size());
//		sim.add("tfidf-sel-cosine", cosineN);
//
//		cosineNIDF = CosineSimilarity.getIDFCosineSimilarity(e1.getNormalizedNgramMultiset(), e2.getNormalizedNgramMultiset(), frequencyInSelectedFiles, files.size());
//		sim.add("idf-sel-cosine", cosineNIDF);

		return sim;
	}

	public static double weight(int numFiles, int freq, int smooth) {
		return Math.log(numFiles * 1.0 / freq) + smooth;
	}
	
	public static double getWeightedJaccard(StringMultiset ngram1, StringMultiset ngram2, StringMultiset ngramFrequency, int numFiles, int smooth) {
		double intersection = 0;
		double size1 = 0;
		for (String s: ngram1.keySet()) {
			intersection += Math.min(ngram1.get(s), ngram2.get(s)) * weight(numFiles, ngramFrequency.get(s), smooth);
			size1 += ngram1.get(s) * weight(numFiles, ngramFrequency.get(s), smooth);
		}
		double size2 = 0;
		for (String s: ngram2.keySet()) {
			size2 += ngram2.get(s) * weight(numFiles, ngramFrequency.get(s), smooth);
		}
		
		double jaccard = intersection / (size1 + size2 - intersection);
		return jaccard;
	}

	public static double getWeightedOverlap(StringMultiset ngram1, StringMultiset ngram2, StringMultiset ngramFrequency, int numFiles, int smooth) {
		double intersection = 0;
		double size1 = 0;
		for (String s: ngram1.keySet()) {
			intersection += Math.min(ngram1.get(s), ngram2.get(s)) * weight(numFiles, ngramFrequency.get(s), smooth);
			size1 += ngram1.get(s) * weight(numFiles, ngramFrequency.get(s), smooth);
		}
		double size2 = 0;
		for (String s: ngram2.keySet()) {
			size2 += ngram2.get(s) * weight(numFiles, ngramFrequency.get(s), smooth);
		}
		
		double overlap = intersection / Math.min(size1, size2);
		return overlap;
	}

	public static class SimilarityRecord {
		
		private String[] names;
		private double[] values;
		private int index;

		public SimilarityRecord() {
			names = new String[MAX_METRICS];
			values = new double[MAX_METRICS];
			index = 0;
		}
		
		public void add(String name, double value) {
			names[index] = name;
			values[index] = value;
			index++;
		}
		
		public boolean isLessThan(double threshold) {
			for (int i=0; i<index; i++) {
				if (values[i] >= threshold) return false;
			}
			return true;
		}
		
		public void writeSimilarity(JsonGenerator gen) throws IOException {
			for (int i=0; i<index; i++) {
				gen.writeNumberField(names[i], values[i]);
			}
		}
		
	}
	
	public static class CosineSimilarity {

		public static double weight(int numFiles, int freq) {
			return Math.log(numFiles * 1.0 / freq);
		}

		public static double getTFIDFCosineSimilarity(StringMultiset ngram1, StringMultiset ngram2, StringMultiset ngramFrequency, int numFiles) {
			double product = 0;
			double v1 = 0;
			double v2 = 0;
			for (String s: ngram1.keySet()) {
				double tf1 = ngram1.get(s) * 1.0 / ngram1.size();
				double tf2 = ngram2.get(s) * 1.0 / ngram2.size();
				double w = weight(numFiles, ngramFrequency.get(s));
				product += tf1 * tf2 * w * w;
				v1 += tf1 * tf1 * w * w;
			}
			for (String s: ngram2.keySet()) {
				double tf2 = ngram2.get(s) * 1.0 / ngram2.size();
				double w = weight(numFiles, ngramFrequency.get(s));
				v2 += tf2 * tf2 * w * w;
			}
			double cosine = product / Math.sqrt(v1 * v2);
			if (Double.isNaN(cosine)) return 0;
			return cosine;
		}

		public static double getIDFCosineSimilarity(StringMultiset ngram1, StringMultiset ngram2, StringMultiset ngramFrequency, int numFiles) {
			double product = 0;
			double v1 = 0;
			double v2 = 0;
			for (String s: ngram1.keySet()) {
				double tf1 = 1;
				double tf2 = ngram2.get(s) > 0 ? 1 : 0;
				double w = weight(numFiles, ngramFrequency.get(s));
				product += tf1 * tf2 * w * w;
				v1 += tf1 * tf1 * w * w;
			}
			for (String s: ngram2.keySet()) {
				double tf2 = 1;
				double w = weight(numFiles, ngramFrequency.get(s));
				v2 += tf2 * tf2 * w * w;
			}
			double cosine = product / Math.sqrt(v1 * v2);
			if (Double.isNaN(cosine)) return 0;
			return cosine;
		}
	}
	
	public static class FileEntity {
		
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
		public static FileEntity parse(File f, FileType enforceLanguage, int N) {
			String path = f.getAbsolutePath();
			FileType type = enforceLanguage != null ? enforceLanguage : FileType.getFileTypeFromName(path);
			if (f.canRead() && FileType.isSupported(type)) {
				try {
					byte[] content = Files.readAllBytes(f.toPath());
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
		
		public String getLanguageName() {
			return type.name();
		}
		
		public boolean isSameLanguage(FileEntity another) { 
			return this.type == another.type;
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


	}

}

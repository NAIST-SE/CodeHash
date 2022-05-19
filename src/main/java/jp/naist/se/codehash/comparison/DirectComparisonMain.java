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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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

	private static final int MAX_METRICS = 12;

	private static String LANG_OPTION = "-lang:";
	private static String NGRAM_OPTION = "-n:";
	private static String THRESHOLD = "-th:";
	private static String THRESHOLD_NORMALIZED_JACCARD = "-thnj:";
	private static String THRESHOLD_ESTIMATED_NORMALIZED_JACCARD = "-thenj:";

	/**
	 * A file name filter to select files for comparison
	 * (other files are included only for IDF)
	 */
	private static String FILENAME_SELECTOR = "-prefix:";

	private static String DIR_OPTION = "-dir";
	private static String DEFAULT_GROUP = "<default>";

	private static String COMPARE_CRSOS_GROUP = "-compare:crossgroup";
	private static String METRICS = "-metrics:";

	private static final String METRIC_JACCARD_DISTANCE_WITHOUT_NORMALIZATION = "exact-jaccard";
	private static final String METRIC_JACCARD_DISTANCE = "jaccard";
	private static final String METRIC_OVERLAP_COEFFICIENT = "overlap-coefficient";
	private static final String METRIC_OVERLAP_COEFFICIENT_WITHOUT_NORMALIZATION = "exact-overlap-coefficient";
	private static final String METRIC_OVERLAP_SIMILARITY = "overlap-similarity";
	private static final String METRIC_OVERLAP_SIMILARITY_WITHOUT_NORMALIZATION = "exact-overlap-similarity";
	
	
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
	private double thresholdNormalizedJaccard = -1;
	private double thresholdEstimatedNormalizedJaccard = -1;
	private HashMap<String, FileGroup> groups = new HashMap<>();
	private boolean compareGroups = false;

	private boolean useExactJaccard = true;
	private boolean useExactOverlapSimilarity = true;
	private boolean useExactOverlapCoefficient = true;
	private boolean useJaccard = true;
	private boolean useOverlapSimilarity = true;
	private boolean useOverlapCoefficient = true;

	public DirectComparisonMain(String[] args) {
		FileGroup defaultGroup = null;
		
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
			} else if (s.startsWith(THRESHOLD_ESTIMATED_NORMALIZED_JACCARD)) {
				thresholdEstimatedNormalizedJaccard = parseThreshold(THRESHOLD_ESTIMATED_NORMALIZED_JACCARD, s);
				if (Double.isNaN(thresholdEstimatedNormalizedJaccard)) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(THRESHOLD_NORMALIZED_JACCARD)) {
				thresholdNormalizedJaccard = parseThreshold(THRESHOLD_NORMALIZED_JACCARD, s);
				if (Double.isNaN(thresholdNormalizedJaccard)) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(THRESHOLD)) {
				threshold = parseThreshold(THRESHOLD, s);
				if (Double.isNaN(threshold)) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(METRICS)) {
				HashSet<String> metricNames = new HashSet<>(Arrays.asList(s.substring(METRICS.length()).split(",")));
				useExactJaccard = metricNames.contains(METRIC_JACCARD_DISTANCE_WITHOUT_NORMALIZATION);
				useExactOverlapCoefficient = metricNames.contains(METRIC_OVERLAP_COEFFICIENT_WITHOUT_NORMALIZATION);
				useExactOverlapSimilarity = metricNames.contains(METRIC_OVERLAP_SIMILARITY_WITHOUT_NORMALIZATION);
				useJaccard = metricNames.contains(METRIC_JACCARD_DISTANCE);
				useOverlapCoefficient = metricNames.contains(METRIC_OVERLAP_COEFFICIENT);
				useOverlapSimilarity = metricNames.contains(METRIC_OVERLAP_SIMILARITY);
			} else if (s.equals(COMPARE_CRSOS_GROUP)) {
				compareGroups = true;
//			} else if (s.startsWith(FILENAME_SELECTOR)) {
//				filePrefix = s.substring(FILENAME_SELECTOR.length());
			} else if (s.startsWith(DIR_OPTION)) {
				String dirname = s.substring(DIR_OPTION.length());
				int index = dirname.indexOf(':');
				if (index >= 0) {
					String groupId;
					if (index == 0) {
						groupId = DEFAULT_GROUP;
					} else {
						groupId = dirname.substring(0, index);
					}
					dirname = dirname.substring(index+1);

					FileGroup g = groups.get(groupId);
					if (g == null) {
						g = new FileGroup(groupId);
						groups.put(groupId, g);
					}
					g.addDirectory(dirname);
				} // else invalid dir option
			} else {
				if (defaultGroup == null) {
					defaultGroup = new FileGroup(DEFAULT_GROUP);
					groups.put(DEFAULT_GROUP, defaultGroup);
					
				}
 				defaultGroup.addFile(s);
			}
		}
//		
//		
//		
//		for (String s: filenames) {
//			File f = new File(s);
//			FileEntity entity = FileEntity.parse(f, t, N);
//			if (entity != null) {
//				if (filePrefix == null || s.startsWith(filePrefix)) {
//					files.add(entity);
//				}
//				idfFiles.add(entity);
//			}
//		}
//		if (files.size() <= 1) {
//			System.err.println("Arguments: Two or more source file names should be specified.");
//			invalid = true;
//			return;
//		}
	}
	
	public void run() {
		if (invalid) return;
		
		// Load files
		for (FileGroup g: groups.values()) {
			g.loadEntities(t, N);
		}
		
//		// Count the number of Ngrams
//		StringMultiset normalizedNgramFrequencyInSelectedFiles = new StringMultiset(1024);
//		StringMultiset normalizedNgramFrequencyInAllFiles = new StringMultiset(1024);
//		for (FileEntity f: idfFiles) {
//			for (String s: f.getNormalizedNgramMultiset().keySet()) {
//				normalizedNgramFrequencyInAllFiles.add(s);
//			}
//		}
//		for (FileEntity f: files) {
//			for (String s: f.getNormalizedNgramMultiset().keySet()) {
//				normalizedNgramFrequencyInSelectedFiles.add(s);
//			}
//		}

		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(System.out)) {
			gen.useDefaultPrettyPrinter();
			gen.writeStartObject();
			
			// Print a file list
			gen.writeArrayFieldStart("Files");
			
			for (FileGroup g: groups.values()) {
				for (FileEntity e1: g.getFiles()) {
					gen.writeStartObject();
					gen.writeStringField("group", g.getGroupId());
					gen.writeNumberField("index", e1.index);
					gen.writeStringField("path", e1.path);
					gen.writeStringField("lang", e1.getLanguageName());
					gen.writeStringField("byte-sha1", e1.filehash);
					gen.writeStringField("token-sha1", e1.codehash);
					gen.writeNumberField("byte-length", e1.byteLength);
					gen.writeNumberField("token-length", e1.tokenLength);
					gen.writeNumberField("ngram-count", e1.ngramCount);
					gen.writeEndObject();
				}
			}
			gen.writeEndArray();

			gen.writeArrayFieldStart("Pairs");

			if (compareGroups) {
				// Compare across groups
				ArrayList<FileGroup> groupList = new ArrayList<>(groups.values());
				
				for (int i=0; i<groupList.size(); i++) {
					for (int j=i+1; j<groupList.size(); j++) {
						
						ArrayList<FileEntity> files1 = groupList.get(i).getFiles();
						ArrayList<FileEntity> files2 = groupList.get(j).getFiles();
						
						for (FileEntity e1: files1) {
							for (FileEntity e2: files2) {
								compare(gen, e1, e2);
							}
						}
						
					}
				}
				
			} else {
				// Compare within groups
				for (FileGroup g: groups.values()) {
					ArrayList<FileEntity> files = g.getFiles();
					for (int i=0; i<files.size(); i++) {
						FileEntity e1 = files.get(i);
						for (int j=i+1; j<files.size(); j++) {
							FileEntity e2 = files.get(j);
							compare(gen, e1, e2);
						}
					}
				}
			}
			
			gen.writeEndArray();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private void compare(JsonGenerator gen, FileEntity e1, FileEntity e2) throws IOException {
		// Compare them if they are written in the same language
		if (e1.isSameLanguage(e2)) {
			// skip actual calculation if estimated similarity is low
			if (thresholdEstimatedNormalizedJaccard > 0 && e1.minhashEntry.estimateNormalizedSimilarity(e2.minhashEntry) < thresholdEstimatedNormalizedJaccard) return;
			
			SimilarityRecord similarityValues = calculateSimilarity(e1, e2, null, null);
			//SimilarityRecord similarityValues = calculateSimilarity(e1, e2, normalizedNgramFrequencyInAllFiles, normalizedNgramFrequencyInSelectedFiles);
			if (similarityValues.isLessThan(threshold)) return;

			// skip actual similarity is lower than threshold
			if (thresholdNormalizedJaccard > 0 && similarityValues.getValue("jaccard") < thresholdNormalizedJaccard) return;

			gen.writeStartObject();
			gen.writeNumberField("index1", e1.index);
			gen.writeNumberField("index2", e2.index);
			similarityValues.writeSimilarity(gen);
			gen.writeEndObject();
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
		if (useExactJaccard) {
			double jaccard = intersection * 1.0 / (e1.ngramCount + e2.ngramCount - intersection);
			sim.add(METRIC_JACCARD_DISTANCE_WITHOUT_NORMALIZATION, jaccard);
		}
		if (useExactOverlapCoefficient) {
			double exactOverlapCoefficient = intersection * 1.0 / Math.min(e1.ngramCount, e2.ngramCount);
			sim.add(METRIC_OVERLAP_COEFFICIENT_WITHOUT_NORMALIZATION, exactOverlapCoefficient);
		}
		if (useExactOverlapSimilarity) {
			double exactOverlapSimilarity = intersection * 1.0 / Math.max(e1.ngramCount, e2.ngramCount);
			sim.add(METRIC_OVERLAP_SIMILARITY_WITHOUT_NORMALIZATION, exactOverlapSimilarity);
		}

		intersection = e1.normalizedNgrams.intersection(e2.normalizedNgrams);
		if (useJaccard) {
			double normalizedJaccard = intersection * 1.0 / (e1.ngramCount + e2.ngramCount - intersection);
			sim.add(METRIC_JACCARD_DISTANCE, normalizedJaccard);
		}
		if (useOverlapCoefficient) {
			double overlap = intersection * 1.0 / Math.min(e1.ngramCount, e2.ngramCount);
			sim.add(METRIC_OVERLAP_COEFFICIENT, overlap);
		}
		if (useOverlapSimilarity) {
			double overlapSimialrity = intersection * 1.0 / Math.max(e1.ngramCount, e2.ngramCount);
			sim.add(METRIC_OVERLAP_SIMILARITY, overlapSimialrity);
		}

		// Temporarily disabled 
//		double v = getWeightedJaccard(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 0);
//		sim.add("w-all-jaccard", v);
//
//		double v5 = getWeightedOverlap(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 0);
//		sim.add("w-all-overlap", v5);
//
//		double v2 = getWeightedJaccard(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 1);
//		sim.add("i-all-jaccard", v2);
//
//		double v6 = getWeightedOverlap(e1.normalizedNgrams, e2.normalizedNgrams, frequencyInAllFiles, idfFiles.size(), 1);
//		sim.add("i-all-overlap", v6);
//
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



//		double cosineN = CosineSimilarity.getTFIDFCosineSimilarity(e1.getNormalizedNgramMultiset(), e2.getNormalizedNgramMultiset(), frequencyInAllFiles, idfFiles.size());
//		sim.add("tfidf-all-cosine", cosineN);
//
//		double cosineNIDF = CosineSimilarity.getIDFCosineSimilarity(e1.getNormalizedNgramMultiset(), e2.getNormalizedNgramMultiset(), frequencyInAllFiles, idfFiles.size());
//		sim.add("idf-all-cosine", cosineNIDF);

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
		
		public double getValue(String name) {
			for (int i=0; i<index; i++) {
				if (names[i].equals(name)) {
					return values[i];
				}
			}
			return Double.NaN;
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
	
	public static class FileGroup {
		
		private String groupId;
		private ArrayList<Path> filePaths;
		private ArrayList<FileEntity> fileEntities;
		
		public FileGroup(String groupId) {
			this.groupId = groupId;
			this.filePaths = new ArrayList<>();
		}
		
		public String getGroupId() {
			return groupId;
		}
		
		public void addFile(String filename) {
			filePaths.add(new File(filename).toPath());
		}
		
		public void addDirectory(String dirname) {
			try {
				Files.walkFileTree(new File(dirname).toPath(), new FileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						filePaths.add(file);
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
			
		}
		
		public void loadEntities(FileType enforceLanguage, int N) {
			this.fileEntities = new ArrayList<>(filePaths.size()); 
			for (Path path: filePaths) {
				FileEntity e = FileEntity.parse(path, enforceLanguage, N);
				if (e != null) {
					fileEntities.add(e);
				}
			}
		}
		
		public ArrayList<FileEntity> getFiles() {
			return fileEntities;
		}
		
	}
	
	public static class FileEntity {
		
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

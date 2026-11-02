package jp.naist.se.codehash.comparison;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.FileCodeHash;
import jp.naist.se.codehash.similarity.SimilarityMetrics;

public class ComparisonMain {

	/**
	 * The default threshold of similarity.
	 */
	public static final double DEFAULT_THRESHOLD = 0.7;

	/**
	 * A file pair is reported if its estimated similarity with normalization is greater than or equal to this threshold, e.g. "-th:0.8".
	 * The option name is the same as {@link DirectComparisonMain}.
	 */
	private static final String THRESHOLD = "-th:";

	/**
	 * This option writes the result in the JSON format of {@link DirectComparisonMain}.
	 */
	public static final String JSON_OPTION = "-json";

	/**
	 * The value of the "Similarity" attribute in the JSON output.
	 * It indicates that the similarity values are estimated by 1-bit minhash vectors.
	 */
	public static final String JSON_SIMILARITY_ESTIMATED = "estimated-by-minhash";

	/**
	 * Compare minhash in given files
	 * @param args specify a list of file names.  "-th:[THRESHOLD]" option specifies a threshold (0.7 by default).
	 * "-compare:crossgroup" option regards each file as a group and compares only file pairs across groups.
	 * "-json" option writes the result in the JSON format of {@link DirectComparisonMain}.
	 */
	public static void main(String[] args) {
		double threshold = DEFAULT_THRESHOLD;
		boolean compareGroups = false;
		boolean json = false;
		ArrayList<File> files = new ArrayList<>();
		for (String arg: args) {
			if (arg.startsWith(THRESHOLD)) {
				threshold = DirectComparisonMain.parseThreshold(THRESHOLD, arg);
				if (Double.isNaN(threshold)) return;
			} else if (arg.equals(DirectComparisonMain.COMPARE_CROSS_GROUP)) {
				compareGroups = true;
			} else if (arg.equals(JSON_OPTION)) {
				json = true;
			} else {
				files.add(new File(arg));
			}
		}
		if (compareGroups && files.size() < 2) {
			System.err.println("Warning: " + DirectComparisonMain.COMPARE_CROSS_GROUP + " requires two or more files.");
		}

		// Load entries ignoring same codehash files (in each group)
		ComparisonMain main = new ComparisonMain(threshold, compareGroups);
		for (int i=0; i<files.size(); i++) {
			File f = files.get(i);
			if (f.exists() && f.isFile() && f.canRead()) {
				main.read(f, compareGroups ? i : 0);
			}
		}

		System.err.println(main.getEntryCount() + " unique files found.");
		if (json) {
			main.analyzeJson(System.out);
		} else {
			main.analyze(System.out);
		}
	}

	/**
	 * A file listed in an input file.
	 * Files having the same codehash in a group share a single {@link MinHashEntry}.
	 */
	private static class FileRecord {
		private int index;
		private int group;
		private String filename;
		private String sha1;
		private String lang;
		private String codehash;
		private long fileLength;
		private int tokenLength;
		private int ngramCount;
	}

	/**
	 * A callback to receive a file pair whose estimated similarity is greater than or equal to the threshold.
	 */
	private interface PairVisitor {
		/**
		 * @param first is a file in the former group.
		 * @param second is the other file.
		 */
		void visit(MinHashEntry first, MinHashEntry second, double estimated, double normalizedEstimated) throws IOException;
	}

	private double threshold;
	private boolean compareGroups;
	private ArrayList<MinHashEntry> entries = new ArrayList<>();
	private ArrayList<FileRecord> files = new ArrayList<>();
	private HashMap<String, ArrayList<FileRecord>> codehashToFiles = new HashMap<>();
	private HashMap<Integer, String> groupNames = new HashMap<>();
	private HashMap<String, String> languages = new HashMap<>(32);

	public ComparisonMain() {
		this(DEFAULT_THRESHOLD);
	}

	/**
	 * @param threshold specifies the minimum estimated similarity with normalization to report a file pair.
	 */
	public ComparisonMain(double threshold) {
		this(threshold, false);
	}

	/**
	 * @param threshold specifies the minimum estimated similarity with normalization to report a file pair.
	 * @param compareGroups If true, only file pairs across groups are compared.
	 * Files having the same codehash are regarded as the same file only within a group.
	 */
	public ComparisonMain(double threshold, boolean compareGroups) {
		this.threshold = threshold;
		this.compareGroups = compareGroups;
	}

	public int getEntryCount() {
		return entries.size();
	}

	/**
	 * Replace a language string into a common string for faster comparison
	 * @param lang
	 * @return
	 */
	public String toSingleInstance(String lang) {
		String langCommonInstance = languages.get(lang);
		if (langCommonInstance == null) {
			languages.put(lang, lang);
			langCommonInstance = lang;
		}
		return langCommonInstance;
	}

	/**
	 * Read a hash list file into the default group
	 * @param f specifies a file
	 */
	public void read(File f) {
		read(f, 0);
	}

	/**
	 * Read a hash list file
	 * @param f specifies a file
	 * @param group specifies a group of the files listed in f
	 */
	public void read(File f, int group) {
		if (compareGroups) {
			groupNames.putIfAbsent(group, f.getPath());
		}
		try (LineNumberReader reader = new LineNumberReader(new FileReader(f))) {
			int skipped = 0;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				// Header lines may appear in the middle of a file concatenated from multiple files
				if (line.equals(FileCodeHash.HEADER)) continue;

				String[] tokens = line.split("\t");
				// Skip a line that is not recognized as data,
				// e.g. a line without 1-bit minhash vectors (GitCodeHash in codehash or sha1minhash mode)
				if (tokens.length != 9 || !isHex(tokens[4]) || !isHex(tokens[5])) {
					skipped++;
					continue;
				}
				String filename = tokens[0];
				String sha1 = tokens[1];
				String lang = toSingleInstance(tokens[2]);
				String codehash = tokens[3];
				String minhash = tokens[4];
				String normalizedMinhash = tokens[5];
				int fileLength;
				int tokenLength;
				int ngramSize;
				try {
					fileLength = Integer.parseInt(tokens[6]);
					tokenLength = Integer.parseInt(tokens[7]);
					ngramSize = Integer.parseInt(tokens[8]);
				} catch (NumberFormatException e) {
					skipped++;
					continue;
				}

				FileRecord r = new FileRecord();
				r.index = files.size();
				r.group = group;
				r.filename = filename;
				r.sha1 = sha1;
				r.lang = lang;
				r.codehash = codehash;
				r.fileLength = fileLength;
				r.tokenLength = tokenLength;
				r.ngramCount = ngramSize;
				files.add(r);

				String key = getKey(group, codehash);
				ArrayList<FileRecord> sameFiles = codehashToFiles.get(key);
				if (sameFiles == null) {
					entries.add(new MinHashEntry(filename, sha1, lang, codehash, minhash, normalizedMinhash, fileLength, tokenLength, ngramSize, group));
					sameFiles = new ArrayList<>();
					codehashToFiles.put(key, sameFiles);
				}
				sameFiles.add(r);
			}
			if (skipped > 0) {
				System.err.println(skipped + " lines without valid minhash vectors are skipped in " + f.getAbsolutePath());
			}
		} catch (IOException e) {
			System.err.println("Failed to read " + f.getAbsolutePath());
		}
	}

	/**
	 * @return true if s is a non-empty hexadecimal string representing a byte array.
	 */
	private static boolean isHex(String s) {
		if (s.isEmpty() || s.length() % 2 != 0) return false;
		for (int i=0; i<s.length(); i++) {
			if (Character.digit(s.charAt(i), 16) < 0) return false;
		}
		return true;
	}

	/**
	 * Write file pairs in TSV.  Files having the same codehash in a group are written in a single line.
	 */
	public void analyze(PrintStream out) {
		out.println("CodeHash1\tCodeHash2\tTokenLength1\tTokenLength2\tEstimatedSim\tEstimatedSimWithNormalization\tFileNames1\tFileNames2");
		try {
			compare((first, second, estimated, normalizedEstimated) -> {
				StringBuilder buf = new StringBuilder(1024);
				buf.append(first.getCodehash());
				buf.append("\t");
				buf.append(second.getCodehash());
				buf.append("\t");
				buf.append(first.getTokenLength());
				buf.append("\t");
				buf.append(second.getTokenLength());
				buf.append("\t");
				buf.append(estimated);
				buf.append("\t");
				buf.append(normalizedEstimated);
				buf.append("\t");
				buf.append(getFileNames(first));
				buf.append("\t");
				buf.append(getFileNames(second));
				out.println(buf.toString());
			});
		} catch (IOException e) {
			// PrintStream does not throw IOException
			e.printStackTrace();
		}
	}

	/**
	 * Write files and file pairs in the JSON format of {@link DirectComparisonMain}.
	 * The first attribute "Similarity" indicates that the values are estimated by minhash.
	 * Each file listed in the input files is an element of "Files".
	 * Each pair of files is an element of "Pairs"; estimated similarity values are written as "exact-jaccard" and "jaccard".
	 * Files having the same codehash in a group are reported as pairs whose estimated similarity is 1.0,
	 * unless the -compare:crossgroup option is specified.
	 */
	public void analyzeJson(PrintStream out) {
		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(out)) {
			gen.useDefaultPrettyPrinter();
			gen.writeStartObject();
			gen.writeStringField("Similarity", JSON_SIMILARITY_ESTIMATED);

			gen.writeArrayFieldStart("Files");
			for (FileRecord r: files) {
				gen.writeStartObject();
				gen.writeStringField("group", groupNames.getOrDefault(r.group, DirectComparisonMain.DEFAULT_GROUP));
				gen.writeNumberField("index", r.index);
				gen.writeStringField("path", r.filename);
				gen.writeStringField("lang", r.lang);
				gen.writeStringField("byte-sha1", r.sha1);
				gen.writeStringField("token-sha1", r.codehash);
				gen.writeNumberField("byte-length", r.fileLength);
				gen.writeNumberField("token-length", r.tokenLength);
				gen.writeNumberField("ngram-count", r.ngramCount);
				gen.writeEndObject();
			}
			gen.writeEndArray();

			gen.writeArrayFieldStart("Pairs");
			if (!compareGroups) {
				// Files having the same codehash are identical in terms of tokens
				for (MinHashEntry e: entries) {
					ArrayList<FileRecord> sameFiles = getFiles(e);
					for (int i=0; i<sameFiles.size(); i++) {
						for (int j=i+1; j<sameFiles.size(); j++) {
							writePair(gen, sameFiles.get(i), sameFiles.get(j), 1.0, 1.0);
						}
					}
				}
			}
			compare((first, second, estimated, normalizedEstimated) -> {
				for (FileRecord r1: getFiles(first)) {
					for (FileRecord r2: getFiles(second)) {
						writePair(gen, r1, r2, estimated, normalizedEstimated);
					}
				}
			});
			gen.writeEndArray();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write a pair as an element of "Pairs".  A smaller index is written as "index1".
	 */
	private static void writePair(JsonGenerator gen, FileRecord r1, FileRecord r2, double estimated, double normalizedEstimated) throws IOException {
		gen.writeStartObject();
		gen.writeNumberField("index1", Math.min(r1.index, r2.index));
		gen.writeNumberField("index2", Math.max(r1.index, r2.index));
		gen.writeNumberField(SimilarityMetrics.EXACT_JACCARD, estimated);
		gen.writeNumberField(SimilarityMetrics.JACCARD, normalizedEstimated);
		gen.writeEndObject();
	}

	/**
	 * Compare entries and pass file pairs satisfying the threshold to the visitor.
	 */
	private void compare(PairVisitor visitor) throws IOException {
		// Sort for efficient comparison
		entries.sort(new Comparator<MinHashEntry>() {
			@Override
			public int compare(MinHashEntry o1, MinHashEntry o2) {
				return o1.compareTo(o2);
			}
		});

		// Compare all pairs
		for (int i=0; i<entries.size(); i++) {
			MinHashEntry e1 = entries.get(i);
			for (int j=i+1; j<entries.size(); j++) {
				MinHashEntry e2 = entries.get(j);
				if (e1.getMaxSimilarity(e2) < threshold) break;
				if (compareGroups && e1.getGroup() == e2.getGroup()) continue;
				if (!e1.isSameLanguage(e2)) continue;
				double normalizedEstimated = e1.estimateNormalizedSimilarity(e2);
				// Check only normalized estimated because normalized similarity is always higher than exact similarity
				// (though there exists a risk that the estimated value is lower than actual)
				if (normalizedEstimated >= threshold) {
					double estimated = e1.estimateSimilarity(e2);
					// Write a file in the former group first
					MinHashEntry first = e1.getGroup() <= e2.getGroup() ? e1 : e2;
					MinHashEntry second = first == e1 ? e2 : e1;
					visitor.visit(first, second, estimated, normalizedEstimated);
				}
			}
		}
	}

	/**
	 * @return a key to identify files having the same codehash in a group.
	 */
	private static String getKey(int group, String codehash) {
		return group + "\t" + codehash;
	}

	/**
	 * @return files having the same codehash as e in the same group, in the order of index.
	 */
	private ArrayList<FileRecord> getFiles(MinHashEntry e) {
		return codehashToFiles.get(getKey(e.getGroup(), e.getCodehash()));
	}

	/**
	 * @return file names having the same codehash as e in the same group.
	 */
	private String getFileNames(MinHashEntry e) {
		ArrayList<String> filenames = new ArrayList<>();
		for (FileRecord r: getFiles(e)) {
			filenames.add(r.filename);
		}
		Collections.sort(filenames);
		return String.join(",", filenames);
	}

}

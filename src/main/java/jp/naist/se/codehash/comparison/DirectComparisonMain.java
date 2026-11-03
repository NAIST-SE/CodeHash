package jp.naist.se.codehash.comparison;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.similarity.FilePair;
import jp.naist.se.codehash.similarity.SimilarityFilter;
import jp.naist.se.codehash.similarity.SimilarityMetric;
import jp.naist.se.codehash.similarity.SimilarityMetrics;
import jp.naist.se.codehash.similarity.SimilarityRecord;
import jp.naist.se.codehash.file.FileEntity;
import jp.naist.se.codehash.file.FileGroup;
import jp.naist.se.codehash.hash.MinHashOptions;
import jp.naist.se.codehash.util.FutureUtil;
import jp.naist.se.codehash.util.StringDictionary;

/**
 * Compares source files using N-gram multisets of tokens and writes similarity values in JSON to STDOUT.
 * Command line arguments are options and names of source files (or directories).
 * An argument that is not an option is added to the default file group.
 * 
 * Only files in the same language are compared.
 * The output is a JSON object with two arrays: "Files" lists the files with their indices and attributes,
 * and "Pairs" lists the compared file pairs ("index1" and "index2") with the values of the selected metrics.
 * See README.md for the details of the output format.
 */
public class DirectComparisonMain {

	/**
	 * An option to select a programming language.  A language name must follow the option string, e.g. "-lang:JAVA".
	 */
	private static String LANG_OPTION = "-lang:";
	
	/**
	 * A file pair is reported if any of the selected metrics is greater than or equal to this threshold.
	 */
	private static String THRESHOLD = "-th:";

	/**
	 * A file pair is reported if its jaccard is greater than or equal to this threshold.
	 * This is equivalent to "-filter:jaccard>=[THRESHOLD]".
	 */
	private static String THRESHOLD_NORMALIZED_JACCARD = "-thnj:";

	/**
	 * A file pair is compared only if its jaccard estimated by minhash is greater than or equal to this threshold.
	 * Minhash vectors of normalized N-grams are computed only if this option is specified.
	 */
	private static String THRESHOLD_ESTIMATED_NORMALIZED_JACCARD = "-thenj:";

	/**
	 * A file pair is compared only if its exact-jaccard estimated by minhash is greater than or equal to this threshold.
	 * Minhash vectors of N-grams (without normalization) are computed only if this option is specified.
	 */
	private static String THRESHOLD_ESTIMATED_JACCARD = "-thej:";

	/**
	 * An option to specify a condition to report a file pair, e.g. "-filter:jaccard>=0.7 or overlap-coefficient>=0.9".
	 * See {@link SimilarityFilter} for the syntax.
	 * If multiple filters are specified, a file pair must satisfy all of them.
	 */
	private static String FILTER_OPTION = "-filter:";

	/**
	 * This is to specify a file group. 
	 */
	private static String GROUP_OPTION = "-group";

	/**
	 * It specifies a directory.  This is an alias of -group.
	 */
	private static String DIR_OPTION = "-dir";

	/**
	 * The default group name.  This is shared with {@link ComparisonMain}.
	 */
	static final String DEFAULT_GROUP = "<default>";

	/**
	 * This option enables to compare files across groups.
	 * This option is shared with {@link ComparisonMain}.
	 */
	static final String COMPARE_CROSS_GROUP = "-compare:crossgroup";
	
	/**
	 * This option is to select metrics to be calculated.
	 * A list of metric names must be separated commas, e.g. "-metrics:jaccard,overlap-coefficient".
	 */
	private static String METRICS = "-metrics:";

	/**
	 * This option specifies the number of threads to load and compare files, e.g. "-threads:4".
	 * The default is the number of available processors.
	 * "-threads:1" processes files in the main thread.
	 */
	private static String THREADS_OPTION = "-threads:";

	/**
	 * The number of rows (a file and files compared with it) processed in parallel per thread.
	 * It limits the number of results kept in memory before they are written.
	 */
	private static final int ROWS_PER_THREAD = 4;

	
	/**
	 * Compare source files and write the result to STDOUT.
	 * @param args specifies options and names of source files (or directories).  See the class description for the details.
	 */
	public static void main(String[] args) {
		DirectComparisonMain main = new DirectComparisonMain(args);
		main.run();
	}
	

	private boolean invalid = false;

	/**
	 * A programming language to process source files.
	 * If none is selected, the tool extracts a language from a file extension. 
	 */
	private FileType selectedLanguage = null;
	
	private int N = MinHashOptions.DEFAULT_NGRAM_SIZE;
	private double threshold = 0;
	private double thresholdNormalizedJaccard = -1;
	private double thresholdEstimatedNormalizedJaccard = -1;
	private double thresholdEstimatedJaccard = -1;
	private int threads = Runtime.getRuntime().availableProcessors();
	private SimilarityMetric jaccard = SimilarityMetrics.get(SimilarityMetrics.JACCARD);
	private HashMap<String, FileGroup> groups = new HashMap<>();
	
	/**
	 * If true, the program compares files across groups but skip comparison within a file group.
	 * If false, the program compares files within a group but does not compare files across groups.
	 */
	private boolean compareGroups = false;

	/**
	 * Metrics written to the output.
	 */
	private ArrayList<SimilarityMetric> metrics = new ArrayList<>(SimilarityMetrics.getAll());

	/**
	 * Conditions specified by -filter options.  A file pair must satisfy all of them.
	 */
	private ArrayList<Predicate<SimilarityRecord>> filters = new ArrayList<>();

	/**
	 * Construct an object from command line arguments.
	 * An argument without a special option is regarded as a source file or a directory 
	 * @param args
	 */
	public DirectComparisonMain(String[] args) {
		FileGroup defaultGroup = null;
		
		for (String s: args) {
			if (s.startsWith(LANG_OPTION)) {
				selectedLanguage = FileType.getFileType(s.substring(LANG_OPTION.length()));
			} else if (s.startsWith(MinHashOptions.NGRAM_OPTION)) {
				N = MinHashOptions.parseNgramSize(s);
				if (N < 0) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(THRESHOLD_ESTIMATED_NORMALIZED_JACCARD)) {
				thresholdEstimatedNormalizedJaccard = parseThreshold(THRESHOLD_ESTIMATED_NORMALIZED_JACCARD, s);
				if (Double.isNaN(thresholdEstimatedNormalizedJaccard)) {
					invalid = true;
					return;
				}
			} else if (s.startsWith(THRESHOLD_ESTIMATED_JACCARD)) {
				thresholdEstimatedJaccard = parseThreshold(THRESHOLD_ESTIMATED_JACCARD, s);
				if (Double.isNaN(thresholdEstimatedJaccard)) {
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
				for (String name: metricNames) {
					if (SimilarityMetrics.get(name) == null) {
						System.err.println("Unknown metric: " + name + " (available: " + String.join(",", SimilarityMetrics.getNames()) + ")");
						invalid = true;
						return;
					}
				}
				metrics.clear();
				for (SimilarityMetric m: SimilarityMetrics.getAll()) {
					if (metricNames.contains(m.getName())) metrics.add(m);
				}
			} else if (s.startsWith(FILTER_OPTION)) {
				try {
					filters.add(SimilarityFilter.parse(s.substring(FILTER_OPTION.length())));
				} catch (IllegalArgumentException e) {
					System.err.println("Invalid filter: " + e.getMessage());
					invalid = true;
					return;
				}
			} else if (s.startsWith(THREADS_OPTION)) {
				String nString = s.substring(THREADS_OPTION.length());
				try {
					threads = Integer.parseInt(nString);
				} catch (NumberFormatException e) {
					threads = 0;
				}
				if (threads < 1) {
					System.err.println("Invalid number of threads: " + nString);
					invalid = true;
					return;
				}
			} else if (s.equals(COMPARE_CROSS_GROUP)) {
				compareGroups = true;
			} else if (s.startsWith(DIR_OPTION) || s.startsWith(GROUP_OPTION)) {
				// Add a specified path (file or directory) to the specified group  
				String path;
				if (s.startsWith(DIR_OPTION)) path = s.substring(DIR_OPTION.length());
				else path = s.substring(GROUP_OPTION.length());
				
				int index = path.indexOf(':');
				if (index >= 0) {
					String groupId;
					if (index == 0) {
						groupId = DEFAULT_GROUP;
					} else {
						groupId = path.substring(0, index);
					}
					path = path.substring(index+1);

					FileGroup g = groups.get(groupId);
					if (g == null) {
						g = new FileGroup(groupId);
						groups.put(groupId, g);
					}
					g.add(path);
				} // else invalid dir/group option
			} else {
				// Add a file (or a directory) to the default file group
				if (defaultGroup == null) {
					defaultGroup = new FileGroup(DEFAULT_GROUP);
					groups.put(DEFAULT_GROUP, defaultGroup);
					
				}
 				defaultGroup.add(s);
			}
		}
	}
	
	public void run() {
		if (invalid) return;

		// Files are processed in the main thread if a single thread is specified
		ExecutorService executor = threads > 1 ? Executors.newFixedThreadPool(threads) : null;
		try {
			run(executor);
		} finally {
			if (executor != null) executor.shutdownNow();
		}
	}

	/**
	 * Load and compare files.
	 * @param executor executes tasks in parallel.  If null, tasks are executed in the main thread.
	 */
	private void run(ExecutorService executor) {
		// Load files.  Minhash vectors are computed only if they are used to skip comparison.
		StringDictionary dictionary = new StringDictionary();
		for (FileGroup g: groups.values()) {
			g.loadEntities(selectedLanguage, N, dictionary, thresholdEstimatedJaccard > 0, thresholdEstimatedNormalizedJaccard > 0, executor);
		}

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
					gen.writeNumberField("index", e1.getIndex());
					gen.writeStringField("path", e1.getPath());
					gen.writeStringField("lang", e1.getLanguageName());
					gen.writeStringField("byte-sha1", e1.getFileHash());
					gen.writeStringField("token-sha1", e1.getCodeHash());
					gen.writeNumberField("byte-length", e1.getByteLength());
					gen.writeNumberField("token-length", e1.getTokenLength());
					gen.writeNumberField("ngram-count", e1.getNgramCount());
					gen.writeEndObject();
				}
			}
			gen.writeEndArray();

			gen.writeArrayFieldStart("Pairs");
			compareRows(gen, createRows(), executor);
			gen.writeEndArray();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * A file and files to be compared with it.  It is a unit of parallel comparison.
	 */
	private static class Row {

		private FileEntity e1;
		private List<FileEntity> others;

		public Row(FileEntity e1, List<FileEntity> others) {
			this.e1 = e1;
			this.others = others;
		}
	}

	/**
	 * @return rows of file pairs in the output order.
	 */
	private ArrayList<Row> createRows() {
		ArrayList<Row> rows = new ArrayList<>();
		if (compareGroups) {
			// Compare across groups
			ArrayList<FileGroup> groupList = new ArrayList<>(groups.values());
			for (int i=0; i<groupList.size(); i++) {
				for (int j=i+1; j<groupList.size(); j++) {
					ArrayList<FileEntity> files2 = groupList.get(j).getFiles();
					for (FileEntity e1: groupList.get(i).getFiles()) {
						rows.add(new Row(e1, files2));
					}
				}
			}
		} else {
			// Compare within groups
			for (FileGroup g: groups.values()) {
				ArrayList<FileEntity> files = g.getFiles();
				for (int i=0; i<files.size(); i++) {
					rows.add(new Row(files.get(i), files.subList(i+1, files.size())));
				}
			}
		}
		return rows;
	}

	/**
	 * Compare file pairs and write the results in the order of rows.
	 * Rows are compared in parallel, while the number of rows in progress is limited
	 * so that only the results of a small number of rows are kept in memory.
	 * @param executor executes comparison.  If null, rows are compared in the main thread.
	 */
	private void compareRows(JsonGenerator gen, List<Row> rows, ExecutorService executor) throws IOException {
		if (executor == null) {
			for (Row row: rows) {
				write(gen, compare(row));
			}
			return;
		}

		ArrayDeque<Future<List<SimilarityRecord>>> running = new ArrayDeque<>();
		int next = 0;
		while (next < rows.size() || !running.isEmpty()) {
			while (next < rows.size() && running.size() < threads * ROWS_PER_THREAD) {
				Row row = rows.get(next++);
				running.add(executor.submit(() -> compare(row)));
			}
			write(gen, FutureUtil.get(running.poll()));
		}
	}

	/**
	 * Compare files in a row.  This method may be executed in parallel.
	 * @return the records to be written.
	 */
	private List<SimilarityRecord> compare(Row row) {
		ArrayList<SimilarityRecord> result = new ArrayList<>();
		for (FileEntity e2: row.others) {
			SimilarityRecord r = compare(row.e1, e2);
			if (r != null) result.add(r);
		}
		return result;
	}

	/**
	 * Compare two files.
	 * The values of the selected metrics are calculated in this method, so that writing the result does not calculate them.
	 * @param e1 is the first file, written as "index1".
	 * @param e2 is the second file, written as "index2".
	 * @return the similarity values of the pair.
	 * null if the files are written in different languages,
	 * or the pair is excluded by the thresholds (-thej, -thenj, -th, -thnj) or the filters.
	 */
	private SimilarityRecord compare(FileEntity e1, FileEntity e2) {
		// Compare them if they are written in the same language
		if (!e1.isSameLanguage(e2)) return null;

		// skip actual calculation if estimated similarity is low
		if (thresholdEstimatedJaccard > 0 && e1.estimateSimilarity(e2) < thresholdEstimatedJaccard) return null;
		if (thresholdEstimatedNormalizedJaccard > 0 && e1.estimateNormalizedSimilarity(e2) < thresholdEstimatedNormalizedJaccard) return null;

		SimilarityRecord similarityValues = new SimilarityRecord(new FilePair(e1, e2));
		if (isLessThan(similarityValues, threshold)) return null;

		// skip actual similarity is lower than threshold
		if (thresholdNormalizedJaccard > 0 && similarityValues.getValue(jaccard) < thresholdNormalizedJaccard) return null;

		for (Predicate<SimilarityRecord> filter: filters) {
			if (!filter.test(similarityValues)) return null;
		}

		for (SimilarityMetric m: metrics) {
			similarityValues.getValue(m);
		}
		return similarityValues;
	}

	/**
	 * Write similarity values as elements of "Pairs".
	 * @throws IOException if an error occurred while writing the result.
	 */
	private void write(JsonGenerator gen, List<SimilarityRecord> records) throws IOException {
		for (SimilarityRecord r: records) {
			gen.writeStartObject();
			gen.writeNumberField("index1", r.getPair().getFirst().getIndex());
			gen.writeNumberField("index2", r.getPair().getSecond().getIndex());
			r.writeSimilarity(gen, metrics);
			gen.writeEndObject();
		}
	}

	/**
	 * Parse a threshold option.  This method is shared with {@link ComparisonMain}.
	 * @param key is an option string, e.g. "-th:".
	 * @param value is a command line argument starting with the key.
	 * @return a parsed value.  Double.NaN is returned if an invalid value is specified.
	 */
	static double parseThreshold(String key, String value) {
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
	
	/**
	 * @return true if all the selected metrics are less than the threshold (or NaN).
	 */
	private boolean isLessThan(SimilarityRecord sim, double threshold) {
		for (SimilarityMetric m: metrics) {
			if (sim.getValue(m) >= threshold) return false;
		}
		return true;
	}

}

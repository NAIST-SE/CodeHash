package jp.naist.se.codehash.comparison;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

	private static String THRESHOLD_ESTIMATED_NORMALIZED_JACCARD = "-thenj:";

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
	 * The default group name.
	 */
	private static String DEFAULT_GROUP = "<default>";

	/**
	 * This option enables to compare files across groups.
	 */
	private static String COMPARE_CRSOS_GROUP = "-compare:crossgroup";
	
	/**
	 * This option is to select metrics to be calculated.
	 * A list of metric names must be separated commas, e.g. "-metrics:jaccard,overlap-coefficient".
	 */
	private static String METRICS = "-metrics:";

	
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
			} else if (s.equals(COMPARE_CRSOS_GROUP)) {
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
		
		// Load files
		for (FileGroup g: groups.values()) {
			g.loadEntities(selectedLanguage, N);
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

	/**
	 * Compare two files and write their similarity values as an element of "Pairs".
	 * Nothing is written if the files are written in different languages,
	 * or the pair is excluded by the thresholds (-thenj, -th, -thnj) or the filters.
	 * @param gen is the JSON generator to write the result.
	 * @param e1 is the first file, written as "index1".
	 * @param e2 is the second file, written as "index2".
	 * @throws IOException if an error occurred while writing the result.
	 */
	private void compare(JsonGenerator gen, FileEntity e1, FileEntity e2) throws IOException {
		// Compare them if they are written in the same language
		if (e1.isSameLanguage(e2)) {
			// skip actual calculation if estimated similarity is low
			if (thresholdEstimatedNormalizedJaccard > 0 && e1.estimateNormalizedSimilarity(e2) < thresholdEstimatedNormalizedJaccard) return;
			
			SimilarityRecord similarityValues = new SimilarityRecord(new FilePair(e1, e2));
			if (isLessThan(similarityValues, threshold)) return;

			// skip actual similarity is lower than threshold
			if (thresholdNormalizedJaccard > 0 && similarityValues.getValue(SimilarityMetrics.get(SimilarityMetrics.JACCARD)) < thresholdNormalizedJaccard) return;

			for (Predicate<SimilarityRecord> filter: filters) {
				if (!filter.test(similarityValues)) return;
			}

			gen.writeStartObject();
			gen.writeNumberField("index1", e1.getIndex());
			gen.writeNumberField("index2", e2.getIndex());
			similarityValues.writeSimilarity(gen, metrics);
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

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

import jp.naist.se.codehash.FileCodeHash;

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
	 * Compare minhash in given files
	 * @param args specify a list of file names.  "-th:[THRESHOLD]" option specifies a threshold (0.7 by default).
	 */
	public static void main(String[] args) {
		double threshold = DEFAULT_THRESHOLD;
		ArrayList<File> files = new ArrayList<>();
		for (String arg: args) {
			if (arg.startsWith(THRESHOLD)) {
				threshold = DirectComparisonMain.parseThreshold(THRESHOLD, arg);
				if (Double.isNaN(threshold)) return;
			} else {
				files.add(new File(arg));
			}
		}

		// Load entries ignoring same codehash files
		ComparisonMain main = new ComparisonMain(threshold);
		for (File f: files) {
			if (f.exists() && f.isFile() && f.canRead()) {
				main.read(f);
			}
		}

		System.err.println(main.getEntryCount() + " unique files found.");
		main.analyze(System.out);
	}

	private double threshold;
	private ArrayList<MinHashEntry> entries = new ArrayList<>();
	private HashMap<String, ArrayList<String>> codehashToFileNames = new HashMap<>();
	private HashMap<String, String> languages = new HashMap<>(32);

	public ComparisonMain() {
		this(DEFAULT_THRESHOLD);
	}

	/**
	 * @param threshold specifies the minimum estimated similarity with normalization to report a file pair.
	 */
	public ComparisonMain(double threshold) {
		this.threshold = threshold;
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
	 * Read a hash list file  
	 * @param f specifies a file
	 */
	public void read(File f) {
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
				String lang = tokens[2];
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

				if (!codehashToFileNames.containsKey(codehash)) {
					entries.add(new MinHashEntry(filename, sha1, toSingleInstance(lang), codehash, minhash, normalizedMinhash, fileLength, tokenLength, ngramSize));
					ArrayList<String> filenames = new ArrayList<>();
					filenames.add(filename);
					codehashToFileNames.put(codehash, filenames);
				} else {
					ArrayList<String> filenames = codehashToFileNames.get(codehash);
					filenames.add(filename);
				}
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

	public void analyze(PrintStream out) {
		// Sort for efficient comparison
		entries.sort(new Comparator<MinHashEntry>() {
			@Override
			public int compare(MinHashEntry o1, MinHashEntry o2) {
				return o1.compareTo(o2);
			}
		});

		out.println("CodeHash1\tCodeHash2\tTokenLength1\tTokenLength2\tEstimatedSim\tEstimatedSimWithNormalization\tFileNames1\tFileNames2");
		// Compare all pairs
		for (int i=0; i<entries.size(); i++) {
			MinHashEntry e1 = entries.get(i);
			for (int j=i+1; j<entries.size(); j++) {
				MinHashEntry e2 = entries.get(j);
				if (e1.getMaxSimilarity(e2) < threshold) break;
				if (!e1.isSameLanguage(e2)) continue;
				double normalizedEstimated = e1.estimateNormalizedSimilarity(e2);
				// Check only normalized estimated because normalized similarity is always higher than exact similarity 
				// (though there exists a risk that the estimated value is lower than actual)
				if (normalizedEstimated >= threshold) {
					double estimated = e1.estimateSimilarity(e2); 
					StringBuilder buf = new StringBuilder(1024);
					buf.append(e1.getCodehash());
					buf.append("\t");
					buf.append(e2.getCodehash());
					buf.append("\t");
					buf.append(e1.getTokenLength());
					buf.append("\t");
					buf.append(e2.getTokenLength());
					buf.append("\t");
					buf.append(estimated);
					buf.append("\t");
					buf.append(normalizedEstimated);
					buf.append("\t");
					buf.append(getFileNames(e1.getCodehash()));
					buf.append("\t");
					buf.append(getFileNames(e2.getCodehash()));
					out.println(buf.toString());
				}
			}
		}
	}
	
	private String getFileNames(String codehash) {
		ArrayList<String> filenames = codehashToFileNames.get(codehash);
		Collections.sort(filenames);
		StringBuilder buf = new StringBuilder();
		for (String filename: filenames) {
			if (buf.length() > 0) buf.append(",");
			buf.append(filename);
		}
		return buf.toString();
	}
	
}

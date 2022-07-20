package jp.naist.se.codehash.comparison;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class ComparisonMain {

	
	public static final double THRESHOLD = 0.7;
	
	/**
	 * Compare minhash in given files
	 * @param args specify a list of file names
	 */
	public static void main(String[] args) {
		// Load entries ignoring same codehash files
		ComparisonMain main = new ComparisonMain();
		for (String arg: args) {
			File f = new File(arg);
			if (f.exists() && f.isFile() && f.canRead()) {
				main.read(f);
			}
		}
		
		System.err.println(main.getEntryCount() + " unique files found.");
		main.analyze(System.out);
	}
	
	private ArrayList<MinHashEntry> entries = new ArrayList<>();
	private HashMap<String, ArrayList<String>> codehashToFileNames = new HashMap<>();
	private HashMap<String, String> languages = new HashMap<>(32);
	
	public ComparisonMain() {
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
		return lang;
	}
	
	/**
	 * Read a hash list file  
	 * @param f specifies a file
	 */
	public void read(File f) {
		try (LineNumberReader reader = new LineNumberReader(new FileReader(f))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] tokens = line.split("\t");
				String filename = tokens[0];
				String sha1 = tokens[1];
				String lang = tokens[2];
				String codehash = tokens[3];
				String minhash = tokens[4];
				String normalizedMinhash = tokens[5];
				int fileLength = Integer.parseInt(tokens[6]);
				int tokenLength = Integer.parseInt(tokens[7]);
				int ngramSize = Integer.parseInt(tokens[8]);
				
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
		} catch (IOException e) {
			System.err.println("Failed to read " + f.getAbsolutePath());
		}
	}

	public void analyze(PrintStream out) {
		out.println("CodeHash1\tCodeHash2\tTokenLength1\tTokenLength2\tEstimatedSim\tEstimatedSimWithNormalization\tFileNames1\tFileNames2");
		// Compare all pairs
		for (int i=0; i<entries.size(); i++) {
			MinHashEntry e1 = entries.get(i);
			for (int j=i+1; j<entries.size(); j++) {
				MinHashEntry e2 = entries.get(j);
				double estimated = e1.estimateSimilarity(e2); 
				double normalizedEstimated = e1.estimateNormalizedSimilarity(e2);
				if (estimated >= THRESHOLD || normalizedEstimated >= THRESHOLD) {
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

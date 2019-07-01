package jp.naist.se.codehash.comparison;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashSet;

public class ComparisonMain {

	
	public static final double THRESHOLD = 0.7;
	
	public static void main(String[] args) {
		// Load entries ignoring same codehash files
		ArrayList<MinHashEntry> entries = new ArrayList<>();
		HashSet<String> codeSHA1 = new HashSet<>();
		for (String arg: args) {
			File f = new File(arg);
			if (f.exists() && f.isFile() && f.canRead()) {
				read(entries, codeSHA1, f);
			}
		}
		
		System.err.println(entries.size() + " unique files found.");
		
		// Compare all pairs
		for (int i=0; i<entries.size(); i++) {
			MinHashEntry e1 = entries.get(i);
			for (int j=i+1; j<entries.size(); j++) {
				MinHashEntry e2 = entries.get(j);
				double estimated = e1.estimateSimilarity(e2); 
				double normalizedEstimated = e1.estimateNormalizedSimilarity(e2);
				if (estimated >= THRESHOLD || normalizedEstimated >= THRESHOLD) {
					System.out.println(e1.getCodehash() + "\t" + e2.getCodehash() + "\t" + Double.toString(estimated) + "\t" + Double.toString(normalizedEstimated));
				}
			}
		}
	}
	
	private static void read(ArrayList<MinHashEntry> entries, HashSet<String> codehashSet, File f) {
		try (LineNumberReader reader = new LineNumberReader(new FileReader(f))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] tokens = line.split("\t");
				String filename = tokens[0];
				String sha1 = tokens[1];
				String lang = tokens[2];
				String codehash = tokens[3];
				String minhash = tokens[4];
				String normalizedMinhash = tokens[5];
				int ngramSize = Integer.parseInt(tokens[8]);
				
				if (codehashSet.add(codehash)) {
					entries.add(new MinHashEntry(filename, sha1, lang, codehash, minhash, normalizedMinhash, ngramSize));
				}
			}
		} catch (IOException e) {
			System.err.println("Failed to read " + f.getAbsolutePath());
		}
	}
}

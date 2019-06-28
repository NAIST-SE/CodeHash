package jp.naist.se.codehash.comparison;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.BitSet;

public class ComparisonMain {

	private ArrayList<MinHashEntry> entries;
	
	public static final double THRESHOLD = 0.7;
	
	public static void main(String[] args) {
		// Load entries
		ArrayList<MinHashEntry> entries = new ArrayList<>();
		for (String arg: args) {
			File f = new File(arg);
			if (f.exists() && f.isFile() && f.canRead()) {
				read(entries, f);
			}
		}
		
		// Compare all pairs
		for (int i=0; i<entries.size(); i++) {
			MinHashEntry e1 = entries.get(i);
			for (int j=i+1; j<entries.size(); j++) {
				MinHashEntry e2 = entries.get(j);
				if (e1.equivalent(e2)) {
					System.out.println(e1.getFilename() + "\t" + e2.getFilename() + "\t" + "SAME");
				} else {
					double estimated = e1.estimateSimilarity(e2); 
					if (estimated > THRESHOLD) {
						System.out.println(e1.getFilename() + "\t" + e2.getFilename() + "\t" + Double.toString(estimated));
					}
				}
			}
		}
	}
	
	private static void read(ArrayList<MinHashEntry> entries, File f) {
		try (LineNumberReader reader = new LineNumberReader(new FileReader(f))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] tokens = line.split("\t");
				String filename = tokens[0];
				String sha1 = tokens[1];
				String lang = tokens[2];
				String minhash = tokens[4];
				int ngramSize = Integer.parseInt(tokens[7]);
				entries.add(new MinHashEntry(filename, sha1, lang, minhash, ngramSize));
			}
		} catch (IOException e) {
			System.err.println("Failed to read " + f.getAbsolutePath());
		}
	}
}

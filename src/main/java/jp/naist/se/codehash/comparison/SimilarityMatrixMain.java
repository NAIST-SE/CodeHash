package jp.naist.se.codehash.comparison;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import jp.naist.se.codehash.similarity.SimilarityMetrics;

/**
 * Converts a JSON output of {@link DirectComparisonMain} into a matrix in CSV.
 * Each row has a file index, a file path, and values of the selected metric for files in the order of index (0, 1, ...).
 * Cells on and below the diagonal are empty.
 * A cell for a pair not reported in the JSON (e.g. filtered out) is also empty.
 */
public class SimilarityMatrixMain {

	/**
	 * @param args The first argument specifies a metric name, e.g. "jaccard".
	 * The second argument specifies a JSON file.  If it is omitted or "-", the program reads STDIN.
	 */
	public static void main(String[] args) {
		if (args.length < 1 || args.length > 2) {
			System.err.println("Usage: SimilarityMatrixMain [METRIC] [JSON-FILE]");
			return;
		}
		String metric = args[0];
		if (SimilarityMetrics.get(metric) == null) {
			System.err.println("Unknown metric: " + metric + " (available: " + String.join(",", SimilarityMetrics.getNames()) + ")");
			return;
		}

		try (InputStream input = (args.length == 1 || args[1].equals("-")) ? System.in : new BufferedInputStream(new FileInputStream(args[1]))) {
			PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
			SimilarityMatrixMain main = new SimilarityMatrixMain(metric);
			main.read(input);
			main.write(out);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String metric;

	/**
	 * File paths sorted by index
	 */
	private TreeMap<Integer, String> files = new TreeMap<>();

	/**
	 * Metric values (as written in JSON) for each pair.  A key is created by {@link #key(int, int)}.
	 */
	private HashMap<Long, String> values = new HashMap<>();

	public SimilarityMatrixMain(String metric) {
		this.metric = metric;
	}

	/**
	 * Read files and pairs from a JSON output of {@link DirectComparisonMain}.
	 */
	public void read(InputStream input) throws IOException {
		JsonFactory f = new JsonFactory();
		f.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
		try (JsonParser parser = f.createParser(input)) {
			if (parser.nextToken() != JsonToken.START_OBJECT) throw new IOException("A JSON object is expected.");
			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				String section = parser.getCurrentName();
				if (parser.nextToken() != JsonToken.START_ARRAY) {
					parser.skipChildren();
					continue;
				}
				while (parser.nextToken() == JsonToken.START_OBJECT) {
					HashMap<String, String> fields = new HashMap<>();
					while (parser.nextToken() == JsonToken.FIELD_NAME) {
						String name = parser.getCurrentName();
						parser.nextToken();
						fields.put(name, parser.getText());
						parser.skipChildren();
					}
					if (section.equals("Files")) {
						files.put(Integer.parseInt(fields.get("index")), fields.get("path"));
					} else if (section.equals("Pairs")) {
						String value = fields.get(metric);
						if (value != null) {
							int index1 = Integer.parseInt(fields.get("index1"));
							int index2 = Integer.parseInt(fields.get("index2"));
							values.put(key(index1, index2), value);
						}
					}
				}
			}
		}
	}

	/**
	 * Write a matrix in CSV.
	 */
	public void write(PrintWriter out) {
		StringBuilder line = new StringBuilder();
		line.append("index,path");
		for (int j: files.keySet()) {
			line.append(',');
			line.append(j);
		}
		out.println(line.toString());

		for (int i: files.keySet()) {
			line.setLength(0);
			line.append(i);
			line.append(',');
			line.append(escape(files.get(i)));
			for (int j: files.keySet()) {
				line.append(',');
				if (i < j) {
					String v = values.get(key(i, j));
					if (v != null) line.append(v);
				}
			}
			out.println(line.toString());
		}
	}

	/**
	 * @return a key for a pair regardless of the order of indices.
	 */
	private static long key(int index1, int index2) {
		int min = Math.min(index1, index2);
		int max = Math.max(index1, index2);
		return ((long)min << 32) | max;
	}

	/**
	 * Quote a CSV field if necessary.
	 */
	private static String escape(String s) {
		if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

}

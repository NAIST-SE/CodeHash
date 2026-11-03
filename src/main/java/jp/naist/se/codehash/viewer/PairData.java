package jp.naist.se.codehash.viewer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Files and file pairs in a JSON file written by DirectComparisonMain or ComparisonMain (-json).
 * Pairs are identified by their positions in the JSON file (pair IDs).
 */
public class PairData {

	/**
	 * An element of "Files".
	 */
	public static class FileInfo {

		private int index;
		private String group;
		private String path;
		private String lang;
		private String sha1;

		public int getIndex() {
			return index;
		}

		public String getGroup() {
			return group;
		}

		public String getPath() {
			return path;
		}

		public String getLanguage() {
			return lang;
		}

		/**
		 * @return the SHA-1 hash of the file content ("byte-sha1").  null if it is not available.
		 */
		public String getSha1() {
			return sha1;
		}
	}

	/**
	 * Files connected by pairs whose metric values are greater than or equal to a threshold.
	 */
	public static class Cluster {

		private int[] files;
		private int[] pairs;

		/**
		 * @return the indices of files in ascending order.
		 */
		public int[] getFiles() {
			return files;
		}

		/**
		 * @return the IDs of pairs in the cluster, in descending order of the metric value.
		 */
		public int[] getPairs() {
			return pairs;
		}
	}

	private String similarity;
	private LinkedHashMap<Integer, FileInfo> files = new LinkedHashMap<>();
	private ArrayList<String> metrics = new ArrayList<>();
	private HashMap<String, Integer> metricIndices = new HashMap<>();

	private int pairCount = 0;
	private int[] first = new int[1024];
	private int[] second = new int[1024];

	/**
	 * values.get(m)[p] is the value of the m-th metric for the p-th pair.  NaN if the value is missing.
	 */
	private ArrayList<double[]> values = new ArrayList<>();

	/**
	 * Read a JSON file.
	 * @param f specifies a JSON file.
	 * @return the content of the file.
	 * @throws IOException if the file cannot be read or it is not a JSON object.
	 */
	public static PairData read(File f) throws IOException {
		PairData data = new PairData();
		try (JsonParser parser = new JsonFactory().createParser(f)) {
			if (parser.nextToken() != JsonToken.START_OBJECT) {
				throw new IOException("A JSON object is expected: " + f.getPath());
			}
			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				String name = parser.currentName();
				JsonToken t = parser.nextToken();
				if (name.equals("Files") && t == JsonToken.START_ARRAY) {
					data.readFiles(parser);
				} else if (name.equals("Pairs") && t == JsonToken.START_ARRAY) {
					data.readPairs(parser);
				} else if (name.equals("Similarity") && t == JsonToken.VALUE_STRING) {
					data.similarity = parser.getText();
				} else {
					parser.skipChildren();
				}
			}
		}
		return data;
	}

	private void readFiles(JsonParser parser) throws IOException {
		while (parser.nextToken() == JsonToken.START_OBJECT) {
			FileInfo info = new FileInfo();
			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				String name = parser.currentName();
				parser.nextToken();
				switch (name) {
				case "index": info.index = parser.getIntValue(); break;
				case "group": info.group = parser.getText(); break;
				case "path": info.path = parser.getText(); break;
				case "lang": info.lang = parser.getText(); break;
				case "byte-sha1": info.sha1 = parser.getText(); break;
				default: parser.skipChildren();
				}
			}
			files.put(info.index, info);
		}
	}

	private void readPairs(JsonParser parser) throws IOException {
		while (parser.nextToken() == JsonToken.START_OBJECT) {
			ensureCapacity(pairCount + 1);
			for (double[] v: values) {
				v[pairCount] = Double.NaN;
			}
			while (parser.nextToken() == JsonToken.FIELD_NAME) {
				String name = parser.currentName();
				JsonToken t = parser.nextToken();
				if (name.equals("index1")) {
					first[pairCount] = parser.getIntValue();
				} else if (name.equals("index2")) {
					second[pairCount] = parser.getIntValue();
				} else if (t.isNumeric()) {
					values.get(getOrAddMetric(name))[pairCount] = parser.getDoubleValue();
				} else {
					parser.skipChildren();
				}
			}
			pairCount++;
		}
	}

	private int getOrAddMetric(String name) {
		Integer m = metricIndices.get(name);
		if (m == null) {
			m = metrics.size();
			metrics.add(name);
			metricIndices.put(name, m);
			double[] v = new double[first.length];
			Arrays.fill(v, Double.NaN);
			values.add(v);
		}
		return m;
	}

	private void ensureCapacity(int capacity) {
		if (capacity > first.length) {
			int newLength = Math.max(capacity, first.length * 2);
			first = Arrays.copyOf(first, newLength);
			second = Arrays.copyOf(second, newLength);
			for (int m=0; m<values.size(); m++) {
				double[] v = Arrays.copyOf(values.get(m), newLength);
				Arrays.fill(v, pairCount, newLength, Double.NaN);
				values.set(m, v);
			}
		}
	}

	/**
	 * @return the "Similarity" attribute (e.g. "estimated-by-minhash" written by ComparisonMain).  null if missing.
	 */
	public String getSimilarity() {
		return similarity;
	}

	/**
	 * @return metric names in the order of their appearance.
	 */
	public List<String> getMetrics() {
		return metrics;
	}

	/**
	 * @return the index of a metric.  -1 if the metric is not included in the data.
	 */
	public int getMetricIndex(String name) {
		Integer m = metricIndices.get(name);
		return m != null ? m : -1;
	}

	public Collection<FileInfo> getFiles() {
		return files.values();
	}

	/**
	 * @param index specifies a file index.
	 * @return the file.  null if the index is not listed in "Files".
	 */
	public FileInfo getFile(int index) {
		return files.get(index);
	}

	public int getPairCount() {
		return pairCount;
	}

	/**
	 * @return the file index of "index1" of the pair.
	 */
	public int getFirst(int pair) {
		return first[pair];
	}

	/**
	 * @return the file index of "index2" of the pair.
	 */
	public int getSecond(int pair) {
		return second[pair];
	}

	/**
	 * @return the value of the metric for the pair.  NaN if the value is missing.
	 */
	public double getValue(int metric, int pair) {
		return values.get(metric)[pair];
	}

	/**
	 * @param metric specifies a metric index.
	 * @param threshold specifies the minimum value.
	 * @return the IDs of pairs whose values are greater than or equal to the threshold,
	 * in descending order of the values.
	 */
	public int[] select(int metric, double threshold) {
		double[] v = values.get(metric);
		ArrayList<Integer> selected = new ArrayList<>();
		for (int p=0; p<pairCount; p++) {
			if (v[p] >= threshold) selected.add(p);
		}
		selected.sort(Comparator.<Integer>comparingDouble(p -> -v[p]).thenComparingInt(p -> first[p]).thenComparingInt(p -> second[p]));
		return selected.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Group files into clusters, i.e. connected components of a graph
	 * whose edges are pairs having values greater than or equal to the threshold.
	 * @param metric specifies a metric index.
	 * @param threshold specifies the minimum value.
	 * @return clusters in descending order of the number of files.
	 */
	public List<Cluster> cluster(int metric, double threshold) {
		int[] selected = select(metric, threshold);

		// Union-find on file indices
		HashMap<Integer, Integer> parent = new HashMap<>();
		for (int p: selected) {
			int r1 = find(parent, first[p]);
			int r2 = find(parent, second[p]);
			if (r1 != r2) parent.put(Math.max(r1, r2), Math.min(r1, r2));
		}

		LinkedHashMap<Integer, ArrayList<Integer>> clusterFiles = new LinkedHashMap<>();
		LinkedHashMap<Integer, ArrayList<Integer>> clusterPairs = new LinkedHashMap<>();
		ArrayList<Integer> indices = new ArrayList<>(parent.keySet());
		indices.sort(null);
		for (int f: indices) {
			clusterFiles.computeIfAbsent(find(parent, f), k -> new ArrayList<>()).add(f);
		}
		for (int p: selected) {
			clusterPairs.computeIfAbsent(find(parent, first[p]), k -> new ArrayList<>()).add(p);
		}

		ArrayList<Cluster> clusters = new ArrayList<>();
		for (int root: clusterFiles.keySet()) {
			Cluster c = new Cluster();
			c.files = clusterFiles.get(root).stream().mapToInt(Integer::intValue).toArray();
			c.pairs = clusterPairs.get(root).stream().mapToInt(Integer::intValue).toArray();
			clusters.add(c);
		}
		clusters.sort(Comparator.<Cluster>comparingInt(c -> -c.files.length).thenComparingInt(c -> c.files[0]));
		return clusters;
	}

	private static int find(HashMap<Integer, Integer> parent, int f) {
		int root = f;
		Integer p = parent.putIfAbsent(f, f);
		while (p != null && p != root) {
			root = p;
			p = parent.get(root);
		}
		// Path compression
		while (f != root) {
			int next = parent.get(f);
			parent.put(f, root);
			f = next;
		}
		return root;
	}

}

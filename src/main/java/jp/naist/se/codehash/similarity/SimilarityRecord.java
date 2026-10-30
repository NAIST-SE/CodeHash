package jp.naist.se.codehash.similarity;

import java.io.IOException;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Similarity values of a file pair.
 * Each value is calculated on demand and cached,
 * so that a filter can skip metrics that are unnecessary to decide the result.
 */
public class SimilarityRecord {

	private FilePair pair;
	private HashMap<SimilarityMetric, Double> values = new HashMap<>();

	public SimilarityRecord(FilePair pair) {
		this.pair = pair;
	}

	public FilePair getPair() {
		return pair;
	}

	/**
	 * @param metric
	 * @return the similarity value calculated by the metric.
	 */
	public double getValue(SimilarityMetric metric) {
		Double v = values.get(metric);
		if (v == null) {
			v = metric.calculate(pair);
			values.put(metric, v);
		}
		return v;
	}

	/**
	 * Write similarity values as JSON fields.
	 * @param gen
	 * @param metrics specifies metrics to be written.
	 * @throws IOException
	 */
	public void writeSimilarity(JsonGenerator gen, Iterable<SimilarityMetric> metrics) throws IOException {
		for (SimilarityMetric m: metrics) {
			gen.writeNumberField(m.getName(), getValue(m));
		}
	}

}

package jp.naist.se.codehash.similarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * A registry of available similarity metrics.
 * The registration order is used as the output order.
 */
public class SimilarityMetrics {

	public static final String EXACT_JACCARD = "exact-jaccard";
	public static final String JACCARD = "jaccard";
	public static final String EXACT_OVERLAP_COEFFICIENT = "exact-overlap-coefficient";
	public static final String OVERLAP_COEFFICIENT = "overlap-coefficient";
	public static final String EXACT_OVERLAP_SIMILARITY = "exact-overlap-similarity";
	public static final String OVERLAP_SIMILARITY = "overlap-similarity";

	private static LinkedHashMap<String, SimilarityMetric> metrics = new LinkedHashMap<>();

	static {
		register(new NgramSimilarityMetric.Jaccard(EXACT_JACCARD, false));
		register(new NgramSimilarityMetric.OverlapCoefficient(EXACT_OVERLAP_COEFFICIENT, false));
		register(new NgramSimilarityMetric.OverlapSimilarity(EXACT_OVERLAP_SIMILARITY, false));
		register(new NgramSimilarityMetric.Jaccard(JACCARD, true));
		register(new NgramSimilarityMetric.OverlapCoefficient(OVERLAP_COEFFICIENT, true));
		register(new NgramSimilarityMetric.OverlapSimilarity(OVERLAP_SIMILARITY, true));
	}

	/**
	 * Register a new metric.
	 * @param metric
	 * @throws IllegalArgumentException if the name is already registered.
	 */
	public static synchronized void register(SimilarityMetric metric) {
		if (metrics.containsKey(metric.getName())) {
			throw new IllegalArgumentException("Metric is already registered: " + metric.getName());
		}
		metrics.put(metric.getName(), metric);
	}

	/**
	 * @param name
	 * @return a metric object.  null if no metric has the name.
	 */
	public static synchronized SimilarityMetric get(String name) {
		return metrics.get(name);
	}

	/**
	 * @return all metrics in the registration order.
	 */
	public static synchronized Collection<SimilarityMetric> getAll() {
		return Collections.unmodifiableList(new ArrayList<>(metrics.values()));
	}

	/**
	 * @return all metric names in the registration order.
	 */
	public static synchronized Collection<String> getNames() {
		return Collections.unmodifiableList(new ArrayList<>(metrics.keySet()));
	}

}

package jp.naist.se.codehash.similarity;

/**
 * A base class for metrics defined by the intersection of N-gram multisets.
 */
public abstract class NgramSimilarityMetric implements SimilarityMetric {

	private String name;
	private boolean normalized;

	/**
	 * @param name specifies a metric name.
	 * @param normalized If true, the metric compares N-grams ignoring identifier names.
	 */
	protected NgramSimilarityMetric(String name, boolean normalized) {
		this.name = name;
		this.normalized = normalized;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double calculate(FilePair pair) {
		return calculate(pair.getIntersection(normalized), pair.getFirst().getNgramCount(), pair.getSecond().getNgramCount());
	}

	/**
	 * @param intersection is the size of intersection of N-gram multisets.
	 * @param size1 is the number of N-grams in the first file.
	 * @param size2 is the number of N-grams in the second file.
	 * @return a similarity value.
	 */
	protected abstract double calculate(int intersection, int size1, int size2);

	/**
	 * Jaccard index: Intersection(S1, S2) / Union(S1, S2).
	 */
	public static class Jaccard extends NgramSimilarityMetric {
		public Jaccard(String name, boolean normalized) {
			super(name, normalized);
		}

		@Override
		protected double calculate(int intersection, int size1, int size2) {
			return intersection * 1.0 / (size1 + size2 - intersection);
		}
	}

	/**
	 * Overlap coefficient: Intersection(S1, S2) / Min(S1, S2).
	 */
	public static class OverlapCoefficient extends NgramSimilarityMetric {
		public OverlapCoefficient(String name, boolean normalized) {
			super(name, normalized);
		}

		@Override
		protected double calculate(int intersection, int size1, int size2) {
			return intersection * 1.0 / Math.min(size1, size2);
		}
	}

	/**
	 * Overlap similarity: Intersection(S1, S2) / Max(S1, S2).
	 */
	public static class OverlapSimilarity extends NgramSimilarityMetric {
		public OverlapSimilarity(String name, boolean normalized) {
			super(name, normalized);
		}

		@Override
		protected double calculate(int intersection, int size1, int size2) {
			return intersection * 1.0 / Math.max(size1, size2);
		}
	}

}

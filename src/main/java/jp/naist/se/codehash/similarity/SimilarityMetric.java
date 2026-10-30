package jp.naist.se.codehash.similarity;

/**
 * A strategy to calculate a similarity value of a file pair.
 * To add a new metric, implement this interface and register an instance
 * to {@link SimilarityMetrics#register(SimilarityMetric)}.
 */
public interface SimilarityMetric {

	/**
	 * @return a metric name.  It is used in command line options and as a key in the output.
	 * The name should consist of alphabets, digits, '-', and '_' so that it can be used in filter expressions.
	 */
	public String getName();

	/**
	 * @param pair specifies files to be compared.
	 * @return the similarity value of the files.
	 */
	public double calculate(FilePair pair);

}

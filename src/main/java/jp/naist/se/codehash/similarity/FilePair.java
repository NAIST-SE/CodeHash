package jp.naist.se.codehash.similarity;

import jp.naist.se.codehash.file.FileEntity;

/**
 * A pair of files to be compared.
 * This object caches intermediate values (e.g. the size of intersection of N-gram multisets)
 * so that multiple similarity metrics can share them.
 */
public class FilePair {

	private static final int NOT_COMPUTED = -1;

	private FileEntity e1;
	private FileEntity e2;
	private int intersection = NOT_COMPUTED;
	private int normalizedIntersection = NOT_COMPUTED;

	public FilePair(FileEntity e1, FileEntity e2) {
		this.e1 = e1;
		this.e2 = e2;
	}

	public FileEntity getFirst() {
		return e1;
	}

	public FileEntity getSecond() {
		return e2;
	}

	/**
	 * @return the size of intersection of the N-gram multisets of the files.
	 */
	public int getIntersection() {
		if (intersection == NOT_COMPUTED) {
			intersection = e1.getNgramMultiset().intersection(e2.getNgramMultiset());
		}
		return intersection;
	}

	/**
	 * @return the size of intersection of the normalized N-gram multisets of the files.
	 */
	public int getNormalizedIntersection() {
		if (normalizedIntersection == NOT_COMPUTED) {
			normalizedIntersection = e1.getNormalizedNgramMultiset().intersection(e2.getNormalizedNgramMultiset());
		}
		return normalizedIntersection;
	}

	/**
	 * @param normalized specifies whether normalized N-grams are used or not.
	 * @return the size of intersection of N-gram multisets.
	 */
	public int getIntersection(boolean normalized) {
		return normalized ? getNormalizedIntersection() : getIntersection();
	}

}

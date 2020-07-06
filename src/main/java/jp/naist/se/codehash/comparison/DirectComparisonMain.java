package jp.naist.se.codehash.comparison;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.http.impl.auth.UnsupportedDigestAlgorithmException;

import jp.naist.se.codehash.CodeHashTokenReader;
import jp.naist.se.codehash.FileCodeHash;
import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.GitCodeHash;
import jp.naist.se.codehash.HashStringUtil;
import jp.naist.se.codehash.MurmurMinHash;
import jp.naist.se.codehash.TokenReader;
import jp.naist.se.codehash.util.StringMultiset;

public class DirectComparisonMain {


	/**
	 * Compare two source files.
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length >= 2) {
			File f1 = new File(args[0]);
			File f2 = new File(args[1]);
			if (f1.canRead() && f2.canRead()) {
				FileEntity e1 = new FileEntity(f1);
				FileEntity e2 = new FileEntity(f2);
				int intersection = e1.ngrams.intersection(e2.ngrams);
				double jaccard = intersection * 1.0 / (e1.ngramCount + e2.ngramCount - intersection);
				int normalizedIntersection = e1.normalizedNgrams.intersection(e2.normalizedNgrams);
				double normalizedSetJaccard = normalizedIntersection * 1.0 / (e1.ngramCount + e2.ngramCount - normalizedIntersection);

				double estimated = e1.minhashEntry.estimateSimilarity(e2.minhashEntry);
				double estimatedNormalized = e1.minhashEntry.estimateNormalizedSimilarity(e2.minhashEntry);

				double sha1estimated = e1.sha1minhashEntry.estimateSimilarity(e2.sha1minhashEntry);
				double sha1estimatedNormalized = e1.sha1minhashEntry.estimateNormalizedSimilarity(e2.sha1minhashEntry);
				

				System.out.println("jaccard: " + jaccard);
				System.out.println("normalized-jaccard: " + normalizedSetJaccard);
				System.out.println("estimated-minhash: " + estimated);
				System.out.println("normalized-minhash: " + estimatedNormalized);
				System.out.println("sha1-estimated-minhash: " + sha1estimated);
				System.out.println("sha1-normalized-minhash: " + sha1estimatedNormalized);
			}
		} else {
			System.err.println("Arguments: Two file names to be compared.");
		}
	}
	
	private static class FileEntity {
		
		private String filehash;
		private String codehash;
		private String minhash;
		private String normalizedMinhash;
		private int ngramCount;
		private StringMultiset ngrams;
		private StringMultiset normalizedNgrams;
		private MinHashEntry minhashEntry;
		private MinHashEntry sha1minhashEntry;
		
		
		public FileEntity(File f) {
			String path = f.getAbsolutePath();
			FileType t = FileType.getFileTypeFromName(path);
			if (FileType.isSupported(t)) {
				try {
					byte[] content = Files.readAllBytes(f.toPath());
					MessageDigest d = MessageDigest.getInstance(FileCodeHash.FILEHASH_ALGORITHM);
					filehash = HashStringUtil.bytesToHex(d.digest(content));
					TokenReader tokenReader = FileType.createReader(t, new ByteArrayInputStream(content));
					CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, f.length());
					MurmurMinHash h = new MurmurMinHash(GitCodeHash.BBITMINHASH_BITCOUNT, GitCodeHash.BBITMINHASH_NGRAM_SIZE, wrapper);
					minhash = HashStringUtil.bytesToHex(h.getHash());
					normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHash());
					codehash = HashStringUtil.bytesToHex(wrapper.getHash());
					ngramCount = h.getNgramCount();
					ngrams = h.getNgramMultiset();
					normalizedNgrams = h.getNormalizedNgramMultiset();
					
					minhashEntry = new MinHashEntry(path, filehash, t.name(), codehash, minhash, normalizedMinhash, f.length(), tokenReader.getTokenCount(), ngramCount);
					sha1minhashEntry = new MinHashEntry(path, filehash, t.name(), codehash, HashStringUtil.bytesToHex(h.getSHA1MinHash()), HashStringUtil.bytesToHex(h.getNormalizedSHA1MinHash()), f.length(), tokenReader.getTokenCount(), ngramCount);
				} catch (NoSuchAlgorithmException e) { 
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}

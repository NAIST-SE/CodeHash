package jp.naist.se.codehash.comparison;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.CodeHashTokenReader;
import jp.naist.se.codehash.FileCodeHash;
import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.GitCodeHash;
import jp.naist.se.codehash.HashStringUtil;
import jp.naist.se.codehash.MurmurMinHash;
import jp.naist.se.codehash.TokenReader;
import jp.naist.se.codehash.util.StringMultiset;

public class DirectComparisonMain {

	private static String LANG_OPTION = "-lang:";
	
	/**
	 * Compare two source files.
	 * @param args
	 */
	public static void main(String[] args) {
		ArrayList<FileEntity> files = new ArrayList<>();
		FileType t = null;
		for (String s: args) {
			if (s.startsWith(LANG_OPTION)) {
				t = FileType.getFileType(s.substring(LANG_OPTION.length()));
			}
		}
		for (String s: args) {
			File f = new File(s);
			FileEntity entity = FileEntity.parse(f, t);
			if (entity != null) files.add(entity);
		}
		
		if (files.size() <= 1) {
			System.err.println("Arguments: Two or more source file names should be specified.");
			return;
		}

		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(System.out)) {
			gen.writeStartObject();
			gen.writeArrayFieldStart("Files");
			for (int i=0; i<files.size(); i++) {
				FileEntity e1 = files.get(i);
				gen.writeStartObject();
				gen.writeNumberField("index", i);
				gen.writeStringField("path", e1.path);
				gen.writeStringField("lang", e1.getLanguageName());
				gen.writeNumberField("byte-length", e1.byteLength);
				gen.writeNumberField("token-length", e1.tokenLength);
				gen.writeNumberField("ngram-count", e1.ngramCount);
				gen.writeEndObject();
			}
			gen.writeEndArray();

			gen.writeArrayFieldStart("Pairs");

			// Pick up file pairs 
			for (int i=0; i<files.size(); i++) {
				FileEntity e1 = files.get(i);
				
				for (int j=i+1; j<files.size(); j++) {
					FileEntity e2 = files.get(j);
					
					// Compare them if they are written in the same language
					if (e1.isSameLanguage(e2)) {
						gen.writeStartObject();
						gen.writeNumberField("index1", i);
						gen.writeNumberField("index2", j);
						writeSimilarity(gen, "", Similarity.calculateSimilarity(e1, e2));
						writeSimilarity(gen, "normalization-", Similarity.calculateSimilarityWithNormalization(e1, e2));
						gen.writeEndObject();
					}
				}
			}
			

			gen.writeEndArray();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private static void writeSimilarity(JsonGenerator gen, String header, Similarity s) throws IOException {
		gen.writeNumberField(header + "jaccard", s.getJaccard());
		gen.writeNumberField(header + "estimated-jaccard", s.getEstimatedJaccard());
		gen.writeNumberField(header + "inclusion1", s.getInclusion1());
		gen.writeNumberField(header + "inclusion2", s.getInclusion2());
	}
	
	public static class Similarity {

		private int intersection;
		private int size1;
		private int size2;
		private double jaccard;
		private double inclusion1;
		private double inclusion2;
		private double estimatedJaccard;
		
		private Similarity(int intersection, int size1, int size2) {
			this.intersection = intersection;
			this.size1 = size1;
			this.size2 = size2;
			this.jaccard = intersection * 1.0 / (size1 + size2 - intersection);
			this.inclusion1 = intersection * 1.0 / size1;
			this.inclusion2 = intersection * 1.0 / size2;
		}
		
		public static Similarity calculateSimilarity(FileEntity e1, FileEntity e2) {
			Similarity s = new Similarity(e1.ngrams.intersection(e2.ngrams), e1.ngramCount, e2.ngramCount);
			s.estimatedJaccard = e1.minhashEntry.estimateSimilarity(e2.minhashEntry);
			return s;
		}
		
		public static Similarity calculateSimilarityWithNormalization(FileEntity e1, FileEntity e2) {
			Similarity s = new Similarity(e1.normalizedNgrams.intersection(e2.normalizedNgrams), e1.ngramCount, e2.ngramCount);
			s.estimatedJaccard = e1.minhashEntry.estimateNormalizedSimilarity(e2.minhashEntry);
			return s;
		}
		
		public double getInclusion1() {
			return inclusion1;
		}
		
		public double getInclusion2() {
			return inclusion2;
		}
		
		public int getSize1() {
			return size1;
		}
		
		public int getSize2() {
			return size2;
		}
		
		public int getIntersection() {
			return intersection;
		}
		
		public double getJaccard() {
			return jaccard;
		}
		
		public double getEstimatedJaccard() {
			return estimatedJaccard;
		}
	}
	
	
	public static class FileEntity {
		
		private String path;
		private FileType type;
		private int byteLength;
		private int tokenLength;
		private String filehash;
		private String codehash;
		private String minhash;
		private String normalizedMinhash;
		private int ngramCount;
		private StringMultiset ngrams;
		private StringMultiset normalizedNgrams;
		private MinHashEntry minhashEntry;
		
		public static FileEntity parse(File f, FileType enforceLanguage) {
			String path = f.getAbsolutePath();
			FileType type = enforceLanguage != null ? enforceLanguage : FileType.getFileTypeFromName(path);
			if (f.canRead() && FileType.isSupported(type)) {
				try {
					byte[] content = Files.readAllBytes(f.toPath());
					return new FileEntity(path, type, content);
				} catch (IOException e) {
					return null;
				}
			} else {
				return null;
			}
		}
		
		public FileEntity(String path, FileType type, byte[] content) {
			this.path = path;
			this.type = type;
			this.byteLength = content.length;
			try {
				MessageDigest d = MessageDigest.getInstance(FileCodeHash.FILEHASH_ALGORITHM);
				filehash = HashStringUtil.bytesToHex(d.digest(content));
				TokenReader tokenReader = FileType.createReader(type, new ByteArrayInputStream(content));
				CodeHashTokenReader wrapper = new CodeHashTokenReader(tokenReader, byteLength);
				MurmurMinHash h = new MurmurMinHash(GitCodeHash.BBITMINHASH_BITCOUNT, GitCodeHash.BBITMINHASH_NGRAM_SIZE, wrapper);
				minhash = HashStringUtil.bytesToHex(h.getHash());
				normalizedMinhash = HashStringUtil.bytesToHex(h.getNormalizedHash());
				codehash = HashStringUtil.bytesToHex(wrapper.getHash());
				ngramCount = h.getNgramCount();
				ngrams = h.getNgramMultiset();
				normalizedNgrams = h.getNormalizedNgramMultiset();
				tokenLength = tokenReader.getTokenCount();

				minhashEntry = new MinHashEntry(path, filehash, getLanguageName(), codehash, minhash, normalizedMinhash, byteLength, tokenLength, ngramCount);
			} catch (NoSuchAlgorithmException e) { 
				e.printStackTrace();
			}
		}
		
		public String getLanguageName() {
			return type.name();
		}
		
		public boolean isSameLanguage(FileEntity another) { 
			return this.type == another.type;
		}
	}

}

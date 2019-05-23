package jp.naist.se.codehash;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AsyncObjectLoaderQueue;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.RawParseUtils;



public class GitCodeHash {

	//public static final String REPO_ROOT = "/data/repo-2019-hata/";
	
	/**
	 * Extract all comments from Git directories.
	 * @param args specify a list of repos and repo root dir.
	 */
	public static void main(String[] args) { 
		GitCodeHash analyzer = new GitCodeHash();
		String REPO_ROOT = args[1];
		
		try (LineNumberReader outcsv = new LineNumberReader(new FileReader(args[0]), 65536)) {

			for (String line = outcsv.readLine(); line != null; line = outcsv.readLine()) {
				String[] tokens = line.split(",");
				String repoId = tokens[0];
				
				File gitDir = new File(REPO_ROOT + repoId);
				String filelist = "filelist/" + repoId + ".txt";
				File outputFile = new File("codehash/" + repoId + "-j.txt");
				if (!outputFile.exists()) {
					File outputFileTemp = new File("codehash/" + repoId + "-j.txt.tmp");
					try (LineNumberReader reader = new LineNumberReader(new FileReader(filelist))) {
						try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(outputFileTemp), 65536))) {
							analyzer.parseGitRepository(gitDir, reader, w);
						} 
						outputFileTemp.renameTo(outputFile);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private MessageDigest digest;

	public GitCodeHash() {
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to compute SHA-1 hash", e);
		}
	}
	
	/**
	 * Check whether a specified directory is .git directory or not.   
	 * @param dir
	 * @return dir itself if it is a .git directory.
	 * If it includes .git as a subdirectory, the subdirectory is returned.  
	 * The method returns null if dir is not .git directory. 
	 */
	public static File ensureGitDir(File dir) {
		if (dir.isDirectory()) {
			if (dir.getName().equals(".git") || dir.getName().endsWith(".git")) {
				return dir;
			} else {
				File another = new File(dir, ".git");
				if (another.exists() && another.isDirectory()) {
					return another;
				}
				File[] candidates = dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.endsWith(".git");
					}
				});
				if (candidates.length > 0) {
					return candidates[0];
				}
			}
		}
		return null;
	}
	
	public void parseRepositoryFaster(File gitDir, LineNumberReader target, PrintWriter w) {
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			try (ObjectReader r = repo.getObjectDatabase().newReader()) {
				AsyncObjectLoaderQueue<ObjectIdWithLang> loader = r.open(new Sha1Provider(target), false);
				while (loader.next()) {
					
					ObjectIdWithLang current = loader.getCurrent();
					ObjectLoader l = loader.open();
					try {
						TokenReader tokenReader = FileType.createReader(current.getLang(), l.openStream());
						long size = l.getSize();
						
						StringBuilder builder = new StringBuilder((int)size);
						int tokenCount = 0;
						while (tokenReader.next()) {
							String token = tokenReader.getText();
							// PHPLexer may return null
							if (token != null) { 
								builder.append(token);
								builder.append('\0');
								tokenCount++;
							}
						}
						byte[] codehashBytes = digest.digest(builder.toString().getBytes());
						String codehash = bytesToHex(codehashBytes);
						
						StringBuilder result = new StringBuilder(256);
						result.append(current.getSHA1());
						result.append("\t");
						result.append(current.getLangName());
						result.append("\t");
						result.append(codehash);
						result.append("\t");
						result.append(size);
						result.append("\t");
						result.append(tokenCount);
						w.println(result.toString());
						
					} catch (MissingObjectException e) {
						// Ignore missing objects
					} catch (IOException e) {
						// Ignore 
					}
				}
			}
		} catch (IOException e) {	
			e.printStackTrace();
		}
	}
	
	static class Sha1Provider implements Iterable<ObjectIdWithLang>, Iterator<ObjectIdWithLang> {
		
		private LineNumberReader target;
		private ObjectIdWithLang nextElement;

		public Sha1Provider(LineNumberReader target) {
			this.target = target;
			nextElement = readNext();
		}
		
		private ObjectIdWithLang readNext() {
			try {
				for (String line = target.readLine(); line != null; line = target.readLine()) {
					int index = line.lastIndexOf('\t');
					String filetype = line.substring(index+1, line.length());
					FileType t = FileType.getFileType(filetype);
					if (t != FileType.UNSUPPORTED) {
						index = line.indexOf('\t');
						String sha1 = line.substring(0, index);
						return new ObjectIdWithLang(sha1, filetype, t);
					}
				}
			} catch (IOException e) {
			}
			return null;
		}
		
		@Override
		public Iterator<ObjectIdWithLang> iterator() {
			return this;
		}
		
		@Override
		public boolean hasNext() {
			return nextElement != null;
		}
		
		@Override
		public ObjectIdWithLang next() {
			ObjectIdWithLang element = nextElement;
			nextElement = readNext();
			return element;
		}
		
		@Override
		public void remove() {
			// ignore the method
		}
	}
	
	static class ObjectIdWithLang extends ObjectId {

		private String sha1;
		private String langName;
		private FileType lang;
		
		public ObjectIdWithLang(String s, String langName, FileType lang) {
			this(Constants.encodeASCII(s), lang);
			this.sha1 = s;
			this.langName = langName;
		}
		
		private ObjectIdWithLang(byte[] bs, FileType lang) {
			super(RawParseUtils.parseHexInt32(bs, 0),
					RawParseUtils.parseHexInt32(bs, 8),
					RawParseUtils.parseHexInt32(bs, 16),
					RawParseUtils.parseHexInt32(bs, 24),
					RawParseUtils.parseHexInt32(bs, 32));
			this.lang = lang;
		}
		
		public FileType getLang() {
			return lang;
		}
		
		public String getLangName() {
			return langName;
		}
		
		public String getSHA1() {
			return sha1;
		}
	}
	
	/**
	 * @param gitDir is a .git directory.
	 * @param target is a list of source files in the repo (.tsv files including SHA1, file name, and LANGUAGE).
	 */
	public void parseGitRepository(File gitDir, LineNumberReader target, PrintWriter w) {
		FileRepositoryBuilder b = new FileRepositoryBuilder();
		b.setGitDir(gitDir);
		try (Repository repo = b.build()) {
			try (ObjectReader r = repo.getObjectDatabase().newReader()) {

				for (String line = target.readLine(); line != null; line = target.readLine()) {
					int index = line.lastIndexOf('\t');
					String filetype = line.substring(index+1, line.length());
					FileType t = FileType.getFileType(filetype);
					if (t != FileType.UNSUPPORTED) {
						index = line.indexOf('\t');
						String sha1 = line.substring(0, index);
						ObjectId id = ObjectId.fromString(sha1);
						
						try {
							ObjectLoader l = r.open(id);
							TokenReader tokenReader = FileType.createReader(t, l.openStream());
							long size = l.getSize();
							
							StringBuilder builder = new StringBuilder((int)size);
							int tokenCount = 0;
							while (tokenReader.next()) {
								String token = tokenReader.getText();
								// PHPLexer may return null
								if (token != null) { 
									builder.append(token);
									builder.append('\0');
									tokenCount++;
								}
							}
							byte[] codehashBytes = digest.digest(builder.toString().getBytes());
							String codehash = bytesToHex(codehashBytes);
							
							StringBuilder result = new StringBuilder(256);
							result.append(sha1);
							result.append("\t");
							result.append(filetype);
							result.append("\t");
							result.append(codehash);
							result.append("\t");
							result.append(size);
							result.append("\t");
							result.append(tokenCount);
							w.println(result.toString());
							
						} catch (MissingObjectException e) {
							// Ignore missing objects
						} catch (IOException e) {
							// Ignore 
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private final static char[] hexArray = "0123456789abcdef".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

}

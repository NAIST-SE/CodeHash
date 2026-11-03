package jp.naist.se.codehash.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.util.StringDictionary;

public class FileGroup {
		
	private String groupId;
	private ArrayList<Path> filePaths;
	private ArrayList<FileEntity> fileEntities;
	
	public FileGroup(String groupId) {
		this.groupId = groupId;
		this.filePaths = new ArrayList<>();
	}
	
	public String getGroupId() {
		return groupId;
	}
	
	public void add(String pathname) {
		try {
			Path path = new File(pathname).toPath();
			if (Files.isRegularFile(path)) {
				filePaths.add(path);
			} else if (Files.isDirectory(path)) {
				Files.walkFileTree(path, new FileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						filePaths.add(file);
						return FileVisitResult.CONTINUE;
					}
					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						return FileVisitResult.CONTINUE;
					}
				});
			}
		} catch (IOException e) {
		}
		
	}
	
	/**
	 * Load files in parallel.
	 * Indices are assigned to the loaded files in the order of the paths added to this group.
	 * The parameters are passed to {@link FileEntity#parse(Path, FileType, int, StringDictionary, boolean, boolean)}.
	 */
	public void loadEntities(FileType enforceLanguage, int N, StringDictionary dictionary, boolean computeMinHash, boolean computeNormalizedMinHash) {
		List<FileEntity> loaded = filePaths.parallelStream()
				.map(path -> FileEntity.parse(path, enforceLanguage, N, dictionary, computeMinHash, computeNormalizedMinHash))
				.collect(Collectors.toList());
		this.fileEntities = new ArrayList<>(loaded.size());
		for (FileEntity e: loaded) {
			if (e != null) {
				e.assignIndex();
				fileEntities.add(e);
			}
		}
	}
	
	public ArrayList<FileEntity> getFiles() {
		return fileEntities;
	}
	
}

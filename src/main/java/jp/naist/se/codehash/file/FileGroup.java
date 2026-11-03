package jp.naist.se.codehash.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.util.FutureUtil;
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
	 * The other parameters are passed to {@link FileEntity#parse(Path, FileType, int, StringDictionary, boolean, boolean)}.
	 * @param executor loads files in parallel.  If null, files are loaded in the caller thread.
	 */
	public void loadEntities(FileType enforceLanguage, int N, StringDictionary dictionary, boolean computeMinHash, boolean computeNormalizedMinHash, ExecutorService executor) {
		ArrayList<FileEntity> loaded = new ArrayList<>(filePaths.size());
		if (executor == null) {
			for (Path path: filePaths) {
				loaded.add(FileEntity.parse(path, enforceLanguage, N, dictionary, computeMinHash, computeNormalizedMinHash));
			}
		} else {
			ArrayList<Future<FileEntity>> tasks = new ArrayList<>(filePaths.size());
			for (Path path: filePaths) {
				tasks.add(executor.submit(() -> FileEntity.parse(path, enforceLanguage, N, dictionary, computeMinHash, computeNormalizedMinHash)));
			}
			for (Future<FileEntity> task: tasks) {
				loaded.add(FutureUtil.get(task));
			}
		}
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

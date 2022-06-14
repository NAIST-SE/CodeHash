package jp.naist.se.codehash.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

import jp.naist.se.codehash.FileType;

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
	
	public void loadEntities(FileType enforceLanguage, int N) {
		this.fileEntities = new ArrayList<>(filePaths.size()); 
		for (Path path: filePaths) {
			FileEntity e = FileEntity.parse(path, enforceLanguage, N);
			if (e != null) {
				fileEntities.add(e);
			}
		}
	}
	
	public ArrayList<FileEntity> getFiles() {
		return fileEntities;
	}
	
}

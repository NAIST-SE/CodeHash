package jp.naist.se.codehash.viewer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;


/**
 * The viewer package must not be referred by other packages, so that the viewer can be separated from the project.
 * The only exception is CodeHashMain launching PairViewerMain.
 */
public class ViewerDependencyTest {

	private static final String PACKAGE = PairViewerMain.class.getPackageName();

	private static final Path CODEHASH_MAIN = Paths.get("src", "main", "java", "jp", "naist", "se", "codehash", "CodeHashMain.java");

	@Test
	public void testNoReferenceToViewer() throws Exception {
		Path root = Paths.get("src", "main", "java");
		Path viewer = root.resolve(PACKAGE.replace('.', '/'));
		try (Stream<Path> files = Files.walk(root)) {
			List<Path> violations = files.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> !p.startsWith(viewer))
					.filter(p -> {
						try {
							String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
							if (p.equals(CODEHASH_MAIN)) {
								content = content.replace(PairViewerMain.class.getName(), "");
							}
							return content.contains(PACKAGE);
						} catch (java.io.IOException e) {
							throw new java.io.UncheckedIOException(e);
						}
					})
					.collect(Collectors.toList());
			Assert.assertEquals("Files referring to " + PACKAGE, List.of(), violations);
		}
	}

}

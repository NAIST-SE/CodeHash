package jp.naist.se.codehash.viewer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A simple viewer of similar file pairs in a JSON file written by DirectComparisonMain or ComparisonMain (-json).
 * It starts an HTTP server on the local host; open the printed URL in a web browser.
 *
 * Command line arguments: [-port:N] [-git:REPOSITORY]... [JSON-FILE]
 *
 * - "-port:N" specifies a port number.  An available port is selected by default.
 * - "-git:REPOSITORY" registers a git repository to show blobs listed by GitCodeHash.
 *   It can be specified multiple times.  Repositories can be also added on the page.
 *
 * This package must not be referred by other packages, so that the viewer can be separated from the project.
 * The only exception is CodeHashMain registering this class as the "pair-viewer" command.
 */
public class PairViewerMain {

	private static final String PORT_OPTION = "-port:";
	private static final String GIT_OPTION = "-git:";

	public static void main(String[] args) {
		int port = 0;
		String jsonPath = null;
		ArrayList<String> repositories = new ArrayList<>();
		for (String arg: args) {
			if (arg.startsWith(PORT_OPTION)) {
				try {
					port = Integer.parseInt(arg.substring(PORT_OPTION.length()));
				} catch (NumberFormatException e) {
					System.err.println("Invalid port number: " + arg);
					return;
				}
			} else if (arg.startsWith(GIT_OPTION)) {
				repositories.add(arg.substring(GIT_OPTION.length()));
			} else if (jsonPath == null) {
				jsonPath = arg;
			} else {
				System.err.println("Only one JSON file can be specified: " + arg);
				return;
			}
		}
		if (jsonPath == null) {
			System.err.println("Usage: PairViewerMain [-port:N] [-git:REPOSITORY]... [JSON-FILE]");
			return;
		}

		try {
			PairData data = PairData.read(new File(jsonPath));
			SourceLoader loader = new SourceLoader();
			for (String r: repositories) {
				try {
					loader.addRepository(r);
				} catch (IOException e) {
					System.err.println("Not a git repository: " + r);
				}
			}
			PairViewerServer server = new PairViewerServer(new File(jsonPath).getAbsolutePath(), data, loader);
			server.start(port);
			System.out.println("Loaded " + data.getFiles().size() + " files and " + data.getPairCount() + " pairs.");
			System.out.println("Open " + server.getURL() + " in a web browser.  Press Ctrl+C to stop.");
		} catch (IOException e) {
			System.err.println("Failed to start the viewer: " + e.getMessage());
		}
	}

}

package jp.naist.se.codehash;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

import jp.naist.se.codehash.comparison.ComparisonMain;
import jp.naist.se.codehash.comparison.DirectComparisonMain;
import jp.naist.se.codehash.comparison.FileTokenizerMain;
import jp.naist.se.codehash.comparison.SimilarityMatrixMain;

/**
 * The entry point of the jar file.
 * The first argument selects a command, and the remaining arguments are passed to the main class of the command,
 * e.g. "java -jar CodeHash.jar compare src" is equivalent to "java -classpath CodeHash.jar jp.naist.se.codehash.comparison.DirectComparisonMain src".
 * A simple name of a main class (e.g. "DirectComparisonMain") is also accepted as a command name.
 */
public class CodeHashMain {

	private static class Command {
		private String name;
		private Class<?> mainClass;
		private Consumer<String[]> main;
		private String description;

		public Command(String name, Class<?> mainClass, Consumer<String[]> main, String description) {
			this.name = name;
			this.mainClass = mainClass;
			this.main = main;
			this.description = description;
		}
	}

	/**
	 * Commands in the order of the usage message.
	 */
	private static final LinkedHashMap<String, Command> COMMANDS = new LinkedHashMap<>();

	private static void register(String name, Class<?> mainClass, Consumer<String[]> main, String description) {
		Command c = new Command(name, mainClass, main, description);
		COMMANDS.put(name, c);
		COMMANDS.put(mainClass.getSimpleName(), c);
	}

	static {
		register("file-minhash", FileCodeHash.class, FileCodeHash::main, "Compute hashes and minhash of files on disk.");
		register("minhash-compare", ComparisonMain.class, ComparisonMain::main, "Estimate similarity from minhash produced by file-hash.");
		register("file-compare", DirectComparisonMain.class, DirectComparisonMain::main, "Compare files directly and write JSON.");
		register("json-to-matrix", SimilarityMatrixMain.class, SimilarityMatrixMain::main, "Convert a JSON output of compare into a CSV matrix.");
		register("git-file-list", GitFileList.class, GitFileList::main, "List blobs reachable from HEAD of a git repository.");
		register("git-minhash", GitCodeHash.class, GitCodeHash::main, "Compute hashes of blobs in git repositories.");
		register("tokenize", FileTokenizerMain.class, FileTokenizerMain::main, "Write tokens of files in JSON.");
	}

	/**
	 * @param args The first argument specifies a command.  The remaining arguments are passed to the command.
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			printUsage(System.err);
			return;
		}
		if (args[0].equals("help") || args[0].equals("-h") || args[0].equals("--help")) {
			printUsage(System.out);
			return;
		}
		Command c = COMMANDS.get(args[0]);
		if (c == null) {
			System.err.println("Unknown command: " + args[0]);
			printUsage(System.err);
			return;
		}
		c.main.accept(Arrays.copyOfRange(args, 1, args.length));
	}

	private static void printUsage(PrintStream out) {
		out.println("Usage: java -jar CodeHash.jar [COMMAND] [ARGUMENTS...]");
		out.println("Commands:");
		for (String name: COMMANDS.keySet()) {
			Command c = COMMANDS.get(name);
			if (c.name.equals(name)) {
				out.println(String.format("  %-16s %s (%s)", name, c.description, c.mainClass.getName()));
			}
		}
		out.println("See README.md for the arguments of each command.");
	}

}

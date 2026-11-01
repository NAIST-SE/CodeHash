package jp.naist.se.codehash.comparison;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.token.FileType;
import jp.naist.se.codehash.token.TokenReader;

/**
 * A program to see how source files are translated into tokens.
 * It writes the tokens of files in JSON to STDOUT.
 * <p>
 * Command line arguments are file names.
 * "-" represents source code read from STDIN.
 * A programming language is recognized from a file extension; a file in an unsupported language is skipped.
 * The option "-lang:[LANGUAGE]" (e.g. "-lang:JAVA") specifies a programming language for all the files.
 * The option is necessary to read STDIN.
 * </p>
 * <p>
 * The output is a JSON object whose "Files" array includes an object for each file with the following fields.
 * </p>
 * <ul>
 * <li>"Type": the programming language of the file.</li>
 * <li>"Name": the file name ("-" for STDIN).</li>
 * <li>"Tokens": an array of tokens excluding whitespace and comments.  Each token has
 * "TokenType" (a token type defined by the lexer), "Text" (the token as it is),
 * "NormalizedText" (the token after normalization, i.e. translation ignoring differences such as variable names following the rules of the language),
 * "Line" (starting from 1), and "CharPositionInLine" (starting from 0).</li>
 * <li>"IOException": an error message if the file could not be read.</li>
 * </ul>
 */
public class FileTokenizerMain {

	/**
	 * An option to select a programming language.  A language name must follow the option string, e.g. "-lang:JAVA".
	 */
	private static final String LANG_OPTION = "-lang:";

	/**
	 * A file name representing STDIN.
	 */
	private static final String STDIN = "-";

	/**
	 * Print a list of tokens in a JSON format.
	 * @param args specifies options and file names.  See the class description for the details.
	 */
	public static void main(String[] args) {
		FileType lang = null;
		ArrayList<String> filenames = new ArrayList<>();
		for (String arg: args) {
			if (arg.startsWith(LANG_OPTION)) {
				String name = arg.substring(LANG_OPTION.length());
				lang = FileType.getFileType(name);
				if (!FileType.isSupported(lang)) {
					System.err.println("Unsupported language: " + name);
					return;
				}
			} else {
				filenames.add(arg);
			}
		}
		if (lang == null && filenames.contains(STDIN)) {
			System.err.println("Specify a language using " + LANG_OPTION + " to read STDIN.");
			return;
		}

		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(System.out)) {
			gen.writeStartObject();
			gen.writeArrayFieldStart("Files");
			for (String filename: filenames) {
				FileType t = lang != null ? lang : FileType.getFileTypeFromName(filename);
				if (FileType.isSupported(t)) {
					gen.writeStartObject();
					gen.writeStringField("Type", t.name());
					gen.writeStringField("Name", filename);
					try (InputStream input = filename.equals(STDIN) ? System.in : new BufferedInputStream(new FileInputStream(filename))) {
						writeTokens(gen, t, input);
					} catch (IOException e) {
						gen.writeStringField("IOException", e.getMessage());
					}
					gen.writeEndObject();
				}
			}
			gen.writeEndArray();
			gen.writeEndObject();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write the "Tokens" field for the content.
	 * @throws IOException if the content could not be read.  Nothing is written in that case.
	 */
	private static void writeTokens(JsonGenerator gen, FileType t, InputStream input) throws IOException {
		TokenReader r = FileType.createReader(t, input);
		if (r == null) throw new IOException("Failed to read the content");
		gen.writeArrayFieldStart("Tokens");
		while (r.next()) {
			gen.writeStartObject();
			gen.writeNumberField("TokenType", r.getTokenType());
			gen.writeStringField("Text", r.getText());
			gen.writeStringField("NormalizedText", r.getNormalizedText());
			gen.writeNumberField("Line", r.getLine());
			gen.writeNumberField("CharPositionInLine", r.getCharPositionInLine());
			gen.writeEndObject();
		}
		gen.writeEndArray();
	}

}

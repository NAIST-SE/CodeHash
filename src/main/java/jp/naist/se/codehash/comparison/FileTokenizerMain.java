package jp.naist.se.codehash.comparison;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.StringReader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.TokenReader;

public class FileTokenizerMain {

	private static StringReader toReader(String[] args) {
		StringBuilder buf = new StringBuilder();
		for (String arg: args) {
			buf.append(arg);
			buf.append("\n");
		}
		return new StringReader(buf.toString());
	}
	
	/**
	 * Print a list of tokens in a JSON format. 
	 * @param args specifies a file name.  "-" represents STDIN.
	 */
	public static void main(String[] args) {
		LineNumberReader reader;
		if (args.length == 1 && args[0].equals("-")) {
			reader = new LineNumberReader(new InputStreamReader(System.in));
		} else {
			reader = new LineNumberReader(toReader(args));
		}

		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(System.out)) {
			gen.writeStartObject();
			gen.writeArrayFieldStart("Files");
			for (String filename = reader.readLine(); filename != null; filename = reader.readLine()) {
				FileType t = FileType.getFileTypeFromName(filename);
				if (FileType.isSupported(t)) {
					gen.writeStartObject();
					gen.writeStringField("Type", t.name());
					gen.writeStringField("Name", filename);
					gen.writeArrayFieldStart("Tokens");
					try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(filename))) {
						TokenReader r = FileType.createReader(t, input);
						while (r.next()) {
							gen.writeStartObject();
							gen.writeStringField("Text", r.getText());
							gen.writeStringField("NormalizedText", r.getNormalizedText());
							gen.writeNumberField("Line", r.getLine());
							gen.writeNumberField("CharPositionInLine", r.getCharPositionInLine());
							gen.writeEndObject();
						}
						gen.writeEndArray();
					} catch (IOException e) {
						gen.writeEndArray();
						gen.writeStringField("IOException", e.getMessage());
					}
					gen.writeEndObject();
				}
			}
			gen.writeEndArray();
			gen.writeEndObject();
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
		}
	}

}

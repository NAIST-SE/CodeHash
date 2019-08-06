package jp.naist.se.codehash.comparison;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import jp.naist.se.codehash.FileType;
import jp.naist.se.codehash.TokenReader;

public class FileTokenizerMain {

	public static void main(String[] args) {
		JsonFactory f = new JsonFactory();
		try (JsonGenerator gen = f.createGenerator(System.out)) {
			gen.writeStartObject();
			gen.writeArrayFieldStart("Files");
			for (String filename: args) {
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

package jp.naist.se.codehash;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.antlr.v4.runtime.CaseChangingCharStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import jp.naist.se.codehash.AntlrTokenReader.Filter;
import jp.naist.se.codehash.normalizer.CPP14Normalizer;
import jp.naist.se.codehash.normalizer.CSharpNormalizer;
import jp.naist.se.codehash.normalizer.ECMAScriptNormalizer;
import jp.naist.se.codehash.normalizer.Java8Normalizer;
import jp.naist.se.codehash.normalizer.Normalizer;
import jp.naist.se.codehash.normalizer.Python3Normalizer;
import jp.naist.se.commentlister.lexer.CPP14Lexer;
import jp.naist.se.commentlister.lexer.CSharpLexer;
import jp.naist.se.commentlister.lexer.ECMAScriptLexer;
import jp.naist.se.commentlister.lexer.Java8Lexer;
import jp.naist.se.commentlister.lexer.PhpLexer;
import jp.naist.se.commentlister.lexer.Python3Lexer;
import jp.naist.se.commentlister.lexer.Python3Parser;


public enum FileType {

	UNSUPPORTED, CPP, JAVA, ECMASCRIPT, PYTHON, PHP, CSHARP;

	private static HashMap<String, FileType> filetype;
	private static HashMap<String, FileType> extToFiletype;
	
	static {
		filetype = new HashMap<>(64);
		filetype.put("C", FileType.CPP);
		filetype.put("CPP", FileType.CPP);
		filetype.put("JAVA", FileType.JAVA);
		filetype.put("JAVASCRIPT", FileType.ECMASCRIPT);
		filetype.put("PYTHON", FileType.PYTHON);
		filetype.put("PHP", FileType.PHP);
		filetype.put("CSHARP", FileType.CSHARP);

		extToFiletype = new HashMap<>(128);
		extToFiletype.put("c", FileType.CPP);
		extToFiletype.put("cc", FileType.CPP);
		extToFiletype.put("cp", FileType.CPP);
		extToFiletype.put("cpp", FileType.CPP);
		extToFiletype.put("cx", FileType.CPP);
		extToFiletype.put("cxx", FileType.CPP);
		extToFiletype.put("c+", FileType.CPP);
		extToFiletype.put("c++", FileType.CPP);
		extToFiletype.put("h", FileType.CPP);
		extToFiletype.put("hh", FileType.CPP);
		extToFiletype.put("hxx", FileType.CPP);
		extToFiletype.put("h+", FileType.CPP);
		extToFiletype.put("h++", FileType.CPP);
		extToFiletype.put("hp", FileType.CPP);
		extToFiletype.put("hpp", FileType.CPP);
		extToFiletype.put("java", FileType.JAVA);
		extToFiletype.put("js", FileType.ECMASCRIPT);
		extToFiletype.put("py", FileType.PYTHON);
		extToFiletype.put("php", FileType.PHP);
		extToFiletype.put("cs", FileType.CSHARP);

	}
	
	/**
	 * This method returns a FileType object representing a given language name.
	 * @param typename should be one of "C", "CPP", "JAVA", "JAVASCRIPT", "PYTHON", "PHP", and "CSHARP". 
	 * @return a FileType instance.
	 * UNSUPPORTED is returned for an invalid argument.
	 */
	public static FileType getFileType(String typename) {
		FileType type = filetype.get(typename);
		if (type != null) {
			return type;
		} else {
			return FileType.UNSUPPORTED;
		}
	}

	/**
	 * This method returns a FileType object for a file name based on its extension.
	 * @param filename specifies a source file name.
	 * @return a FileType object.  
	 * UNSUPPORTED is returned if the extension is not supported by the method.
	 */
	public static FileType getFileTypeFromName(String filename) {
		int idx = filename.lastIndexOf('.');
		if (idx >= 0) {
			String ext = filename.substring(idx+1).toLowerCase();
			FileType type = extToFiletype.get(ext);
			if (type != null) {
				return type;
			}
		}
		return FileType.UNSUPPORTED;
	}

	/**
	 * @param filetype
	 * @return true if the given type is UNSUPPORTED.
	 */
	public static boolean isSupported(FileType filetype) {
		return filetype != FileType.UNSUPPORTED;
	}

	/**
	 * Create a reader object
	 * @param filetype specifies a programming language.
	 * @param input is a stream of the content to be processed.
	 * @return a TokenReader object to read the input
	 */
	public static TokenReader createReader(FileType filetype, InputStream input) {
		try {
			CharStream stream = CharStreams.fromStream(input);
			switch (filetype) {
			case JAVA:
			{
				Java8Lexer lexer = new Java8Lexer(stream);
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() != Java8Lexer.HIDDEN;
					}
				}, new Java8Normalizer());
			}
			case CPP:
			{
				CPP14Lexer lexer = new CPP14Lexer(stream);
				//CommonTokenStream c = new CommonTokenStream(lexer, Java8Lexer.HIDDEN);
				//System.out.println(c.size());
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() != CPP14Lexer.HIDDEN;
					}
				}, new CPP14Normalizer());
			}
			case ECMASCRIPT:
			{
				ECMAScriptLexer lexer = new ECMAScriptLexer(stream);
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() != ECMAScriptLexer.HIDDEN;
					}
				}, new ECMAScriptNormalizer());
			}
			case PYTHON:
			{
				Python3Lexer lexer = new Python3Lexer(stream);
				return new Python3LexerReader(lexer);
			}
			case PHP:
			{
				PhpLexer lexer = new PhpLexer(new CaseChangingCharStream(stream, false));
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() != PhpLexer.PhpComments &&
								t.getChannel() != PhpLexer.SkipChannel &&
								t.getChannel() != PhpLexer.ErrorLexem;
					}
				}, null);
			}
			case CSHARP:
			{
				CSharpLexer lexer = new CSharpLexer(stream);
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return (t.getChannel() != CSharpLexer.HIDDEN) &&
								(t.getChannel() != CSharpLexer.COMMENTS_CHANNEL);
					}
				}, new CSharpNormalizer());
			}
			default:
				return null;
			
			}
		} catch (IOException e) {
			return null;
		}
	}
	
	/**
	 * An internal class to process Python files.
	 */
	private static class Python3LexerReader extends AntlrTokenReader {
		public Python3LexerReader(Lexer lexer) {
			super(lexer, new AntlrTokenReader.Filter() {
				@Override
				public boolean accept(Token t) {
					return (t.getChannel() != Python3Lexer.HIDDEN) &&
							(t.getType() != Python3Lexer.NEWLINE); 
							//(t.getType() != Python3Parser.INDENT) &&
							//(t.getType() != Python3Parser.DEDENT);
				}
			}, new Python3Normalizer());
		}
		/**
		 * Overriding tokens 
		 */
		@Override
		public String getText() {
			if (getTokenType() == Python3Parser.INDENT) {
				return "<INDENT>";
			} else if (getTokenType() == Python3Parser.DEDENT) {
				return "<DEDENT>";
			}
			return super.getText();
		}
		@Override
		public String getNormalizedText() {
			if (getTokenType() == Python3Parser.INDENT) {
				return "<INDENT>";
			} else if (getTokenType() == Python3Parser.DEDENT) {
				return "<DEDENT>";
			}
			return super.getNormalizedText();
		}
	}

}

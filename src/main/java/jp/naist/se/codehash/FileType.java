package jp.naist.se.codehash;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;

import org.antlr.v4.runtime.CaseChangingCharStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import jp.naist.se.commentlister.lexer.CPP14Lexer;
import jp.naist.se.commentlister.lexer.ECMAScriptLexer;
import jp.naist.se.commentlister.lexer.Java8Lexer;
import jp.naist.se.commentlister.lexer.PhpLexer;
import jp.naist.se.commentlister.lexer.Python3Lexer;


public enum FileType {

	UNSUPPORTED, CPP, JAVA, ECMASCRIPT, CSHARP, PYTHON, PHP, RUBY;

	private static HashMap<String, FileType> filetype;
	static {
		filetype = new HashMap<>(64);
//		filetype.put("c", FileType.CPP);
//		filetype.put("cc", FileType.CPP);
//		filetype.put("cp", FileType.CPP);
//		filetype.put("cpp", FileType.CPP);
//		filetype.put("cx", FileType.CPP);
//		filetype.put("cxx", FileType.CPP);
//		filetype.put("c+", FileType.CPP);
//		filetype.put("c++", FileType.CPP);
//		filetype.put("h", FileType.CPP);
//		filetype.put("hh", FileType.CPP);
//		filetype.put("hxx", FileType.CPP);
//		filetype.put("h+", FileType.CPP);
//		filetype.put("h++", FileType.CPP);
//		filetype.put("hp", FileType.CPP);
//		filetype.put("hpp", FileType.CPP);
//		filetype.put("java", FileType.JAVA);
//		filetype.put("js", FileType.ECMASCRIPT);
//		filetype.put("cs", FileType.CSHARP);
//		filetype.put("py", FileType.PYTHON);
//		filetype.put("php", FileType.PHP);
//		filetype.put("rb", FileType.RUBY);
		
		filetype.put("C", FileType.CPP);
		filetype.put("JAVA", FileType.JAVA);
		filetype.put("JAVASCRIPT", FileType.ECMASCRIPT);
		filetype.put("PYTHON", FileType.PYTHON);
		filetype.put("PHP", FileType.PHP);
	}
	
	public static FileType getFileType(String typename) {
		FileType type = filetype.get(typename);
		if (type != null) {
			return type;
		} else {
			return FileType.UNSUPPORTED;
		}
	}

	public static boolean isSupported(String filename) {
		return isSupported(getFileType(filename));
	}

	public static boolean isSupported(FileType filetype) {
		return filetype != FileType.UNSUPPORTED;
	}

	/**
	 * Create a stream for an ANTLR lexer.
	 * This method handles UTF-8/16 BOM.
	 * @param buf bytes be parsed.
	 * @return an instance of ANTLR CharStream.
	 * @throws IOException may be thrown if instantiation failed.
	 */
	private static CharStream createStream(byte[] buf) throws IOException {
		if (buf.length >= 3 && 
			buf[0] == (byte)0xEF && buf[1] == (byte)0xBB && buf[2] == (byte)0xBF) {
			return CharStreams.fromStream(new ByteArrayInputStream(buf, 3, buf.length-3));
		} else if (buf.length >= 2 && buf[0] == (byte)0xFE && buf[1] == (byte)0xFF) {
			return CharStreams.fromStream(new ByteArrayInputStream(buf, 2, buf.length-2), Charset.forName("UTF-16BE"));
		} else if (buf.length >= 2 && buf[0] == (byte)0xFF && buf[1] == (byte)0xFE) {
			return CharStreams.fromStream(new ByteArrayInputStream(buf, 2, buf.length-2), Charset.forName("UTF-16LE"));
		} else {
			return CharStreams.fromStream(new ByteArrayInputStream(buf));
		}
	}
	
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
				});
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
				});
			}
			case ECMASCRIPT:
			{
				ECMAScriptLexer lexer = new ECMAScriptLexer(stream);
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return t.getChannel() != ECMAScriptLexer.HIDDEN;
					}
				});
			}
			case PYTHON:
			{
				Python3Lexer lexer = new Python3Lexer(stream);
				return new AntlrTokenReader(lexer, new AntlrTokenReader.Filter() {
					@Override
					public boolean accept(Token t) {
						return (t.getChannel() != Python3Lexer.HIDDEN);
					}
				});
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
				});
			}
			case RUBY:
			{
				// unsupported 
				return null;
			}
			default:
				return null;
			
			}
		} catch (IOException e) {
			return null;
		}
	}

}

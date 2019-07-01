package jp.naist.se.codehash.normalizer;


import org.antlr.v4.runtime.Token;

import jp.naist.se.commentlister.lexer.CPP14Lexer;


public class CPP14Normalizer implements Normalizer {

	public static final String NORMALIZED_TOKEN = "$p";
	/**
	 * Ignore the differences of identifiers and literal
	 * but keep type names
	 */
	@Override
	public String normalize(Token t) {
		switch (t.getType()) {
		case CPP14Lexer.Identifier:
		case CPP14Lexer.Binaryliteral:
		case CPP14Lexer.Characterliteral:
		case CPP14Lexer.Decimalliteral:
		case CPP14Lexer.Floatingliteral:
		case CPP14Lexer.Hexadecimalliteral:
		case CPP14Lexer.Integerliteral:
		case CPP14Lexer.Octalliteral:
		case CPP14Lexer.Stringliteral:
		case CPP14Lexer.Userdefinedcharacterliteral:
		case CPP14Lexer.Userdefinedfloatingliteral:
		case CPP14Lexer.Userdefinedintegerliteral:
		case CPP14Lexer.Userdefinedstringliteral:
			return NORMALIZED_TOKEN;
		}
		return t.getText();
	}
}

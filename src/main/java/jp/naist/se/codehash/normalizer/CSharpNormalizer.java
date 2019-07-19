package jp.naist.se.codehash.normalizer;

import org.antlr.v4.runtime.Token;

import jp.naist.se.commentlister.lexer.CSharpLexer;

public class CSharpNormalizer implements Normalizer {

	public static final String NORMALIZED_TOKEN = "$p";
	/**
	 * Ignore the differences of identifiers and literal
	 * but keep type names
	 */
	@Override
	public String normalize(Token t) {
		switch (t.getType()) {
		case CSharpLexer.IDENTIFIER:
		case CSharpLexer.CHARACTER_LITERAL:
		case CSharpLexer.INTEGER_LITERAL:
		case CSharpLexer.HEX_INTEGER_LITERAL:
		case CSharpLexer.REAL_LITERAL:
		case CSharpLexer.REGULAR_STRING:
			return NORMALIZED_TOKEN;
		}
		return t.getText();
	}

}

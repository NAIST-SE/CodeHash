package jp.naist.se.codehash.normalizer;


import org.antlr.v4.runtime.Token;

import jp.naist.se.commentlister.lexer.ECMAScriptLexer;


public class ECMAScriptNormalizer implements Normalizer {

	public static final String NORMALIZED_TOKEN = "$p";

	/**
	 * A simple normalization for Java8.
	 * This does not include a CCFinder's normalization rule
	 * that replaces a dotted expression (x.y.z --> $p). 
	 */
	@Override
	public String normalize(Token t) {
		switch (t.getType()) {
		case ECMAScriptLexer.Identifier:
			return NORMALIZED_TOKEN;
		}
		return t.getText();
	}
}

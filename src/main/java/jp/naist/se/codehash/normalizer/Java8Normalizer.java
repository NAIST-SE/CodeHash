package jp.naist.se.codehash.normalizer;


import org.antlr.v4.runtime.Token;

import jp.naist.se.commentlister.lexer.Java8Lexer;


public class Java8Normalizer implements Normalizer {

	/**
	 * A simple normalization for Java8.
	 * This does not include a CCFinder's normalization rule
	 * that replaces a dotted expression (x.y.z --> $p). 
	 */
	@Override
	public String normalize(Token t) {
		switch (t.getType()) {
		case Java8Lexer.Identifier:
		case Java8Lexer.IntegerLiteral:
		case Java8Lexer.FloatingPointLiteral:
		case Java8Lexer.BooleanLiteral: 
		case Java8Lexer.CharacterLiteral:
		case Java8Lexer.StringLiteral:
		case Java8Lexer.NullLiteral: 
			return "$p";
		}
		return t.getText();
	}
}

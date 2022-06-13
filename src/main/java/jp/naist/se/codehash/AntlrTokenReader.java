package jp.naist.se.codehash;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import jp.naist.se.codehash.normalizer.Normalizer;

public class AntlrTokenReader implements TokenReader {

	private Lexer lexer;
	private Filter filter;
	private Normalizer normalizer;
	private Token current;
	
	private int tokenCount;
	
	public AntlrTokenReader(Lexer lexer, Filter filter, Normalizer normalizer) {
		this.lexer = lexer;
		this.filter = filter;
		this.normalizer = normalizer;
		this.tokenCount = 0;
	}
	
	@Override
	public boolean next() {
		current = lexer.nextToken();
		while (!filter.accept(current) && current.getType() != Lexer.EOF) {
			current = lexer.nextToken();
		}
		boolean hasToken = current.getType() != Lexer.EOF;
		if (hasToken) tokenCount++;
		return hasToken;
	}
	
	@Override
	public String getText() {
		return current.getText();
	}
	
	@Override
	public int getTokenType() {
		return current.getType();
	}
	
	@Override
	public String getNormalizedText() {
		if (normalizer != null) {
			return normalizer.normalize(current);
		} else {
			return getText();
		}
	}
	
	@Override
	public int getLine() {
		return current.getLine();
	}
	
	@Override
	public int getCharPositionInLine() {
		return current.getCharPositionInLine();
	}
	
	@Override
	public int getTokenCount() {
		return tokenCount;
	}
	
	public interface Filter {
		boolean accept(Token t);
	}
}

package jp.naist.se.codehash;


public interface TokenReader {

	public boolean next();
	public String getText();
	public int getLine();
	public int getCharPositionInLine();

}

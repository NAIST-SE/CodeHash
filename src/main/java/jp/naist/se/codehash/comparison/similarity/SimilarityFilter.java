package jp.naist.se.codehash.comparison.similarity;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * A parser of filter expressions to select file pairs to be reported.
 *
 * Grammar:
 * <pre>
 *   expr       := and ( ("||" | "or") and )*
 *   and        := unary ( ("&amp;&amp;" | "and") unary )*
 *   unary      := ("!" | "not") unary | "(" expr ")" | comparison
 *   comparison := operand ( "&gt;=" | "&gt;" | "&lt;=" | "&lt;" | "==" | "=" | "!=" ) operand
 *   operand    := metric-name | number
 * </pre>
 * Example: <code>jaccard&gt;=0.7 or (overlap-coefficient&gt;=0.9 and exact-jaccard&lt;0.5)</code>.
 * Keywords are case-insensitive.
 * Any comparison with NaN (e.g. similarity of empty files) is false except for "!=".
 */
public class SimilarityFilter {

	private enum TokenType { LPAREN, RPAREN, AND, OR, NOT, OP, NUMBER, NAME, END }

	private static class Token {
		TokenType type;
		String text;
		int pos;

		Token(TokenType type, String text, int pos) {
			this.type = type;
			this.text = text;
			this.pos = pos;
		}
	}

	/**
	 * An operand of a comparison: a metric or a constant.
	 */
	private interface Operand {
		double getValue(SimilarityRecord r);
	}

	private String source;
	private ArrayList<Token> tokens;
	private int index;

	/**
	 * Parse a filter expression.
	 * @param expression
	 * @return a predicate that returns true if a file pair satisfies the condition.
	 * @throws IllegalArgumentException if the expression is invalid or includes an unknown metric name.
	 */
	public static Predicate<SimilarityRecord> parse(String expression) {
		SimilarityFilter parser = new SimilarityFilter(expression);
		Predicate<SimilarityRecord> p = parser.parseOr();
		parser.expect(TokenType.END);
		return p;
	}

	private SimilarityFilter(String expression) {
		this.source = expression;
		this.tokens = tokenize(expression);
		this.index = 0;
	}

	private Predicate<SimilarityRecord> parseOr() {
		Predicate<SimilarityRecord> left = parseAnd();
		while (peek().type == TokenType.OR) {
			index++;
			left = left.or(parseAnd());
		}
		return left;
	}

	private Predicate<SimilarityRecord> parseAnd() {
		Predicate<SimilarityRecord> left = parseUnary();
		while (peek().type == TokenType.AND) {
			index++;
			left = left.and(parseUnary());
		}
		return left;
	}

	private Predicate<SimilarityRecord> parseUnary() {
		Token t = peek();
		if (t.type == TokenType.NOT) {
			index++;
			return parseUnary().negate();
		} else if (t.type == TokenType.LPAREN) {
			index++;
			Predicate<SimilarityRecord> p = parseOr();
			expect(TokenType.RPAREN);
			return p;
		} else {
			return parseComparison();
		}
	}

	private Predicate<SimilarityRecord> parseComparison() {
		Operand left = parseOperand();
		String op = expect(TokenType.OP).text;
		Operand right = parseOperand();
		switch (op) {
		case ">=": return r -> left.getValue(r) >= right.getValue(r);
		case ">":  return r -> left.getValue(r) > right.getValue(r);
		case "<=": return r -> left.getValue(r) <= right.getValue(r);
		case "<":  return r -> left.getValue(r) < right.getValue(r);
		case "=":
		case "==": return r -> left.getValue(r) == right.getValue(r);
		case "!=": return r -> left.getValue(r) != right.getValue(r);
		default: throw new AssertionError("Unknown operator: " + op);
		}
	}

	private Operand parseOperand() {
		Token t = peek();
		if (t.type == TokenType.NUMBER) {
			index++;
			try {
				double v = Double.parseDouble(t.text);
				return r -> v;
			} catch (NumberFormatException e) {
				throw error(t, "Invalid number: " + t.text);
			}
		} else if (t.type == TokenType.NAME) {
			index++;
			SimilarityMetric m = SimilarityMetrics.get(t.text);
			if (m == null) {
				throw new IllegalArgumentException("Unknown metric '" + t.text + "' at position " + t.pos + " in \"" + source + "\" (available: " + String.join(",", SimilarityMetrics.getNames()) + ")");
			}
			return r -> r.getValue(m);
		} else {
			throw error(t, "A metric name or a number is expected");
		}
	}

	private Token peek() {
		return tokens.get(index);
	}

	private Token expect(TokenType type) {
		Token t = peek();
		if (t.type != type) {
			throw error(t, type + " is expected");
		}
		index++;
		return t;
	}

	private IllegalArgumentException error(Token t, String message) {
		String found = t.type == TokenType.END ? "end of expression" : "'" + t.text + "'";
		return new IllegalArgumentException(message + " but found " + found + " at position " + t.pos + " in \"" + source + "\"");
	}

	private static boolean isNameStart(char c) {
		return Character.isLetter(c) || c == '_';
	}

	private static boolean isNamePart(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '-';
	}

	private static ArrayList<Token> tokenize(String s) {
		ArrayList<Token> result = new ArrayList<>();
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			int start = i;
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '(') {
				result.add(new Token(TokenType.LPAREN, "(", start));
				i++;
			} else if (c == ')') {
				result.add(new Token(TokenType.RPAREN, ")", start));
				i++;
			} else if (s.startsWith("&&", i)) {
				result.add(new Token(TokenType.AND, "&&", start));
				i += 2;
			} else if (s.startsWith("||", i)) {
				result.add(new Token(TokenType.OR, "||", start));
				i += 2;
			} else if (s.startsWith(">=", i) || s.startsWith("<=", i) || s.startsWith("==", i) || s.startsWith("!=", i)) {
				result.add(new Token(TokenType.OP, s.substring(i, i+2), start));
				i += 2;
			} else if (c == '>' || c == '<' || c == '=') {
				result.add(new Token(TokenType.OP, String.valueOf(c), start));
				i++;
			} else if (c == '!') {
				result.add(new Token(TokenType.NOT, "!", start));
				i++;
			} else if (Character.isDigit(c) || c == '.') {
				while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
				// exponent part, e.g. "1e-3"
				if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
					i++;
					if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
					while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
				}
				result.add(new Token(TokenType.NUMBER, s.substring(start, i), start));
			} else if (isNameStart(c)) {
				while (i < s.length() && isNamePart(s.charAt(i))) i++;
				String name = s.substring(start, i);
				if (name.equalsIgnoreCase("and")) {
					result.add(new Token(TokenType.AND, name, start));
				} else if (name.equalsIgnoreCase("or")) {
					result.add(new Token(TokenType.OR, name, start));
				} else if (name.equalsIgnoreCase("not")) {
					result.add(new Token(TokenType.NOT, name, start));
				} else {
					result.add(new Token(TokenType.NAME, name, start));
				}
			} else {
				throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + start + " in \"" + s + "\"");
			}
		}
		result.add(new Token(TokenType.END, "", s.length()));
		return result;
	}

}

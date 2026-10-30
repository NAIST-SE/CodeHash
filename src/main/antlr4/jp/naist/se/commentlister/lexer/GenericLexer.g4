/*
 * A generic lexer for plain text.
 * Copied from https://github.com/takashi-ishio/NCDSearch/blob/main/grammars/sarf/lexer/lang/GenericLexer.g4
 */
lexer grammar GenericLexer;

Word: [a-zA-Z0-9_]+;

Symbol: ~[a-zA-Z0-9_ \t\r\n];

WS: [ \t\r\n\f]+ -> skip;

ErrorCharacter: .;

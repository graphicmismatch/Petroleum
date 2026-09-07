package JSynth.Expression;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String source;
    private int pos = 0;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = next();
            tokens.add(token);
        } while (token.type != TokenType.EOF);
        return tokens;
    }

    private Token next() {
        skipWhitespace();
        if (pos >= source.length()) {
            return new Token(TokenType.EOF, "", pos);
        }

        int start = pos;
        char c = source.charAt(pos);

        if (Character.isDigit(c) || (c == '.' && pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1)))) {
            return readNumber();
        }
        if (Character.isLetter(c) || c == '_') {
            return readIdentifier();
        }

        pos++;
        switch (c) {
            case '+': return new Token(TokenType.PLUS, "+", start);
            case '-': return new Token(TokenType.MINUS, "-", start);
            case '*': return new Token(TokenType.STAR, "*", start);
            case '/': return new Token(TokenType.SLASH, "/", start);
            case '%': return new Token(TokenType.PERCENT, "%", start);
            case '^': return new Token(TokenType.CARET, "^", start);
            case '(': return new Token(TokenType.LPAREN, "(", start);
            case ')': return new Token(TokenType.RPAREN, ")", start);
            case ',': return new Token(TokenType.COMMA, ",", start);
            default:
                throw new ExpressionException("Unexpected character '" + c + "'", start);
        }
    }

    private void skipWhitespace() {
        while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
            pos++;
        }
    }

    private Token readNumber() {
        int start = pos;
        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
            pos++;
        }
        if (pos < source.length() && source.charAt(pos) == '.') {
            pos++;
            while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                pos++;
            }
        }
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            int mark = pos;
            pos++;
            if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                    pos++;
                }
            } else {
                pos = mark;
            }
        }
        String text = source.substring(start, pos);
        try {
            return new Token(TokenType.NUMBER, text, Double.parseDouble(text), start);
        } catch (NumberFormatException e) {
            throw new ExpressionException("Invalid number literal '" + text + "'", start);
        }
    }

    private Token readIdentifier() {
        int start = pos;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            pos++;
        }
        return new Token(TokenType.IDENTIFIER, source.substring(start, pos), start);
    }
}

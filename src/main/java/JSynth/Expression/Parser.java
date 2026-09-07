package JSynth.Expression;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int index = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Expr parse(String source) {
        List<Token> tokens = new Lexer(source).tokenize();
        Parser parser = new Parser(tokens);
        Expr expr = parser.parseExpression();
        parser.expect(TokenType.EOF, "Unexpected trailing input");
        return expr;
    }

    private Expr parseExpression() {
        Expr expr = parseTerm();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            Expr right = parseTerm();
            expr = new BinaryExpr(op.type, expr, right, op.position);
        }
        return expr;
    }

    private Expr parseTerm() {
        Expr expr = parseUnary();
        while (true) {
            if (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
                Token op = advance();
                Expr right = parseUnary();
                expr = new BinaryExpr(op.type, expr, right, op.position);
            } else if (startsAtom(peek())) {
                int position = peek().position;
                Expr right = parseUnary();
                expr = new BinaryExpr(TokenType.STAR, expr, right, position);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr parseUnary() {
        if (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            Expr operand = parseUnary();
            return new UnaryExpr(op.type, operand);
        }
        return parsePower();
    }

    private Expr parsePower() {
        Expr base = parseAtom();
        if (check(TokenType.CARET)) {
            Token op = advance();
            Expr exponent = parseUnary();
            return new BinaryExpr(TokenType.CARET, base, exponent, op.position);
        }
        return base;
    }

    private Expr parseAtom() {
        Token t = peek();
        switch (t.type) {
            case NUMBER:
                advance();
                return new NumberExpr(t.numberValue);
            case IDENTIFIER: {
                advance();
                if (check(TokenType.LPAREN)) {
                    advance();
                    List<Expr> args = new ArrayList<>();
                    if (!check(TokenType.RPAREN)) {
                        args.add(parseExpression());
                        while (check(TokenType.COMMA)) {
                            advance();
                            args.add(parseExpression());
                        }
                    }
                    expect(TokenType.RPAREN, "Expected ')' after arguments to '" + t.text + "'");
                    return new FunctionCallExpr(t.text, args, t.position);
                }
                return new VariableExpr(t.text, t.position);
            }
            case LPAREN: {
                advance();
                Expr inner = parseExpression();
                expect(TokenType.RPAREN, "Expected ')'");
                return inner;
            }
            default:
                throw new ExpressionException("Unexpected token '" + t.text + "'", t.position);
        }
    }

    private boolean startsAtom(Token t) {
        return t.type == TokenType.NUMBER || t.type == TokenType.IDENTIFIER || t.type == TokenType.LPAREN;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private boolean check(TokenType type) {
        return peek().type == type;
    }

    private Token advance() {
        Token t = tokens.get(index);
        if (index < tokens.size() - 1) {
            index++;
        }
        return t;
    }

    private void expect(TokenType type, String message) {
        if (!check(type)) {
            throw new ExpressionException(message + ", found '" + peek().text + "'", peek().position);
        }
        advance();
    }
}

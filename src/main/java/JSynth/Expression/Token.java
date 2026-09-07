package JSynth.Expression;

public class Token {
    public final TokenType type;
    public final String text;
    public final double numberValue;
    public final int position;

    public Token(TokenType type, String text, int position) {
        this(type, text, 0.0, position);
    }

    public Token(TokenType type, String text, double numberValue, int position) {
        this.type = type;
        this.text = text;
        this.numberValue = numberValue;
        this.position = position;
    }
}

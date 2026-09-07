package JSynth.Expression;

public class ExpressionException extends RuntimeException {
    public ExpressionException(String message, int position) {
        super(message + " (at position " + position + ")");
    }
}

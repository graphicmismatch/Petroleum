package Petroleum.Expression;

public class FunctionSpec {
    public final int minArity;
    public final int maxArity;
    public final MathFunction implementation;

    public FunctionSpec(int minArity, int maxArity, MathFunction implementation) {
        this.minArity = minArity;
        this.maxArity = maxArity;
        this.implementation = implementation;
    }
}

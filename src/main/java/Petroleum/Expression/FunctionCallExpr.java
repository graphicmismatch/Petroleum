package Petroleum.Expression;

import java.util.List;

public class FunctionCallExpr implements Expr {
    private final String name;
    private final List<Expr> args;
    private final int position;
    private final FunctionSpec spec;
    private final double[] argValues;

    public FunctionCallExpr(String name, List<Expr> args, int position) {
        this.name = name;
        this.args = args;
        this.position = position;
        this.spec = Functions.get(name);
        if (spec == null) {
            throw new ExpressionException("Unknown function '" + name + "'", position);
        }
        if (args.size() < spec.minArity || args.size() > spec.maxArity) {
            throw new ExpressionException("Function '" + name + "' takes "
                    + arityDescription() + " argument(s), got " + args.size(), position);
        }
        this.argValues = new double[args.size()];
    }

    private String arityDescription() {
        if (spec.minArity == spec.maxArity) {
            return String.valueOf(spec.minArity);
        }
        if (spec.maxArity == Integer.MAX_VALUE) {
            return "at least " + spec.minArity;
        }
        return spec.minArity + "-" + spec.maxArity;
    }

    @Override
    public double evaluate(double[] slots) {
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = args.get(i).evaluate(slots);
        }
        return spec.implementation.apply(argValues);
    }
}

package Petroleum.Expression;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class Functions {
    private static final Map<String, FunctionSpec> REGISTRY = new HashMap<>();
    private static final Random RANDOM = new Random();

    static {
        unary("sin", Math::sin);
        unary("cos", Math::cos);
        unary("tan", Math::tan);
        unary("asin", Math::asin);
        unary("acos", Math::acos);
        unary("atan", Math::atan);
        unary("sinh", Math::sinh);
        unary("cosh", Math::cosh);
        unary("tanh", Math::tanh);
        unary("sqrt", Math::sqrt);
        unary("abs", Math::abs);
        unary("exp", Math::exp);
        unary("ln", Math::log);
        unary("log", Math::log);
        unary("log10", Math::log10);
        unary("floor", Math::floor);
        unary("ceil", Math::ceil);
        unary("round", a -> (double) Math.round(a));
        unary("sign", Math::signum);
        unary("frac", a -> a - Math.floor(a));

        binary("atan2", Math::atan2);
        binary("pow", Math::pow);
        binary("mod", (a, b) -> a % b);

        REGISTRY.put("min", new FunctionSpec(1, Integer.MAX_VALUE, args -> {
            double result = args[0];
            for (double arg : args) {
                result = Math.min(result, arg);
            }
            return result;
        }));
        REGISTRY.put("max", new FunctionSpec(1, Integer.MAX_VALUE, args -> {
            double result = args[0];
            for (double arg : args) {
                result = Math.max(result, arg);
            }
            return result;
        }));
        REGISTRY.put("clamp", new FunctionSpec(3, 3, args -> Math.max(args[1], Math.min(args[2], args[0]))));
        REGISTRY.put("rand", new FunctionSpec(0, 0, args -> RANDOM.nextDouble() * 2.0 - 1.0));
    }

    private Functions() {
    }

    private static void unary(String name, java.util.function.DoubleUnaryOperator fn) {
        REGISTRY.put(name, new FunctionSpec(1, 1, args -> fn.applyAsDouble(args[0])));
    }

    private static void binary(String name, java.util.function.DoubleBinaryOperator fn) {
        REGISTRY.put(name, new FunctionSpec(2, 2, args -> fn.applyAsDouble(args[0], args[1])));
    }

    public static FunctionSpec get(String name) {
        return REGISTRY.get(name);
    }
}

package Petroleum.Expression;

import java.util.HashMap;
import java.util.Map;

public final class Constants {
    private static final Map<String, Double> VALUES = new HashMap<>();

    static {
        VALUES.put("pi", Math.PI);
        VALUES.put("e", Math.E);
        VALUES.put("tau", 2 * Math.PI);
    }

    private Constants() {
    }

    public static Double get(String name) {
        return VALUES.get(name);
    }
}

package Petroleum.Graph;

public final class Port {
    private final String name;
    private final float defaultValue;

    public Port(String name, float defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String name() {
        return name;
    }

    public float defaultValue() {
        return defaultValue;
    }
}

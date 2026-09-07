package JSynth.Serialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, 0);
        return out.toString();
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new SerializationException("Trailing content at position " + parser.position());
        }
        return value;
    }

    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map)) {
            throw new SerializationException("Expected an object, found " + describe(value));
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (!(value instanceof List)) {
            throw new SerializationException("Expected an array, found " + describe(value));
        }
        return (List<Object>) value;
    }

    public static String asString(Object value) {
        if (!(value instanceof String s)) {
            throw new SerializationException("Expected a string, found " + describe(value));
        }
        return s;
    }

    public static double asDouble(Object value) {
        if (!(value instanceof Number n)) {
            throw new SerializationException("Expected a number, found " + describe(value));
        }
        return n.doubleValue();
    }

    public static String string(Map<String, Object> object, String key) {
        return asString(require(object, key));
    }

    public static double number(Map<String, Object> object, String key) {
        return asDouble(require(object, key));
    }

    public static String stringOrDefault(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value == null ? fallback : asString(value);
    }

    public static double numberOrDefault(Map<String, Object> object, String key, double fallback) {
        Object value = object.get(key);
        return value == null ? fallback : asDouble(value);
    }

    private static Object require(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            throw new SerializationException("Missing required key '" + key + "'");
        }
        return value;
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return "an object";
        }
        if (value instanceof List) {
            return "an array";
        }
        return value.getClass().getSimpleName();
    }

    private static void writeValue(Object value, StringBuilder out, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, out, depth);
        } else if (value instanceof List<?> list) {
            writeArray(list, out, depth);
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b.booleanValue());
        } else if (value instanceof Number n) {
            writeNumber(n, out);
        } else {
            throw new SerializationException("Cannot serialize " + value.getClass().getName());
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int remaining = map.size();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, depth + 1);
            writeString(String.valueOf(entry.getKey()), out);
            out.append(": ");
            writeValue(entry.getValue(), out, depth + 1);
            if (--remaining > 0) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, depth);
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(out, depth + 1);
            writeValue(list.get(i), out, depth + 1);
            if (i < list.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        indent(out, depth);
        out.append(']');
    }

    private static void writeNumber(Number n, StringBuilder out) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new SerializationException("Cannot serialize non-finite number " + d);
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            out.append((long) d);
        } else if (n instanceof Float f) {
            out.append(f.floatValue());
        } else {
            out.append(d);
        }
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        int position() {
            return index;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        Object readValue() {
            if (atEnd()) {
                throw new SerializationException("Unexpected end of input");
            }
            char c = text.charAt(index);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, readValue());
                skipWhitespace();
                char c = peek();
                index++;
                if (c == '}') {
                    return result;
                }
                if (c != ',') {
                    throw new SerializationException("Expected ',' or '}' at position " + (index - 1));
                }
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(readValue());
                skipWhitespace();
                char c = peek();
                index++;
                if (c == ']') {
                    return result;
                }
                if (c != ',') {
                    throw new SerializationException("Expected ',' or ']' at position " + (index - 1));
                }
            }
        }

        String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new SerializationException("Unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new SerializationException("Unknown escape '\\" + escape + "'");
                }
            }
        }

        Boolean readBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new SerializationException("Invalid literal at position " + index);
        }

        Object readNull() {
            if (!text.startsWith("null", index)) {
                throw new SerializationException("Invalid literal at position " + index);
            }
            index += 4;
            return null;
        }

        Double readNumber() {
            int start = index;
            if (peek() == '-' || peek() == '+') {
                index++;
            }
            while (index < text.length()) {
                char c = text.charAt(index);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) {
                throw new SerializationException("Expected a value at position " + start);
            }
            try {
                return Double.valueOf(text.substring(start, index));
            } catch (NumberFormatException ex) {
                throw new SerializationException("Malformed number at position " + start);
            }
        }

        char peek() {
            if (atEnd()) {
                throw new SerializationException("Unexpected end of input");
            }
            return text.charAt(index);
        }

        void expect(char expected) {
            if (atEnd() || text.charAt(index) != expected) {
                throw new SerializationException("Expected '" + expected + "' at position " + index);
            }
            index++;
        }
    }
}

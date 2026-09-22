package Petroleum.Serialization;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Diesel {
    public static final String EXTENSION = ".dsl";
    private static final int BASE64_LINE = 76;
    private static final Set<String> DOCUMENT_KEYS = Set.of("format", "name", "nodes", "connections", "macros");
    private static final Set<String> NODE_KEYS = Set.of("id", "type", "x", "y", "portValues");

    private Diesel() {
    }

    public static boolean isDieselFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    public static String write(PatchCodec codec, PatchDocument document) {
        PatchArchive archive = new PatchArchive();
        Map<String, Object> root = codec.saveRoot(document, archive);
        StringBuilder out = new StringBuilder("// Petroleum Diesel script\n");
        writeDocument(root, out, 0);
        out.append('\n');
        for (Map.Entry<String, float[]> entry : archive.samples().entrySet()) {
            writeBlob("sample", entry.getKey(), SampleCodec.encode(entry.getValue()), out);
        }
        for (Map.Entry<String, byte[]> entry : archive.data().entrySet()) {
            writeBlob("data", entry.getKey(), entry.getValue(), out);
        }
        return out.toString();
    }

    public static PatchDocument read(PatchCodec codec, String text) {
        PatchArchive archive = new PatchArchive();
        Map<String, Object> root = new Reader(text, archive).file();
        root.put("format", PatchCodec.FORMAT);
        root.putIfAbsent("version", PatchCodec.FORMAT_VERSION);
        return codec.loadRoot(root, archive);
    }

    private static void writeDocument(Map<String, Object> document, StringBuilder out, int depth) {
        out.append("patch ");
        String name = (String) document.getOrDefault("name", "");
        if (!name.isEmpty()) {
            Json.writeString(name, out);
            out.append(' ');
        }
        out.append("{\n");
        boolean fields = false;
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if (!DOCUMENT_KEYS.contains(entry.getKey())) {
                indent(out, depth + 1);
                writeAssignment(entry.getKey(), entry.getValue(), out, depth + 1);
                fields = true;
            }
        }

        Map<String, String> ids = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        List<Object> nodes = Json.asArray(document.get("nodes"));
        if (!nodes.isEmpty() && fields) {
            out.append('\n');
        }
        for (Object entry : nodes) {
            Map<String, Object> node = Json.asObject(entry);
            String type = Json.string(node, "type");
            String base = slug(type);
            String id = base + counts.merge(base, 1, Integer::sum);
            ids.put(Json.string(node, "id"), id);
            writeNode(node, type, id, out, depth + 1);
        }

        List<Object> connections = Json.asArray(document.get("connections"));
        if (!connections.isEmpty()) {
            out.append('\n');
        }
        for (Object entry : connections) {
            Map<String, Object> connection = Json.asObject(entry);
            indent(out, depth + 1);
            out.append(ids.get(Json.string(connection, "from"))).append('.');
            writeKey(Json.string(connection, "fromPort"), out);
            out.append(" -> ").append(ids.get(Json.string(connection, "to"))).append('.');
            writeKey(Json.string(connection, "toPort"), out);
            out.append(";\n");
        }

        List<Object> macros = Json.asArray(document.get("macros"));
        if (!macros.isEmpty()) {
            out.append('\n');
        }
        for (Object entry : macros) {
            Map<String, Object> macro = Json.asObject(entry);
            indent(out, depth + 1);
            out.append("macro ");
            Json.writeString(Json.string(macro, "name"), out);
            out.append(" -> ").append(ids.get(Json.string(macro, "target"))).append('(');
            Json.writeNumber((Number) macro.get("min"), out);
            out.append(", ");
            Json.writeNumber((Number) macro.get("max"), out);
            out.append(");\n");
        }
        indent(out, depth);
        out.append('}');
    }

    private static void writeNode(Map<String, Object> node, String type, String id, StringBuilder out, int depth) {
        indent(out, depth);
        writeKey(type, out);
        out.append(' ').append(id).append(" {\n");
        indent(out, depth + 1);
        out.append("x = ");
        Json.writeNumber(Json.numberOrDefault(node, "x", 0), out);
        out.append("; y = ");
        Json.writeNumber(Json.numberOrDefault(node, "y", 0), out);
        out.append(";\n");
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if (!NODE_KEYS.contains(entry.getKey())) {
                indent(out, depth + 1);
                writeAssignment(entry.getKey(), entry.getValue(), out, depth + 1);
            }
        }
        Object portValues = node.get("portValues");
        if (portValues != null) {
            for (Map.Entry<String, Object> entry : Json.asObject(portValues).entrySet()) {
                indent(out, depth + 1);
                out.append('.');
                writeKey(entry.getKey(), out);
                out.append(" = ");
                Json.writeNumber((Number) entry.getValue(), out);
                out.append(";\n");
            }
        }
        indent(out, depth);
        out.append("}\n");
    }

    private static void writeAssignment(String key, Object value, StringBuilder out, int depth) {
        writeKey(key, out);
        out.append(" = ");
        writeValue(value, out, depth);
        out.append(";\n");
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder out, int depth) {
        if (value instanceof Map<?, ?> map && map.containsKey("nodes")) {
            writeDocument((Map<String, Object>) map, out, depth);
        } else if (value instanceof Map<?, ?> map) {
            boolean flat = isFlat(map.values());
            out.append(map.isEmpty() ? "{" : flat ? "{ " : "{\n");
            int remaining = map.size();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!flat) {
                    indent(out, depth + 1);
                }
                writeKey(String.valueOf(entry.getKey()), out);
                out.append(" = ");
                writeValue(entry.getValue(), out, depth + 1);
                if (--remaining > 0) {
                    out.append(',');
                }
                out.append(flat ? " " : "\n");
            }
            if (!flat) {
                indent(out, depth);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            boolean flat = isFlat(list);
            out.append(list.isEmpty() || flat ? "[" : "[\n");
            for (int i = 0; i < list.size(); i++) {
                if (!flat) {
                    indent(out, depth + 1);
                }
                writeValue(list.get(i), out, depth + 1);
                if (i < list.size() - 1) {
                    out.append(flat ? ", " : ",");
                }
                if (!flat) {
                    out.append('\n');
                }
            }
            if (!flat) {
                indent(out, depth);
            }
            out.append(']');
        } else if (value instanceof String s) {
            Json.writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b.booleanValue());
        } else if (value instanceof Number n) {
            Json.writeNumber(n, out);
        } else {
            throw new SerializationException("Cannot write " + value + " as Diesel");
        }
    }

    private static boolean isFlat(Iterable<?> values) {
        for (Object value : values) {
            if (value instanceof Map || value instanceof List) {
                return false;
            }
        }
        return true;
    }

    private static void writeKey(String key, StringBuilder out) {
        if (isIdentifier(key)) {
            out.append(key);
        } else {
            Json.writeString(key, out);
        }
    }

    private static void writeBlob(String kind, String id, byte[] bytes, StringBuilder out) {
        String encoded = Base64.getEncoder().encodeToString(bytes);
        out.append('\n').append(kind).append(' ').append(id).append(" = b64\"");
        for (int i = 0; i < encoded.length(); i += BASE64_LINE) {
            out.append("\n    ").append(encoded, i, Math.min(encoded.length(), i + BASE64_LINE));
        }
        out.append("\n\";\n");
    }

    private static String slug(String type) {
        String slug = type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() || Character.isDigit(slug.charAt(0)) ? "n_" + slug : slug;
    }

    private static boolean isIdentifier(String text) {
        if (text.isEmpty() || !(Character.isLetter(text.charAt(0)) || text.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("    ".repeat(depth));
    }

    private enum Kind { IDENT, STRING, NUMBER, BLOB, PUNCT, END }

    private record Token(Kind kind, String text, int line, int column) {
        boolean is(String punct) {
            return (kind == Kind.PUNCT || kind == Kind.IDENT) && text.equals(punct);
        }
    }

    private static final class Reader {
        private final List<Token> tokens = new ArrayList<>();
        private final PatchArchive archive;
        private int position;

        Reader(String text, PatchArchive archive) {
            this.archive = archive;
            new Lexer(text, tokens).run();
        }

        Map<String, Object> file() {
            Map<String, Object> root = null;
            while (peek().kind != Kind.END) {
                Token token = next();
                if (token.is("patch")) {
                    if (root != null) {
                        throw error(token, "A Diesel file holds exactly one top-level patch");
                    }
                    root = document();
                } else if (token.is("sample") || token.is("data")) {
                    String id = expect(Kind.IDENT, "a blob id").text;
                    expectPunct("=");
                    Token blob = expect(Kind.BLOB, "a b64\"...\" literal");
                    byte[] bytes;
                    try {
                        bytes = Base64.getMimeDecoder().decode(blob.text);
                    } catch (IllegalArgumentException ex) {
                        throw error(blob, "Invalid base64: " + ex.getMessage());
                    }
                    if (token.is("sample")) {
                        archive.putSample(id, SampleCodec.decode(bytes));
                    } else {
                        archive.putData(id, bytes);
                    }
                    expectPunct(";");
                } else {
                    throw error(token, "Expected 'patch', 'sample' or 'data', found '" + token.text + "'");
                }
            }
            if (root == null) {
                throw error(peek(), "Missing top-level 'patch { ... }'");
            }
            return root;
        }

        private Map<String, Object> document() {
            Map<String, Object> document = Json.object();
            document.put("name", peek().kind == Kind.STRING ? next().text : "");
            List<Object> nodes = new ArrayList<>();
            List<Object> connections = new ArrayList<>();
            List<Object> macros = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            expectPunct("{");
            while (!peek().is("}")) {
                Token token = peek();
                Token after = peekAt(1);
                if (token.is("macro") && after.kind == Kind.STRING) {
                    next();
                    Map<String, Object> macro = Json.object();
                    macro.put("name", next().text);
                    expectPunct("->");
                    macro.put("target", nodeRef(ids));
                    expectPunct("(");
                    macro.put("min", number());
                    expectPunct(",");
                    macro.put("max", number());
                    expectPunct(")");
                    expectPunct(";");
                    macros.add(macro);
                } else if (token.kind == Kind.STRING || (token.kind == Kind.IDENT && after.kind == Kind.IDENT)) {
                    nodes.add(node(ids));
                } else if (token.kind == Kind.IDENT && after.is("=")) {
                    next();
                    next();
                    document.put(token.text, value());
                    expectPunct(";");
                } else if (token.kind == Kind.IDENT && after.is(".")) {
                    Map<String, Object> connection = Json.object();
                    connection.put("from", nodeRef(ids));
                    expectPunct(".");
                    connection.put("fromPort", key());
                    expectPunct("->");
                    connection.put("to", nodeRef(ids));
                    expectPunct(".");
                    connection.put("toPort", key());
                    expectPunct(";");
                    connections.add(connection);
                } else {
                    throw error(token, "Expected a node declaration, connection, macro or assignment, found '"
                            + token.text + "'");
                }
            }
            next();
            document.put("nodes", nodes);
            document.put("connections", connections);
            document.put("macros", macros);
            return document;
        }

        private Map<String, Object> node(Set<String> ids) {
            String type = next().text;
            Token idToken = expect(Kind.IDENT, "a node name");
            if (!ids.add(idToken.text)) {
                throw error(idToken, "Node '" + idToken.text + "' is already declared in this patch");
            }
            Map<String, Object> node = Json.object();
            node.put("id", idToken.text);
            node.put("type", type);
            if (peek().is(";")) {
                next();
                return node;
            }
            if (!peek().is("{")) {
                throw error(peek(), "Expected ';' or '{' after node '" + idToken.text + "', found '"
                        + peek().text + "'");
            }
            next();
            Map<String, Object> portValues = Json.object();
            while (!peek().is("}")) {
                if (peek().is(".")) {
                    next();
                    String port = key();
                    expectPunct("=");
                    portValues.put(port, number());
                } else {
                    Token keyToken = peek();
                    String key = key();
                    if (key.equals("id") || key.equals("type") || key.equals("portValues")) {
                        throw error(keyToken, "'" + key + "' cannot be assigned inside a node body");
                    }
                    expectPunct("=");
                    node.put(key, value());
                }
                expectPunct(";");
            }
            next();
            if (peek().is(";")) {
                next();
            }
            if (!portValues.isEmpty()) {
                node.put("portValues", portValues);
            }
            return node;
        }

        private String nodeRef(Set<String> ids) {
            Token token = expect(Kind.IDENT, "a node name");
            if (!ids.contains(token.text)) {
                throw error(token, "Unknown node '" + token.text + "' (declare a node before referring to it)");
            }
            return token.text;
        }

        private Object value() {
            Token token = next();
            switch (token.kind) {
                case NUMBER:
                    return parseNumber(token);
                case STRING:
                    return token.text;
                case IDENT:
                    if (token.text.equals("patch")) {
                        return document();
                    }
                    if (token.text.equals("true") || token.text.equals("false")) {
                        return Boolean.parseBoolean(token.text);
                    }
                    return token.text;
                default:
                    break;
            }
            if (token.is("{")) {
                Map<String, Object> object = Json.object();
                while (!peek().is("}")) {
                    String key = key();
                    expectPunct("=");
                    object.put(key, value());
                    if (!peek().is("}")) {
                        expectPunct(",");
                    }
                }
                next();
                return object;
            }
            if (token.is("[")) {
                List<Object> list = new ArrayList<>();
                while (!peek().is("]")) {
                    list.add(value());
                    if (!peek().is("]")) {
                        expectPunct(",");
                    }
                }
                next();
                return list;
            }
            throw error(token, "Expected a value, found '" + token.text + "'");
        }

        private Number number() {
            return parseNumber(expect(Kind.NUMBER, "a number"));
        }

        private Number parseNumber(Token token) {
            try {
                if (token.text.matches("-?\\d+")) {
                    return Long.parseLong(token.text);
                }
                return Double.parseDouble(token.text);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(token.text);
            }
        }

        private String key() {
            Token token = next();
            if (token.kind != Kind.IDENT && token.kind != Kind.STRING) {
                throw error(token, "Expected a name, found '" + token.text + "'");
            }
            return token.text;
        }

        private Token expect(Kind kind, String what) {
            Token token = next();
            if (token.kind != kind) {
                throw error(token, "Expected " + what + ", found '" + token.text + "'");
            }
            return token;
        }

        private void expectPunct(String punct) {
            Token token = next();
            if (token.kind != Kind.PUNCT || !token.text.equals(punct)) {
                throw error(token, "Expected '" + punct + "', found '" + token.text + "'");
            }
        }

        private Token peek() {
            return peekAt(0);
        }

        private Token peekAt(int offset) {
            return tokens.get(Math.min(position + offset, tokens.size() - 1));
        }

        private Token next() {
            Token token = peek();
            if (token.kind != Kind.END) {
                position++;
            }
            return token;
        }
    }

    private static final class Lexer {
        private final String text;
        private final List<Token> tokens;
        private int index;
        private int line = 1;
        private int lineStart;

        Lexer(String text, List<Token> tokens) {
            this.text = text;
            this.tokens = tokens;
        }

        void run() {
            while (true) {
                skipBlank();
                int start = index;
                int column = index - lineStart + 1;
                int startLine = line;
                if (index >= text.length()) {
                    tokens.add(new Token(Kind.END, "end of file", line, column));
                    return;
                }
                char c = text.charAt(index);
                if (c == 'b' && text.startsWith("b64\"", index)) {
                    index += 3;
                    tokens.add(new Token(Kind.BLOB, string(startLine, column), startLine, column));
                } else if (Character.isLetter(c) || c == '_') {
                    while (index < text.length()
                            && (Character.isLetterOrDigit(text.charAt(index)) || text.charAt(index) == '_')) {
                        index++;
                    }
                    tokens.add(new Token(Kind.IDENT, text.substring(start, index), line, column));
                } else if (c == '"') {
                    tokens.add(new Token(Kind.STRING, string(startLine, column), startLine, column));
                } else if (Character.isDigit(c) || (c == '-' && startsNumber(index + 1))
                        || (c == '.' && index + 1 < text.length() && Character.isDigit(text.charAt(index + 1)))) {
                    index++;
                    while (index < text.length() && isNumberChar(text.charAt(index), text.charAt(index - 1))) {
                        index++;
                    }
                    tokens.add(new Token(Kind.NUMBER, text.substring(start, index), line, column));
                } else if (text.startsWith("->", index)) {
                    index += 2;
                    tokens.add(new Token(Kind.PUNCT, "->", line, column));
                } else if ("{}()[];,=.".indexOf(c) >= 0) {
                    index++;
                    tokens.add(new Token(Kind.PUNCT, String.valueOf(c), line, column));
                } else {
                    throw new SerializationException(
                            "Diesel line " + line + ":" + column + ": Unexpected character '" + c + "'");
                }
            }
        }

        private boolean startsNumber(int at) {
            return at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.');
        }

        private static boolean isNumberChar(char c, char previous) {
            return Character.isDigit(c) || c == '.' || c == 'e' || c == 'E'
                    || ((c == '-' || c == '+') && (previous == 'e' || previous == 'E'));
        }

        private void skipBlank() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == '\n') {
                    newline();
                } else if (Character.isWhitespace(c)) {
                    index++;
                } else if (text.startsWith("//", index)) {
                    while (index < text.length() && text.charAt(index) != '\n') {
                        index++;
                    }
                } else if (text.startsWith("/*", index)) {
                    int end = text.indexOf("*/", index + 2);
                    if (end < 0) {
                        throw new SerializationException("Diesel line " + line + ": Unterminated /* comment");
                    }
                    while (index < end + 2) {
                        if (text.charAt(index) == '\n') {
                            newline();
                        } else {
                            index++;
                        }
                    }
                } else {
                    return;
                }
            }
        }

        private void newline() {
            index++;
            line++;
            lineStart = index;
        }

        private String string(int startLine, int column) {
            StringBuilder out = new StringBuilder();
            index++;
            while (true) {
                if (index >= text.length()) {
                    throw new SerializationException(
                            "Diesel line " + startLine + ":" + column + ": Unterminated string");
                }
                char c = text.charAt(index);
                if (c == '"') {
                    index++;
                    return out.toString();
                }
                if (c == '\n') {
                    out.append(c);
                    newline();
                    continue;
                }
                index++;
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (index >= text.length()) {
                    continue;
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw new SerializationException("Diesel line " + line + ": Bad \\u escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> out.append(escape);
                }
            }
        }
    }

    private static SerializationException error(Token token, String message) {
        return new SerializationException("Diesel line " + token.line + ":" + token.column + ": " + message);
    }
}

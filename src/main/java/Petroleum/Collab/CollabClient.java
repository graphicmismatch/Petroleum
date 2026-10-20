package Petroleum.Collab;

import Petroleum.Serialization.Json;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class CollabClient implements AutoCloseable {
    public static final int DEFAULT_PORT = 7777;
    static final int MAX_LINE = 32 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    public interface Handler {
        void op(String key, String value);

        void peers(List<String> names);

        void closed(String reason);
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final ConcurrentLinkedQueue<Map<String, Object>> inbox = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> pending = new HashMap<>();
    private final Map<String, String> initialState = new LinkedHashMap<>();
    private final String clientId;
    private final String room;
    private volatile String closedReason;

    private CollabClient(String host, int port, Map<String, Object> hello) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
        try {
            write(hello);
            out.flush();
            Map<String, Object> welcome = readMessage();
            if (welcome == null || !"welcome".equals(welcome.get("t"))) {
                throw new IOException(welcome == null ? "server closed the connection"
                        : Json.stringOrDefault(welcome, "message", "unexpected reply"));
            }
            clientId = Json.string(welcome, "client");
            room = Json.string(welcome, "room");
            Map<String, Object> entry;
            while ((entry = readMessage()) != null && "state".equals(entry.get("t"))) {
                initialState.put(Json.string(entry, "k"), (String) entry.get("v"));
            }
            if (entry == null || !"ready".equals(entry.get("t"))) {
                throw new IOException("incomplete session state");
            }
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
        Thread reader = new Thread(this::readLoop, "collab-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public static CollabClient create(String host, int port, String name) throws IOException {
        Map<String, Object> hello = Json.object();
        hello.put("t", "create");
        hello.put("name", name);
        return new CollabClient(host, port, hello);
    }

    public static CollabClient join(String host, int port, String room, String name) throws IOException {
        Map<String, Object> hello = Json.object();
        hello.put("t", "join");
        hello.put("room", room);
        hello.put("name", name);
        return new CollabClient(host, port, hello);
    }

    public String clientId() {
        return clientId;
    }

    public String room() {
        return room;
    }

    public Map<String, String> initialState() {
        return initialState;
    }

    public boolean isClosed() {
        return closedReason != null;
    }

    public static boolean fits(String key, String value) {
        return key.length() + (value == null ? 0 : value.length()) * 2L + 64 < MAX_LINE;
    }

    public void send(String key, String value) {
        if (isClosed() || !fits(key, value)) {
            return;
        }
        Map<String, Object> op = Json.object();
        op.put("t", "op");
        op.put("k", key);
        op.put("v", value);
        try {
            write(op);
            pending.merge(key, 1, Integer::sum);
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    public void poll(Handler handler) {
        Map<String, Object> msg;
        while ((msg = inbox.poll()) != null) {
            switch (String.valueOf(msg.get("t"))) {
                case "op" -> {
                    String key = Json.string(msg, "k");
                    if (clientId.equals(msg.get("from"))) {
                        pending.computeIfPresent(key, (k, n) -> n > 1 ? n - 1 : null);
                    } else if (!pending.containsKey(key)) {
                        handler.op(key, (String) msg.get("v"));
                    }
                }
                case "peers" -> {
                    List<String> names = new ArrayList<>();
                    for (Object name : Json.asArray(msg.get("names"))) {
                        names.add(String.valueOf(name));
                    }
                    handler.peers(names);
                }
                case "closed" -> handler.closed(Json.stringOrDefault(msg, "message", "disconnected"));
                default -> { }
            }
        }
    }

    @Override
    public void close() {
        fail("left the session");
    }

    private void readLoop() {
        try {
            Map<String, Object> msg;
            while ((msg = readMessage()) != null) {
                if ("error".equals(msg.get("t"))) {
                    fail(Json.stringOrDefault(msg, "message", "server error"));
                    return;
                }
                inbox.add(msg);
            }
            fail("server closed the connection");
        } catch (IOException | RuntimeException e) {
            fail(e.getMessage());
        }
    }

    private void fail(String reason) {
        synchronized (this) {
            if (closedReason != null) {
                return;
            }
            closedReason = reason == null ? "disconnected" : reason;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        Map<String, Object> msg = Json.object();
        msg.put("t", "closed");
        msg.put("message", closedReason);
        inbox.add(msg);
    }

    private void write(Map<String, Object> msg) throws IOException {
        out.write(Json.write(msg).replaceAll("\n *", "").getBytes(StandardCharsets.UTF_8));
        out.write('\n');
    }

    private Map<String, Object> readMessage() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != '\n') {
            if (b < 0) {
                return null;
            }
            if (line.size() >= MAX_LINE) {
                throw new IOException("message too long");
            }
            line.write(b);
        }
        return Json.asObject(Json.parse(line.toString(StandardCharsets.UTF_8)));
    }
}

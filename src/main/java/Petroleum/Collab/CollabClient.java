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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

public final class CollabClient implements AutoCloseable {
    public static final int DEFAULT_PORT = 7777;
    static final int MAX_LINE = 32 * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    public record Endpoint(String host, int port, boolean tls) {
        public static Endpoint parse(String server) {
            String text = server.trim();
            boolean tls = false;
            if (text.regionMatches(true, 0, "tls://", 0, 6)) {
                tls = true;
                text = text.substring(6);
            } else if (text.regionMatches(true, 0, "tcp://", 0, 6)) {
                text = text.substring(6);
            }
            String host = text;
            int port = DEFAULT_PORT;
            int colon = text.lastIndexOf(':');
            if (text.startsWith("[")) {
                int close = text.indexOf(']');
                if (close < 0) {
                    throw new IllegalArgumentException("unclosed '[' in " + server);
                }
                host = text.substring(1, close);
                colon = text.indexOf(':', close);
            } else if (colon >= 0) {
                host = text.substring(0, colon);
            }
            if (colon >= 0) {
                try {
                    port = Integer.parseInt(text.substring(colon + 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("bad port in " + server);
                }
            }
            if (host.isEmpty() || port < 1 || port > 65535) {
                throw new IllegalArgumentException("bad server address " + server);
            }
            return new Endpoint(host, port, tls);
        }

        public String key() {
            return (tls ? "tls://" : "tcp://") + host + ":" + port;
        }
    }

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

    private CollabClient(Endpoint endpoint, String pinned, Map<String, Object> hello) throws IOException {
        socket = connect(endpoint, pinned);
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

    public static CollabClient create(Endpoint endpoint, String pinned, String name) throws IOException {
        Map<String, Object> hello = Json.object();
        hello.put("t", "create");
        hello.put("name", name);
        return new CollabClient(endpoint, pinned, hello);
    }

    public static CollabClient join(Endpoint endpoint, String pinned, String room, String name) throws IOException {
        Map<String, Object> hello = Json.object();
        hello.put("t", "join");
        hello.put("room", room);
        hello.put("name", name);
        return new CollabClient(endpoint, pinned, hello);
    }

    private static Socket connect(Endpoint endpoint, String pinned) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), CONNECT_TIMEOUT_MS);
        plain.setTcpNoDelay(true);
        if (!endpoint.tls()) {
            return plain;
        }
        PinningTrustManager trust;
        SSLSocket tls;
        try {
            trust = PinningTrustManager.create(pinned);
            tls = (SSLSocket) trust.context().getSocketFactory()
                    .createSocket(plain, endpoint.host(), endpoint.port(), true);
        } catch (GeneralSecurityException e) {
            plain.close();
            throw new IOException("TLS is unavailable: " + e.getMessage(), e);
        }
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        tls.setSSLParameters(parameters);
        try {
            plain.setSoTimeout(CONNECT_TIMEOUT_MS);
            tls.startHandshake();
            plain.setSoTimeout(0);
        } catch (SSLException e) {
            tls.close();
            if (trust.rejectedFingerprint != null) {
                throw new UntrustedServerException(trust.rejectedFingerprint, pinned != null, e);
            }
            throw e;
        }
        return tls;
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

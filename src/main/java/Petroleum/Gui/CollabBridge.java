package Petroleum.Gui;

import Petroleum.Collab.CollabClient;
import Petroleum.Graph.Node;
import Petroleum.Graph.Stateful;
import Petroleum.Patch.Connection;
import Petroleum.Serialization.Diesel;
import Petroleum.Serialization.Json;
import Petroleum.Serialization.Macro;
import Petroleum.Serialization.PatchDocument;
import Petroleum.Sources.ConstantNode;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class CollabBridge implements CollabClient.Handler {
    private static final String NODE = "node/";
    private static final String POS = "pos/";
    private static final String KNOBS = "knobs/";
    private static final String WIRE = "wire/";
    private static final String MACRO = "macro/";
    private static final int BODY_EVERY = 5;

    private final GraphEditor editor;
    private CollabClient client;
    private final BooleanSupplier paused;
    private final BooleanSupplier bodiesLocked;
    private final Consumer<String> status;
    private final Consumer<Boolean> onClosed;
    private final Runnable onChange;
    private final Map<Node, String> ids = new IdentityHashMap<>();
    private final Map<String, Node> byId = new HashMap<>();
    private final Map<String, String> baseline = new LinkedHashMap<>();
    private final Map<String, String> deferred = new LinkedHashMap<>();
    private final Timeline timeline;
    private int counter;
    private int tick;

    CollabBridge(GraphEditor editor, CollabClient client, BooleanSupplier paused, BooleanSupplier bodiesLocked,
                 Consumer<String> status, Consumer<Boolean> onClosed, Runnable onChange) {
        this.editor = editor;
        this.client = client;
        this.paused = paused;
        this.bodiesLocked = bodiesLocked;
        this.status = status;
        this.onClosed = onClosed;
        this.onChange = onChange;
        this.timeline = new Timeline(new KeyFrame(Duration.millis(100), e -> tick()));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    void start(boolean adoptRemote) {
        if (adoptRemote) {
            editor.loadDocument(buildDocument(client.initialState()));
            baseline.putAll(client.initialState());
        }
        timeline.play();
        refreshStatus();
    }

    void stop() {
        timeline.stop();
        client.close();
    }

    void resume(CollabClient next) {
        CollabClient old = client;
        Set<String> unacked = old.pendingKeys();
        boolean backup = CollabClient.HOST.equals(old.role()) && next.seq() < old.seq();
        client = next;
        deferred.clear();
        Map<String, String> server = next.initialState();
        Map<String, String> local = flatten(editor.toDocument(), true);
        Map<String, String> merged = new LinkedHashMap<>(server);
        if (canEdit()) {
            Set<String> keys = new LinkedHashSet<>(local.keySet());
            keys.addAll(baseline.keySet());
            keys.addAll(server.keySet());
            for (String key : keys) {
                String mine = local.get(key);
                boolean changed = backup ? !Objects.equals(mine, server.get(key))
                        : unacked.contains(key) || !Objects.equals(mine, baseline.get(key));
                if (changed && mine == null) {
                    merged.remove(key);
                } else if (changed) {
                    merged.put(key, mine);
                }
            }
        }
        if (!merged.equals(local)) {
            ids.clear();
            byId.clear();
            editor.loadDocument(buildDocument(merged));
        }
        baseline.clear();
        baseline.putAll(server);
        timeline.play();
        refreshStatus();
    }

    CollabClient client() {
        return client;
    }

    String room() {
        return client.room();
    }

    private boolean canEdit() {
        return !CollabClient.VIEWER.equals(client.role());
    }

    private void tick() {
        if (paused.getAsBoolean()) {
            return;
        }
        if (!client.isClosed() && canEdit()) {
            push();
        } else if (!client.isClosed() && !bodiesLocked.getAsBoolean()) {
            revert();
        }
        client.poll(this);
        if (client.isClosed()) {
            return;
        }
        if (!bodiesLocked.getAsBoolean() && !deferred.isEmpty()) {
            Map<String, String> flush = new LinkedHashMap<>(deferred);
            deferred.clear();
            flush.forEach(this::op);
        }
    }

    private void push() {
        Map<String, String> current = flatten(editor.toDocument(), tick++ % BODY_EVERY == 0);
        for (Map.Entry<String, String> entry : current.entrySet()) {
            if (!entry.getValue().equals(baseline.get(entry.getKey()))) {
                if (!CollabClient.fits(entry.getKey(), entry.getValue())) {
                    status.accept("Too large to share: " + entry.getKey());
                }
                client.send(entry.getKey(), entry.getValue());
                deferred.remove(entry.getKey());
            }
        }
        for (String key : baseline.keySet()) {
            if (!current.containsKey(key)) {
                client.send(key, null);
            }
        }
        client.flush();
        baseline.clear();
        baseline.putAll(current);
    }

    private void revert() {
        Map<String, String> current = flatten(editor.toDocument(), tick++ % BODY_EVERY == 0);
        if (current.equals(baseline)) {
            return;
        }
        Set<String> all = new LinkedHashSet<>(current.keySet());
        all.addAll(baseline.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : all) {
            if (!Objects.equals(current.get(key), baseline.get(key))) {
                changed.add(key.startsWith(NODE) ? 0 : changed.size(), key);
            }
        }
        for (String key : changed) {
            try {
                apply(key, baseline.get(key));
            } catch (RuntimeException e) {
                status.accept("Could not undo a local change (" + key + "): " + e.getMessage());
            }
        }
        editor.clearHistory();
    }

    private Map<String, String> flatten(PatchDocument document, boolean encodeBodies) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<Node, String> live = new IdentityHashMap<>();
        Map<Node, List<String>> driven = new IdentityHashMap<>();
        for (Connection c : document.getConnections()) {
            driven.computeIfAbsent(c.destination(), n -> new ArrayList<>()).add(c.destinationInput());
        }
        for (Node node : document.getNodes()) {
            String id = ids.get(node);
            if (id == null) {
                id = client.clientId() + ":" + counter++;
                ids.put(node, id);
                byId.put(id, node);
            }
            live.put(node, id);
            String body = encodeBodies ? null : baseline.get(NODE + id);
            out.put(NODE + id, body != null ? body : encode(node));
            out.put(POS + id, document.getX(node) + "," + document.getY(node));
            Map<String, Float> knobs = new TreeMap<>(document.getPortValues(node));
            knobs.keySet().removeAll(driven.getOrDefault(node, List.of()));
            out.put(KNOBS + id, oneLine(knobs));
        }
        ids.keySet().retainAll(live.keySet());
        byId.values().retainAll(live.keySet());
        for (Connection c : document.getConnections()) {
            out.put(WIRE + live.get(c.destination()) + "/" + c.destinationInput(),
                    live.get(c.source()) + "/" + c.sourceOutput());
        }
        for (Macro macro : document.getMacros()) {
            String target = live.get(macro.target());
            if (target != null) {
                Map<String, Object> json = Json.object();
                json.put("target", target);
                json.put("min", macro.min());
                json.put("max", macro.max());
                out.put(MACRO + macro.name(), oneLine(json));
            }
        }
        return out;
    }

    private String encode(Node node) {
        PatchDocument single = new PatchDocument();
        single.addNode(node, 0, 0);
        return Diesel.write(editor.getPatchCodec(), single);
    }

    private Node decode(String body) {
        List<Node> nodes = Diesel.read(editor.getPatchCodec(), body).getNodes();
        if (nodes.size() != 1) {
            throw new IllegalArgumentException("expected one node");
        }
        return nodes.get(0);
    }

    @Override
    public void op(String key, String value) {
        try {
            if (key.startsWith(NODE) && bodiesLocked.getAsBoolean() && byId.containsKey(key.substring(NODE.length()))) {
                deferred.put(key, value);
                return;
            }
            apply(key, value);
            if (value == null) {
                baseline.remove(key);
            } else {
                baseline.put(key, value);
            }
        } catch (RuntimeException e) {
            status.accept("Dropped a bad update (" + key + "): " + e.getMessage());
        }
    }

    private void apply(String key, String value) {
        if (key.startsWith(NODE)) {
            applyNode(key.substring(NODE.length()), value);
        } else if (key.startsWith(POS)) {
            Node node = byId.get(key.substring(POS.length()));
            if (node != null && value != null) {
                double[] xy = parsePos(value);
                editor.placeNode(node, xy[0], xy[1]);
            }
        } else if (key.startsWith(KNOBS)) {
            NodeView view = viewOf(key.substring(KNOBS.length()));
            if (view != null && value != null) {
                view.applyShadowValues(parseKnobs(value));
            }
        } else if (key.startsWith(WIRE)) {
            applyWire(key.substring(WIRE.length()), value);
        } else if (key.startsWith(MACRO)) {
            Map<String, String> next = new LinkedHashMap<>(baseline);
            if (value == null) {
                next.remove(key);
            } else {
                next.put(key, value);
            }
            editor.replaceMacros(macrosFrom(next));
        }
    }

    private void applyNode(String id, String body) {
        Node old = byId.get(id);
        if (old != null && editor.viewOf(old) == null) {
            forget(old);
            old = null;
        }
        if (body == null) {
            if (old != null) {
                forget(old);
                editor.removeNodeByReference(old);
                editor.clearHistory();
            }
            return;
        }
        Node node = decode(body);
        if (old == null) {
            double[] xy = parsePos(baseline.getOrDefault(POS + id, "0,0"));
            NodeView view = editor.addNodeSilently(node, xy[0], xy[1]);
            view.applyShadowValues(parseKnobs(baseline.getOrDefault(KNOBS + id, "{}")));
            remember(id, node);
            reconnect(id);
            editor.replaceMacros(macrosFrom(baseline));
            return;
        }
        NodeView oldView = editor.viewOf(old);
        if (old.getClass() == node.getClass() && old instanceof Stateful target && node instanceof Stateful source) {
            Map<String, String> state = new HashMap<>();
            source.saveState(state);
            target.loadState(state);
            if (encode(old).equals(body)) {
                oldView.syncControlsFromNodes();
                return;
            }
        }
        double x = oldView.getLayoutX();
        double y = oldView.getLayoutY();
        Map<String, Float> knobs = oldView.getShadowValues();
        forget(old);
        editor.removeNodeByReference(old);
        NodeView view = editor.addNodeSilently(node, x, y);
        view.applyShadowValues(knobs);
        remember(id, node);
        reconnect(id);
        editor.replaceMacros(macrosFrom(baseline));
        editor.clearHistory();
    }

    private void applyWire(String target, String value) {
        int slash = target.indexOf('/');
        Node destination = byId.get(target.substring(0, slash));
        String input = target.substring(slash + 1);
        if (destination == null) {
            return;
        }
        if (value == null) {
            for (Connection c : editor.toDocument().getConnections()) {
                if (c.destination() == destination && c.destinationInput().equals(input)) {
                    editor.disconnectSilently(c);
                }
            }
            return;
        }
        int split = value.indexOf('/');
        Node source = byId.get(value.substring(0, split));
        if (source != null) {
            editor.connectSilently(new Connection(source, value.substring(split + 1), destination, input));
        }
    }

    private void reconnect(String id) {
        for (Map.Entry<String, String> entry : baseline.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(WIRE) && (key.startsWith(WIRE + id + "/") || entry.getValue().startsWith(id + "/"))) {
                applyWire(key.substring(WIRE.length()), entry.getValue());
            }
        }
    }

    private List<Macro> macrosFrom(Map<String, String> state) {
        List<Macro> macros = new ArrayList<>();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            if (!entry.getKey().startsWith(MACRO)) {
                continue;
            }
            Map<String, Object> json = Json.asObject(Json.parse(entry.getValue()));
            if (byId.get(Json.string(json, "target")) instanceof ConstantNode constant) {
                macros.add(new Macro(entry.getKey().substring(MACRO.length()), constant,
                        (float) Json.number(json, "min"), (float) Json.number(json, "max")));
            }
        }
        return macros;
    }

    private PatchDocument buildDocument(Map<String, String> state) {
        PatchDocument document = new PatchDocument();
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(NODE)) {
                continue;
            }
            String id = key.substring(NODE.length());
            try {
                Node node = decode(entry.getValue());
                double[] xy = parsePos(state.getOrDefault(POS + id, "0,0"));
                document.addNode(node, xy[0], xy[1]);
                document.setPortValues(node, parseKnobs(state.getOrDefault(KNOBS + id, "{}")));
                remember(id, node);
            } catch (RuntimeException e) {
                status.accept("Dropped a bad node (" + id + "): " + e.getMessage());
            }
        }
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(WIRE)) {
                continue;
            }
            String target = key.substring(WIRE.length());
            int slash = target.indexOf('/');
            int split = entry.getValue().indexOf('/');
            Node destination = byId.get(target.substring(0, slash));
            Node source = split < 0 ? null : byId.get(entry.getValue().substring(0, split));
            if (destination != null && source != null) {
                document.addConnection(new Connection(source, entry.getValue().substring(split + 1),
                        destination, target.substring(slash + 1)));
            }
        }
        for (Macro macro : macrosFrom(state)) {
            document.addMacro(macro);
        }
        return document;
    }

    private void remember(String id, Node node) {
        ids.put(node, id);
        byId.put(id, node);
    }

    private void forget(Node node) {
        byId.remove(ids.remove(node));
    }

    private NodeView viewOf(String id) {
        Node node = byId.get(id);
        return node == null ? null : editor.viewOf(node);
    }

    private static double[] parsePos(String value) {
        int comma = value.indexOf(',');
        return new double[] {Double.parseDouble(value.substring(0, comma)), Double.parseDouble(value.substring(comma + 1))};
    }

    private static Map<String, Float> parseKnobs(String value) {
        Map<String, Float> knobs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : Json.asObject(Json.parse(value)).entrySet()) {
            knobs.put(entry.getKey(), (float) Json.asDouble(entry.getValue()));
        }
        return knobs;
    }

    private static String oneLine(Object json) {
        return Json.write(json).replaceAll("\n *", "");
    }

    @Override
    public void members(List<CollabClient.Member> members) {
        refreshStatus();
        onChange.run();
    }

    @Override
    public void room(String code) {
        refreshStatus();
        onChange.run();
    }

    @Override
    public void closed(String reason, boolean resumable) {
        timeline.stop();
        status.accept(resumable ? "Disconnected: " + reason + " - Session > Reconnect to continue"
                : "Session ended: " + reason);
        onClosed.accept(resumable);
    }

    private void refreshStatus() {
        List<String> names = new ArrayList<>();
        for (CollabClient.Member member : client.members()) {
            if (member.online()) {
                names.add(CollabClient.EDITOR.equals(member.role()) ? member.name()
                        : member.name() + " (" + member.role() + ")");
            }
        }
        status.accept("Session " + client.room() + (canEdit() ? "" : " (view only)") + " - "
                + String.join(", ", names));
    }
}

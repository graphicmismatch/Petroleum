package Petroleum.Serialization;

import Petroleum.Conditionals.MultiplexerNode;
import Petroleum.Conditionals.SequencerNode;
import Petroleum.Effects.ParametricEqNode;
import Petroleum.Effects.TranceGateNode;
import Petroleum.Effects.WaveshaperNode;
import Petroleum.Graph.Node;
import Petroleum.Graph.NodeFactory;
import Petroleum.Graph.Port;
import Petroleum.Graph.Stateful;
import Petroleum.Granular.GranularPlayerNode;
import Petroleum.Instruments.Instrument;
import Petroleum.Instruments.InstrumentNode;
import Petroleum.Mixers.MixNode;
import Petroleum.Modules.PatchNode;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Oscillators.OscillatorNode;
import Petroleum.Patch.Connection;
import Petroleum.Sources.ConstantNode;
import Petroleum.Spectral.AdditiveResynthNode;
import Petroleum.Tracker.MidiPlayerNode;
import Petroleum.Tracker.TrackerNode;
import Petroleum.Utility.SplitterNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class PatchCodec {
    public static final int FORMAT_VERSION = 6;
    private static final String FORMAT = "petroleum-patch";
    private static final String LEGACY_FORMAT = "jsynth-patch";

    private static final Map<Integer, BiConsumer<Map<String, Object>, PatchArchive>> MIGRATIONS =
            Map.of(5, FormatV5Migration::apply);

    private static final String TYPE_OSCILLATOR = "Oscillator";
    private static final String TYPE_SPLITTER = "Splitter";
    private static final String TYPE_MULTIPLEXER = "Multiplexer";
    private static final String TYPE_MIX = "Mix";
    private static final String TYPE_SEQUENCER = "Sequencer";
    private static final String TYPE_TRANCE_GATE = "Trance Gate";
    private static final String TYPE_PARAMETRIC_EQ = "Parametric EQ";
    private static final String TYPE_PATCH = "Patch";
    private static final String TYPE_GRANULAR_PLAYER = "Granular Player";
    private static final String TYPE_WAVESHAPER = "Waveshaper";
    private static final String TYPE_TRACKER = "Tracker";
    private static final String TYPE_MIDI_PLAYER = "MIDI Player";
    private static final String TYPE_ADDITIVE_RESYNTH = "Additive Resynth";
    private static final String TYPE_INSTRUMENT = "Instrument";

    private final NodeFactory nodeFactory;
    private final OscillatorCodec oscillatorCodec;
    private final InstrumentCodec instrumentCodec = new InstrumentCodec();

    public PatchCodec(NodeFactory nodeFactory, OscillatorFactory oscillatorFactory) {
        this.nodeFactory = nodeFactory;
        this.oscillatorCodec = new OscillatorCodec(oscillatorFactory);
    }

    public void save(PatchDocument document, OutputStream out) throws IOException {
        Map<String, Object> root = Json.object();
        root.put("format", FORMAT);
        root.put("version", FORMAT_VERSION);
        PatchArchive archive = new PatchArchive();
        root.putAll(saveDocument(document, archive));
        archive.write(out, Json.write(root));
    }

    private Map<String, Object> saveDocument(PatchDocument document, PatchArchive archive) {
        Map<Node, String> ids = new IdentityHashMap<>();
        List<Object> nodes = new ArrayList<>();
        int next = 0;
        for (Node node : document.getNodes()) {
            String id = "n" + next++;
            ids.put(node, id);
            nodes.add(saveNode(node, id, document, archive));
        }

        List<Object> connections = new ArrayList<>();
        for (Connection connection : document.getConnections()) {
            String from = ids.get(connection.source());
            String to = ids.get(connection.destination());
            if (from == null || to == null) {
                continue;
            }
            Map<String, Object> json = Json.object();
            json.put("from", from);
            json.put("fromPort", connection.sourceOutput());
            json.put("to", to);
            json.put("toPort", connection.destinationInput());
            connections.add(json);
        }

        List<Object> macros = new ArrayList<>();
        for (Macro macro : document.getMacros()) {
            String target = ids.get(macro.target());
            if (target == null) {
                throw new SerializationException("Macro '" + macro.name()
                        + "' points at a node that is not part of this patch, so it cannot be saved");
            }
            Map<String, Object> json = Json.object();
            json.put("name", macro.name());
            json.put("target", target);
            json.put("min", macro.min());
            json.put("max", macro.max());
            macros.add(json);
        }

        Map<String, Object> json = Json.object();
        if (!document.getName().isEmpty()) {
            json.put("name", document.getName());
        }
        if (document.hasOrigin()) {
            Map<String, Object> origin = Json.object();
            origin.put("category", document.getOriginCategory());
            origin.put("seed", Long.toString(document.getOriginSeed()));
            json.put("origin", origin);
        }
        json.put("nodes", nodes);
        json.put("connections", connections);
        json.put("macros", macros);
        return json;
    }

    public PatchDocument load(byte[] bytes) {
        PatchArchive archive;
        String text;
        if (PatchArchive.isArchive(bytes)) {
            archive = PatchArchive.read(bytes);
            text = archive.document();
        } else {
            archive = new PatchArchive();
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        Map<String, Object> root = Json.asObject(Json.parse(text));
        String format = Json.stringOrDefault(root, "format", "");
        if (!format.equals(FORMAT) && !format.equals(LEGACY_FORMAT)) {
            throw new SerializationException("Not a Petroleum patch file");
        }
        int version = (int) Json.number(root, "version");
        if (version > FORMAT_VERSION) {
            throw new SerializationException(
                    "Patch was written by a newer Petroleum (format version " + version
                            + ", this build reads up to " + FORMAT_VERSION + ")");
        }
        while (version < FORMAT_VERSION) {
            BiConsumer<Map<String, Object>, PatchArchive> migration = MIGRATIONS.get(version);
            if (migration == null) {
                throw new SerializationException(
                        "Patch is in format version " + version
                                + ", which this build can no longer read (no migration registered from version "
                                + version + " to " + (version + 1) + ")");
            }
            migration.accept(root, archive);
            version++;
        }

        return loadDocument(root, archive);
    }

    private PatchDocument loadDocument(Map<String, Object> root, PatchArchive archive) {
        PatchDocument document = new PatchDocument();
        document.setName(Json.stringOrDefault(root, "name", ""));
        Object origin = root.get("origin");
        if (origin != null) {
            Map<String, Object> originJson = Json.asObject(origin);
            document.setOrigin(Json.string(originJson, "category"),
                    Long.parseLong(Json.string(originJson, "seed")));
        }

        Map<String, Node> byId = new LinkedHashMap<>();
        for (Object entry : Json.asArray(root.get("nodes"))) {
            Map<String, Object> json = Json.asObject(entry);
            String id = Json.string(json, "id");
            Node node = loadNode(json, archive);
            byId.put(id, node);
            document.addNode(node, Json.numberOrDefault(json, "x", 0),
                    Json.numberOrDefault(json, "y", 0));

            Object portValues = json.get("portValues");
            if (portValues != null) {
                Map<String, Float> values = new LinkedHashMap<>();
                for (Map.Entry<String, Object> portEntry : Json.asObject(portValues).entrySet()) {
                    values.put(portEntry.getKey(), (float) Json.asDouble(portEntry.getValue()));
                }
                document.setPortValues(node, values);
            }
        }

        for (Object entry : Json.asArray(root.get("connections"))) {
            Map<String, Object> json = Json.asObject(entry);
            Node source = requireNode(byId, Json.string(json, "from"));
            Node destination = requireNode(byId, Json.string(json, "to"));
            document.addConnection(new Connection(source, Json.string(json, "fromPort"),
                    destination, Json.string(json, "toPort")));
        }

        Object macroList = root.get("macros");
        if (macroList != null) {
            for (Object entry : Json.asArray(macroList)) {
                Map<String, Object> json = Json.asObject(entry);
                Node target = requireNode(byId, Json.string(json, "target"));
                if (!(target instanceof ConstantNode constant)) {
                    throw new SerializationException("Macro '" + Json.string(json, "name")
                            + "' targets a node that is not a Constant");
                }
                document.addMacro(new Macro(Json.string(json, "name"), constant,
                        (float) Json.numberOrDefault(json, "min", 0),
                        (float) Json.numberOrDefault(json, "max", 1)));
            }
        }

        return document;
    }

    private static Node requireNode(Map<String, Node> byId, String id) {
        Node node = byId.get(id);
        if (node == null) {
            throw new SerializationException("Connection refers to unknown node id '" + id + "'");
        }
        return node;
    }

    private Map<String, Object> saveNode(Node node, String id, PatchDocument document, PatchArchive archive) {
        Map<String, Object> json = Json.object();
        json.put("id", id);
        json.put("type", typeOf(node));
        json.put("x", document.getX(node));
        json.put("y", document.getY(node));

        if (node instanceof OscillatorNode oscillatorNode) {
            json.put("oscillator", oscillatorCodec.save(oscillatorNode.getOscillator(), archive));
        } else if (node instanceof PatchNode patchNode) {
            json.put("name", patchNode.getName());
            json.put("patch", saveDocument(patchNode.getDocument(), archive));
        } else if (node instanceof SplitterNode) {
            json.put("outputs", node.getOutputPorts().size());
        } else if (node instanceof MultiplexerNode) {
            json.put("inputs", node.getInputPorts().size() - 1);
        } else if (node instanceof SequencerNode sequencer) {
            json.put("steps", sequencer.getSteps());
        } else if (node instanceof TranceGateNode tranceGate) {
            json.put("steps", tranceGate.getSteps());
        } else if (node instanceof ParametricEqNode eq) {
            json.put("bands", eq.getBands());
        } else if (node instanceof MixNode) {
            List<Object> ports = new ArrayList<>();
            for (Port port : node.getInputPorts()) {
                ports.add(port.name());
            }
            json.put("ports", ports);
        } else if (node instanceof GranularPlayerNode player) {
            json.put("name", player.getName());
            json.put("sample", archive.sampleId(player.getSamples()));
        } else if (node instanceof InstrumentNode instrumentNode) {
            json.put("channels", instrumentNode.getChannelCount());
            json.put("instrument", instrumentCodec.save(instrumentNode.getInstrument(), archive));
        } else if (node instanceof WaveshaperNode waveshaper) {
            json.put("source", waveshaper.getSource());
        } else if (node instanceof TrackerNode tracker) {
            json.put("channels", tracker.getChannelCount());
            json.put("song", archive.dataId(tracker.saveSongData()));
        } else if (node instanceof MidiPlayerNode midiPlayer) {
            json.put("channels", midiPlayer.getChannelCount());
            byte[] midi = midiPlayer.getMidiData();
            if (midi != null) {
                json.put("midi", archive.dataId(midi));
                if (midiPlayer.getFileName() != null) {
                    json.put("file", midiPlayer.getFileName());
                }
            }
        } else if (node instanceof AdditiveResynthNode resynth) {
            json.put("name", resynth.getName());
            json.put("sampleRate", resynth.getSampleRate());
            json.put("fidelity", resynth.getFidelity());
            json.put("sample", archive.sampleId(resynth.getSamples()));
        }

        if (node instanceof Stateful stateful) {
            Map<String, String> state = new LinkedHashMap<>();
            stateful.saveState(state);
            if (!state.isEmpty()) {
                json.put("state", new LinkedHashMap<String, Object>(state));
            }
        }

        Map<String, Float> portValues = document.getPortValues(node);
        if (!portValues.isEmpty()) {
            Map<String, Object> ports = Json.object();
            ports.putAll(portValues);
            json.put("portValues", ports);
        }
        return json;
    }

    private Node loadNode(Map<String, Object> json, PatchArchive archive) {
        String type = Json.string(json, "type");
        Node node = switch (type) {
            case TYPE_OSCILLATOR -> new OscillatorNode(
                    oscillatorCodec.load(Json.asObject(json.get("oscillator")), archive));
            case TYPE_PATCH -> loadPatchNode(json, archive);
            case TYPE_SPLITTER -> new SplitterNode((int) Json.numberOrDefault(json, "outputs", 4));
            case TYPE_MULTIPLEXER -> new MultiplexerNode((int) Json.numberOrDefault(json, "inputs", 4));
            case TYPE_SEQUENCER -> new SequencerNode((int) Json.numberOrDefault(json, "steps", 8));
            case TYPE_TRANCE_GATE -> new TranceGateNode((int) Json.numberOrDefault(json, "steps", 8));
            case TYPE_PARAMETRIC_EQ -> new ParametricEqNode((int) Json.numberOrDefault(json, "bands", 3));
            case TYPE_MIX -> loadMix(json);
            case TYPE_GRANULAR_PLAYER -> new GranularPlayerNode(
                    archive.sample(Json.string(json, "sample")),
                    Json.stringOrDefault(json, "name", "Granular Player"));
            case TYPE_INSTRUMENT -> loadInstrumentNode(json, archive);
            case TYPE_WAVESHAPER -> new WaveshaperNode(Json.stringOrDefault(json, "source", "x"));
            case TYPE_TRACKER -> loadTrackerNode(json, archive);
            case TYPE_MIDI_PLAYER -> loadMidiPlayerNode(json, archive);
            case TYPE_ADDITIVE_RESYNTH -> new AdditiveResynthNode(
                    archive.sample(Json.string(json, "sample")),
                    (float) Json.numberOrDefault(json, "sampleRate", 44100),
                    Json.stringOrDefault(json, "name", "Additive Resynth"),
                    (float) Json.numberOrDefault(json, "fidelity", 0.5));
            default -> createFromFactory(type);
        };

        Object state = json.get("state");
        if (state != null && node instanceof Stateful stateful) {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : Json.asObject(state).entrySet()) {
                values.put(entry.getKey(), Json.asString(entry.getValue()));
            }
            stateful.loadState(values);
        }
        return node;
    }

    private static Node loadTrackerNode(Map<String, Object> json, PatchArchive archive) {
        TrackerNode node = new TrackerNode((int) Json.numberOrDefault(json, "channels", 8));
        Object song = json.get("song");
        if (song != null) {
            try {
                node.loadSongData(archive.data(Json.asString(song)));
            } catch (RuntimeException ex) {
                throw new SerializationException("Tracker song data is corrupt", ex);
            }
        }
        return node;
    }

    private static Node loadMidiPlayerNode(Map<String, Object> json, PatchArchive archive) {
        MidiPlayerNode node = new MidiPlayerNode((int) Json.numberOrDefault(json, "channels", 8));
        Object midi = json.get("midi");
        if (midi != null) {
            try {
                node.loadMidi(archive.data(Json.asString(midi)), Json.stringOrDefault(json, "file", null));
            } catch (RuntimeException ex) {
                throw new SerializationException("MIDI data is corrupt", ex);
            }
        }
        return node;
    }

    private Node loadInstrumentNode(Map<String, Object> json, PatchArchive archive) {
        InstrumentNode node = new InstrumentNode((int) Json.numberOrDefault(json, "channels", 1));
        Instrument instrument = instrumentCodec.load(Json.asObject(json.get("instrument")), archive);
        node.setInstrument(instrument);
        return node;
    }

    private Node loadPatchNode(Map<String, Object> json, PatchArchive archive) {
        PatchDocument embedded = loadDocument(Json.asObject(json.get("patch")), archive);
        String name = Json.stringOrDefault(json, "name", embedded.getName());
        try {
            return PatchNode.create(name, embedded);
        } catch (IllegalArgumentException ex) {
            throw new SerializationException(ex.getMessage(), ex);
        }
    }

    private static Node loadMix(Map<String, Object> json) {
        Map<String, Float> weights = new LinkedHashMap<>();
        for (Object port : Json.asArray(json.get("ports"))) {
            weights.put(Json.asString(port), 1f);
        }
        return new MixNode(weights);
    }

    private Node createFromFactory(String type) {
        try {
            return nodeFactory.create(type);
        } catch (IllegalArgumentException ex) {
            throw new SerializationException("Unknown node type '" + type + "'", ex);
        }
    }

    private String typeOf(Node node) {
        if (node instanceof OscillatorNode) {
            return TYPE_OSCILLATOR;
        }
        if (node instanceof PatchNode) {
            return TYPE_PATCH;
        }
        if (node instanceof GranularPlayerNode) {
            return TYPE_GRANULAR_PLAYER;
        }
        if (node instanceof InstrumentNode) {
            return TYPE_INSTRUMENT;
        }
        if (node instanceof TrackerNode) {
            return TYPE_TRACKER;
        }
        if (node instanceof MidiPlayerNode) {
            return TYPE_MIDI_PLAYER;
        }
        if (node instanceof AdditiveResynthNode) {
            return TYPE_ADDITIVE_RESYNTH;
        }
        String name = nodeFactory.nameOf(node);
        if (name == null) {
            throw new SerializationException(
                    "Node " + node.getClass().getName() + " is not registered in NodeFactory and cannot be saved");
        }
        return name;
    }
}

package Petroleum.Gui;

import Petroleum.Graph.Node;
import Petroleum.Graph.Port;
import Petroleum.Mixers.MixNode;
import Petroleum.Modules.PatchNode;
import Petroleum.Patch.Connection;
import Petroleum.Patch.Patch;
import Petroleum.Serialization.PatchCodec;
import Petroleum.Sinks.AudioOutNode;
import Petroleum.Sources.ConstantNode;
import Petroleum.Tracker.PianoRollClip;
import Petroleum.Tracker.PianoRollNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PianoRollPreview {
    static final String FILE_NAME = "preview.ptr";
    private static final float VOICE_LEVEL = 0.25f;

    final Patch patch;
    final PianoRollNode roll;
    final ConstantNode bpm;

    static Path configDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData == null || appData.isBlank() ? home : appData, "Petroleum");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "Petroleum");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return Path.of(xdg == null || xdg.isBlank() ? Path.of(home, ".config").toString() : xdg, "petroleum");
    }

    static Path userVoiceFile() {
        return configDirectory().resolve(FILE_NAME);
    }

    static byte[] builtInVoice() {
        try (InputStream in = PianoRollPreview.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("The built-in " + FILE_NAME + " is missing from the application");
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the built-in " + FILE_NAME, ex);
        }
    }

    static byte[] userVoice() throws IOException {
        Path file = userVoiceFile();
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    PianoRollPreview(PatchCodec codec, byte[] voicePatch, PianoRollClip clip, int voiceCount, float bpmValue,
                     boolean loop) {
        roll = new PianoRollNode(voiceCount);
        roll.setClip(clip);
        bpm = new ConstantNode(bpmValue);
        ConstantNode loopValue = new ConstantNode(loop ? 1f : 0f);
        List<Node> nodes = new ArrayList<>(List.of(roll, bpm, loopValue));
        List<Connection> wires = new ArrayList<>();
        wires.add(new Connection(bpm, "out", roll, "bpm"));
        wires.add(new Connection(loopValue, "out", roll, "loop"));

        Map<String, Float> weights = new LinkedHashMap<>();
        for (int v = 0; v < voiceCount; v++) {
            weights.put("in" + v, VOICE_LEVEL);
        }
        MixNode mix = new MixNode(weights);
        AudioOutNode out = new AudioOutNode();
        nodes.add(mix);
        nodes.add(out);
        wires.add(new Connection(mix, "out", out, "in"));

        for (int v = 0; v < voiceCount; v++) {
            PatchNode voice = PatchNode.create("Preview voice", codec.load(voicePatch));
            List<String> inputs = voice.getInputPorts().stream().map(Port::name).toList();
            if (!inputs.contains("pitch") || !inputs.contains("gate")) {
                throw new IllegalArgumentException("A preview voice needs macros named \"pitch\" and \"gate\""
                        + " (and optionally \"velocity\"); this one has " + inputs);
            }
            nodes.add(voice);
            for (String port : List.of("pitch", "gate", "velocity")) {
                if (inputs.contains(port)) {
                    wires.add(new Connection(roll, port + v, voice, port));
                }
            }
            wires.add(new Connection(voice, "out", mix, "in" + v));
        }
        patch = new Patch(nodes, wires);
    }
}

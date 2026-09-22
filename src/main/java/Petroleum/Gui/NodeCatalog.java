package Petroleum.Gui;

import Petroleum.Conditionals.DemultiplexerNode;
import Petroleum.Conditionals.MultiplexerNode;
import Petroleum.Conditionals.SequencerNode;
import Petroleum.Effects.ParametricEqNode;
import Petroleum.Effects.TranceGateNode;
import Petroleum.Graph.Node;
import Petroleum.Graph.NodeFactory;
import Petroleum.Granular.GranularPlayerNode;
import Petroleum.Mixers.MixNode;
import Petroleum.Modules.PatchNode;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Oscillators.OscillatorNode;
import Petroleum.Oscillators.Implementations.DrawnWaveform;
import Petroleum.Oscillators.Implementations.DualOscillator;
import Petroleum.Oscillators.Implementations.ExpressionOscillator;
import Petroleum.Oscillators.Implementations.Wavefolder;
import Petroleum.Oscillators.Implementations.Wavetable;
import Petroleum.Oscillators.Implementations.WavetableLoader;
import Petroleum.Serialization.PatchCodec;
import Petroleum.Serialization.PatchDocument;
import Petroleum.Sinks.AudioOutNode;
import Petroleum.Spectral.AdditiveResynthNode;
import Petroleum.Tracker.MidiPlayerNode;
import Petroleum.Tracker.TrackerNode;
import Petroleum.Utility.SplitterNode;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class NodeCatalog {
    public static final List<String> CATEGORY_ORDER = List.of(
            "Sources & Sinks", "Envelopes", "Oscillators", "Oscillator Wrappers",
            "Filters", "Effects", "Granular", "Instruments", "Physical", "Spectral", "Conditionals",
            "Tracker", "Mixers", "Utility", "Patches");

    private static final Map<String, String> PACKAGE_CATEGORIES = Map.ofEntries(
            Map.entry("Petroleum.Sources", "Sources & Sinks"),
            Map.entry("Petroleum.Sinks", "Sources & Sinks"),
            Map.entry("Petroleum.Envelopes", "Envelopes"),
            Map.entry("Petroleum.Filters", "Filters"),
            Map.entry("Petroleum.Effects", "Effects"),
            Map.entry("Petroleum.Granular", "Granular"),
            Map.entry("Petroleum.Instruments", "Instruments"),
            Map.entry("Petroleum.Physical", "Physical"),
            Map.entry("Petroleum.Spectral", "Spectral"),
            Map.entry("Petroleum.Conditionals", "Conditionals"),
            Map.entry("Petroleum.Tracker", "Tracker"),
            Map.entry("Petroleum.Mixers", "Mixers"),
            Map.entry("Petroleum.Utility", "Utility"));

    // "Distortion & Waveshaping" avoids nesting a "Distortion" submenu
    // directly under a same-named "Distortion" category grouping - a literal
    // Effects > Distortion > Distortion stutter Filters > Biquad doesn't have,
    // since Biquad's parent category is plainly "Filters".
    private static final List<String> DISTORTION_TYPE_PATH = List.of("Distortion & Waveshaping", "Distortion");
    private static final List<String> COMPRESSOR_TYPE_PATH = List.of("Dynamics", "Compressor");
    private static final List<String> QUANTIZER_TYPE_PATH = List.of("Pitch & Time", "Quantizer");
    private static final List<String> PITCH_CORRECTION_TYPE_PATH = List.of("Pitch Correction");
    private static final List<String> EQ_PATH = List.of("EQ");
    private static final List<String> PARAMETRIC_EQ_PATH = List.of("EQ", "Parametric EQ");
    private static final List<String> SPLITTER_PATH = List.of("Splitter");
    private static final List<String> MULTIPLEXER_PATH = List.of("Multiplexer");
    private static final List<String> DEMULTIPLEXER_PATH = List.of("Demultiplexer");
    private static final List<String> SEQUENCER_PATH = List.of("Sequencer");
    private static final List<String> TRANCE_GATE_PATH = List.of("Trance Gate");
    private static final List<String> MIX_PATH = List.of("Mix");
    private static final List<String> LOGIC_PATH = List.of("Logic");
    private static final List<String> CLOCKING_PATH = List.of("Clocking");
    private static final List<String> STEREO_PATH = List.of("Stereo");
    private static final List<String> OUTPUTS_PATH = List.of("Outputs");
    private static final List<String> ONE_POLE_PATH = List.of("One-Pole");
    private static final List<String> REVERB_PATH = List.of("Reverb");

    /**
     * Submenu path a node sits under, within its top-level category, keyed by
     * the node's catalog label. Anything absent gets {@code List.of()} - a
     * direct child of the category, no submenu. Categories crowded enough to
     * need scanning aids (originally an 8+ flat entries bar) are split here;
     * a handful of related groupings below that bar are split too, since a
     * conceptual cluster (Logic vs. Clocking, mono vs. Stereo, sources vs.
     * Outputs) earns its own submenu once it exists, not only once the
     * category is crowded. A single leftover item never gets a one-item
     * submenu of its own.
     */
    private static final Map<String, List<String>> SUBPATH_OVERRIDES = Map.ofEntries(
            Map.entry("Phasor", List.of("Phasors")),
            Map.entry("Period Phasor", List.of("Phasors")),
            Map.entry("Unit Phasor", List.of("Phasors")),
            Map.entry("Unit Period Phasor", List.of("Phasors")),
            Map.entry("Ping Pong", List.of("Phasors")),
            Map.entry("Period Ping Pong", List.of("Phasors")),
            Map.entry("Unit Ping Pong", List.of("Phasors")),
            Map.entry("Unit Period Ping Pong", List.of("Phasors")),
            Map.entry("LFO Sine", List.of("LFO")),
            Map.entry("LFO Triangle", List.of("LFO")),
            Map.entry("LFO Square", List.of("LFO")),
            Map.entry("LFO Sawtooth", List.of("LFO")),

            Map.entry("Biquad Lowpass", List.of("Biquad")),
            Map.entry("Biquad Highpass", List.of("Biquad")),
            Map.entry("Biquad Bandpass", List.of("Biquad")),
            Map.entry("Biquad Notch", List.of("Biquad")),
            Map.entry("Biquad Allpass", List.of("Biquad")),
            Map.entry("Biquad Peaking", List.of("Biquad")),
            Map.entry("Biquad Low Shelf", List.of("Biquad")),
            Map.entry("Biquad High Shelf", List.of("Biquad")),

            Map.entry("Chorus", List.of("Modulation")),
            Map.entry("Flanger", List.of("Modulation")),
            Map.entry("Phaser", List.of("Modulation")),
            Map.entry("Tremolo", List.of("Modulation")),
            Map.entry("Vibrato", List.of("Modulation")),
            Map.entry("Ring Modulator", List.of("Modulation")),
            Map.entry("Amplitude Modulator", List.of("Modulation")),
            Map.entry("Frequency Modulator", List.of("Modulation")),
            Map.entry("Distortion Soft Clip", DISTORTION_TYPE_PATH),
            Map.entry("Distortion Hard Clip", DISTORTION_TYPE_PATH),
            Map.entry("Distortion Foldback", DISTORTION_TYPE_PATH),
            Map.entry("Distortion Overdrive", DISTORTION_TYPE_PATH),
            Map.entry("Distortion Rectify", DISTORTION_TYPE_PATH),
            Map.entry("Distortion Asymmetric", DISTORTION_TYPE_PATH),
            Map.entry("Bitcrusher", List.of("Distortion & Waveshaping")),
            Map.entry("Waveshaper", List.of("Distortion & Waveshaping")),
            Map.entry("Compressor Peak", COMPRESSOR_TYPE_PATH),
            Map.entry("Compressor RMS", COMPRESSOR_TYPE_PATH),
            Map.entry("Compressor Feedback", COMPRESSOR_TYPE_PATH),
            Map.entry("Limiter", List.of("Dynamics")),
            Map.entry("Noise Gate", List.of("Dynamics")),
            Map.entry("Level Follower", List.of("Dynamics")),
            Map.entry("Reverb", REVERB_PATH),
            Map.entry("Spring Reverb", REVERB_PATH),
            Map.entry("One-Pole Filter", ONE_POLE_PATH),
            Map.entry("One-Pole Highpass", ONE_POLE_PATH),

            Map.entry("Multiplier", List.of("Signal Math")),
            Map.entry("Difference", List.of("Signal Math")),
            Map.entry("Amplifier", List.of("Signal Math")),
            Map.entry("Attenuverter", List.of("Signal Math")),
            Map.entry("Invert", List.of("Signal Math")),
            Map.entry("Map Range", List.of("Signal Math")),
            Map.entry("Clamp", List.of("Signal Math")),
            Map.entry("Round", List.of("Signal Math")),
            Map.entry("Ceil", List.of("Signal Math")),
            Map.entry("Floor", List.of("Signal Math")),
            Map.entry("Slew", List.of("Signal Math")),
            Map.entry("Pitch to Time", List.of("Pitch & Time")),
            Map.entry("Time to Pitch", List.of("Pitch & Time")),
            Map.entry("Quantizer Chromatic", QUANTIZER_TYPE_PATH),
            Map.entry("Quantizer Major", QUANTIZER_TYPE_PATH),
            Map.entry("Quantizer Minor", QUANTIZER_TYPE_PATH),
            Map.entry("Quantizer Major Pentatonic", QUANTIZER_TYPE_PATH),
            Map.entry("Quantizer Minor Pentatonic", QUANTIZER_TYPE_PATH),
            Map.entry("Pitch Correction Chromatic", PITCH_CORRECTION_TYPE_PATH),
            Map.entry("Pitch Correction Major", PITCH_CORRECTION_TYPE_PATH),
            Map.entry("Pitch Correction Minor", PITCH_CORRECTION_TYPE_PATH),
            Map.entry("Pitch Correction Major Pentatonic", PITCH_CORRECTION_TYPE_PATH),
            Map.entry("Pitch Correction Minor Pentatonic", PITCH_CORRECTION_TYPE_PATH),
            Map.entry("Tone Stack", EQ_PATH),
            Map.entry("Graphic EQ", EQ_PATH),
            Map.entry("Parametric EQ", PARAMETRIC_EQ_PATH),
            Map.entry("Parametric EQ 1", PARAMETRIC_EQ_PATH),
            Map.entry("Parametric EQ 5", PARAMETRIC_EQ_PATH),

            Map.entry("Splitter", SPLITTER_PATH),
            Map.entry("Splitter 2", SPLITTER_PATH),
            Map.entry("Splitter 8", SPLITTER_PATH),
            Map.entry("Multiplexer", MULTIPLEXER_PATH),
            Map.entry("Multiplexer 2", MULTIPLEXER_PATH),
            Map.entry("Multiplexer 8", MULTIPLEXER_PATH),
            Map.entry("Demultiplexer", DEMULTIPLEXER_PATH),
            Map.entry("Demultiplexer 2", DEMULTIPLEXER_PATH),
            Map.entry("Demultiplexer 8", DEMULTIPLEXER_PATH),
            Map.entry("Sequencer", SEQUENCER_PATH),
            Map.entry("Sequencer 4", SEQUENCER_PATH),
            Map.entry("Sequencer 16", SEQUENCER_PATH),
            Map.entry("Trance Gate", TRANCE_GATE_PATH),
            Map.entry("Trance Gate 4", TRANCE_GATE_PATH),
            Map.entry("Trance Gate 16", TRANCE_GATE_PATH),
            Map.entry("Mix", MIX_PATH),
            Map.entry("Mix 2", MIX_PATH),
            Map.entry("Mix 8", MIX_PATH),

            Map.entry("Select", LOGIC_PATH),
            Map.entry("CV Gate", LOGIC_PATH),
            Map.entry("Toggle", LOGIC_PATH),
            Map.entry("Latch", CLOCKING_PATH),
            Map.entry("Queue Quantizer", CLOCKING_PATH),
            Map.entry("Counter", CLOCKING_PATH),
            Map.entry("Clock Divider", CLOCKING_PATH),
            Map.entry("Rungler", CLOCKING_PATH),

            Map.entry("Panner Equal Power", STEREO_PATH),
            Map.entry("Panner Linear", STEREO_PATH),
            Map.entry("Stereo Mix", STEREO_PATH),

            Map.entry("Audio Out", OUTPUTS_PATH),
            Map.entry("Stereo Audio Out", OUTPUTS_PATH));

    private static final List<String> WAVEFORM_PATH = List.of("Waveforms");
    private static final List<String> NOISE_PATH = List.of("Noise");
    private static final List<String> GENERATOR_PATH = List.of("Generators");
    private static final List<String> NO_PATH = List.of();

    public record Entry(String category, List<String> subPath, String label,
                         Function<Window, Optional<Node>> action) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public NodeCatalog(NodeFactory nodeFactory, OscillatorFactory oscillatorFactory, PatchCodec patchCodec) {
        for (String key : nodeFactory.names()) {
            Node probe = nodeFactory.create(key);
            String category = PACKAGE_CATEGORIES.getOrDefault(probe.getClass().getPackageName(), "Utility");
            List<String> subPath = SUBPATH_OVERRIDES.getOrDefault(key, NO_PATH);
            entries.add(new Entry(category, subPath, key, window -> Optional.of(nodeFactory.create(key))));
        }

        for (String key : oscillatorFactory.names()) {
            List<String> subPath = oscillatorSubPath(key);
            entries.add(new Entry("Oscillators", subPath, key,
                    window -> Optional.of(new OscillatorNode(oscillatorFactory.create(key)))));
        }

        entries.add(new Entry("Oscillator Wrappers", NO_PATH, "Dual Oscillator",
                window -> Optional.of(new OscillatorNode(
                        new DualOscillator(oscillatorFactory.create("Sine"), DualOscillator.DEFAULT_OFFSET)))));
        entries.add(new Entry("Oscillator Wrappers", NO_PATH, "Wavefolder",
                window -> Optional.of(new OscillatorNode(
                        new Wavefolder(oscillatorFactory.create("Sine"), Wavefolder.DEFAULT_GAIN)))));

        entries.add(new Entry("Oscillators", NO_PATH, "Sound Clip",
                window -> Optional.of(new OscillatorNode(new Wavetable(new float[64], "Sound Clip")))));
        entries.add(new Entry("Oscillators", GENERATOR_PATH, "Expression",
                window -> Optional.of(new OscillatorNode(new ExpressionOscillator("sin(x)")))));
        entries.add(new Entry("Oscillators", NO_PATH, "Draw Waveform",
                window -> Optional.of(new OscillatorNode(
                        new DrawnWaveform(new float[DrawnWaveform.TABLE_SIZE], "Drawn Waveform")))));

        entries.add(new Entry("Patches", NO_PATH, "Load patch as node...", window -> loadPatchNode(window, patchCodec)));
        entries.add(new Entry("Patches", NO_PATH, "New Subpatch", window -> newSubpatch()));

        entries.add(new Entry("Granular", NO_PATH, "Granular Player", NodeCatalog::loadGranularPlayerSample));

        entries.add(new Entry("Spectral", NO_PATH, "Additive Resynth", NodeCatalog::loadAdditiveResynthSample));

        entries.add(new Entry("Utility", SPLITTER_PATH, "Splitter (Custom Size)...",
                window -> customSized(window, "Splitter", "Number of outputs:", SplitterNode::new)));
        entries.add(new Entry("Conditionals", MULTIPLEXER_PATH, "Multiplexer (Custom Size)...",
                window -> customSized(window, "Multiplexer", "Number of inputs:", MultiplexerNode::new)));
        entries.add(new Entry("Conditionals", DEMULTIPLEXER_PATH, "Demultiplexer (Custom Size)...",
                window -> customSized(window, "Demultiplexer", "Number of outputs:", DemultiplexerNode::new)));
        entries.add(new Entry("Conditionals", SEQUENCER_PATH, "Sequencer (Custom Size)...",
                window -> customSized(window, "Sequencer", "Number of steps:", SequencerNode::new)));
        entries.add(new Entry("Effects", TRANCE_GATE_PATH, "Trance Gate (Custom Size)...",
                window -> customSized(window, "Trance Gate", "Number of steps:", TranceGateNode::new)));
        entries.add(new Entry("Effects", PARAMETRIC_EQ_PATH, "Parametric EQ (Custom Size)...",
                window -> customSized(window, "Parametric EQ", "Number of bands:", ParametricEqNode::new)));
        entries.add(new Entry("Mixers", MIX_PATH, "Mix (Custom Size)...",
                window -> customSized(window, "Mix", "Number of inputs:", NodeCatalog::mixOfSize)));
        entries.add(new Entry("Tracker", NO_PATH, "Tracker (Custom Size)...",
                window -> customSized(window, "Tracker", "Number of channels:", TrackerNode::new)));
        entries.add(new Entry("Tracker", NO_PATH, "MIDI Player (Custom Size)...",
                window -> customSized(window, "MIDI Player", "Number of voices:", MidiPlayerNode::new)));
    }

    private static Optional<Node> customSized(Window window, String title, String prompt, IntFunction<Node> factory) {
        return promptSize(window, title, prompt).map(factory::apply);
    }

    private static Node mixOfSize(int size) {
        Map<String, Float> weights = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            weights.put("in" + i, 1f);
        }
        return new MixNode(weights);
    }

    private static Optional<Integer> promptSize(Window window, String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog("4");
        dialog.setTitle(title + " Size");
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        Optional<String> text = dialog.showAndWait();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        int size;
        try {
            size = Integer.parseInt(text.get().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid size: " + text.get());
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        return Optional.of(size);
    }

    private static List<String> oscillatorSubPath(String name) {
        return switch (name) {
            case "Sine", "Cosine", "Sawtooth", "Square", "Triangle" -> WAVEFORM_PATH;
            case "White Noise", "Pink Noise", "Brown Noise", "Blue Noise", "Violet Noise", "Grey Noise" -> NOISE_PATH;
            case "Sample & Hold", "Chaotic", "SuperSaw", "FM" -> GENERATOR_PATH;
            default -> NO_PATH;
        };
    }

    private static Optional<Node> loadGranularPlayerSample(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import WAV file for granular playback");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return Optional.empty();
        }
        try {
            float[] samples = WavetableLoader.loadMonoSamples(file);
            return Optional.of(new GranularPlayerNode(samples, file.getName()));
        } catch (IOException | UnsupportedAudioFileException ex) {
            throw new IllegalArgumentException("Could not import " + file.getName() + ": " + ex.getMessage());
        }
    }

    private static Optional<Node> loadAdditiveResynthSample(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import WAV file for additive resynthesis");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav"));
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return Optional.empty();
        }

        TextInputDialog fidelityDialog = new TextInputDialog("50");
        fidelityDialog.setTitle("Additive Resynth Fidelity");
        fidelityDialog.setHeaderText(null);
        fidelityDialog.setContentText("Fidelity (0-100%) - how many partials to track:");
        Optional<String> fidelityText = fidelityDialog.showAndWait();
        if (fidelityText.isEmpty()) {
            return Optional.empty();
        }
        int fidelityPercent;
        try {
            fidelityPercent = Math.max(0, Math.min(100, Integer.parseInt(fidelityText.get().trim())));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid fidelity: " + fidelityText.get());
        }
        float fidelity = fidelityPercent / 100f;

        try {
            float sampleRate = AudioSystem.getAudioFileFormat(file).getFormat().getSampleRate();
            float[] samples = WavetableLoader.loadMonoSamples(file);
            String name = file.getName() + " (" + fidelityPercent + "% fidelity)";
            return Optional.of(new AdditiveResynthNode(samples, sampleRate, name, fidelity));
        } catch (IOException | UnsupportedAudioFileException ex) {
            throw new IllegalArgumentException("Could not import " + file.getName() + ": " + ex.getMessage());
        }
    }

    private static Optional<Node> loadPatchNode(Window window, PatchCodec codec) {
        FileChooser chooser = Dialogs.jsynthFileChooser("Load patch as a node");
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return Optional.empty();
        }
        PatchDocument document;
        try {
            document = codec.load(Files.readAllBytes(file.toPath()));
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("Could not load " + file.getName() + ": " + ex.getMessage());
        }
        String name = document.getName().isBlank() ? Dialogs.stripExtension(file.getName()) : document.getName();
        return Optional.of(PatchNode.create(name, document));
    }

    private static Optional<Node> newSubpatch() {
        PatchDocument document = new PatchDocument();
        document.addNode(new AudioOutNode(), 300, 150);
        return Optional.of(PatchNode.create("Subpatch", document));
    }

    public List<Entry> getEntries() {
        return List.copyOf(entries);
    }
}

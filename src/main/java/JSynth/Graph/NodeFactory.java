package JSynth.Graph;

import JSynth.Conditionals.ClockDividerNode;
import JSynth.Conditionals.CounterNode;
import JSynth.Conditionals.DemultiplexerNode;
import JSynth.Conditionals.GateNode;
import JSynth.Conditionals.LatchNode;
import JSynth.Conditionals.MultiplexerNode;
import JSynth.Conditionals.RunglerNode;
import JSynth.Conditionals.SelectNode;
import JSynth.Conditionals.SequencerNode;
import JSynth.Conditionals.ToggleNode;
import JSynth.Effects.AmplitudeModulatorNode;
import JSynth.Effects.AutoWahNode;
import JSynth.Effects.BitcrusherNode;
import JSynth.Effects.ChorusNode;
import JSynth.Effects.CompressorNode;
import JSynth.Effects.DelayNode;
import JSynth.Effects.DistortionNode;
import JSynth.Effects.EnvelopeFollowerNode;
import JSynth.Effects.FlangerNode;
import JSynth.Effects.FrequencyModulatorNode;
import JSynth.Effects.FrequencyShifterNode;
import JSynth.Effects.GraphicEqNode;
import JSynth.Effects.LimiterNode;
import JSynth.Effects.NoiseGateNode;
import JSynth.Effects.ParametricEqNode;
import JSynth.Effects.PhaserNode;
import JSynth.Effects.ReverbNode;
import JSynth.Effects.RingModulatorNode;
import JSynth.Effects.SpringReverbNode;
import JSynth.Effects.ToneStackNode;
import JSynth.Effects.TranceGateNode;
import JSynth.Effects.TremoloNode;
import JSynth.Effects.VibratoNode;
import JSynth.Effects.WaveshaperNode;
import JSynth.Envelopes.AdsrNode;
import JSynth.Envelopes.ArpeggioNode;
import JSynth.Envelopes.EnvelopeNode;
import JSynth.Envelopes.FunctionGeneratorNode;
import JSynth.Envelopes.RampNode;
import JSynth.Envelopes.TriggerNode;
import JSynth.Filters.BiquadFilterNode;
import JSynth.Filters.CombFilterNode;
import JSynth.Filters.LadderFilterNode;
import JSynth.Filters.LowPassGateNode;
import JSynth.Filters.OnePoleFilterNode;
import JSynth.Filters.OnePoleHighpassNode;
import JSynth.Filters.StateVariableFilterNode;
import JSynth.Granular.GranularCloudNode;
import JSynth.Granular.GranularDelayNode;
import JSynth.Granular.GranularFreezeNode;
import JSynth.Instruments.InstrumentNode;
import JSynth.Physical.ResonatorNode;
import JSynth.Mixers.CrossfadeNode;
import JSynth.Mixers.MixNode;
import JSynth.Mixers.PannerNode;
import JSynth.Mixers.StereoMixNode;
import JSynth.Sinks.AudioOutNode;
import JSynth.Sinks.StereoAudioOutNode;
import JSynth.Spectral.PhaseVocoderPitchShiftNode;
import JSynth.Spectral.SpectralFreezeNode;
import JSynth.Spectral.SpectralGateNode;
import JSynth.Sources.ConstantNode;
import JSynth.Sources.LfoNode;
import JSynth.Sources.MidiInputNode;
import JSynth.Sources.NoteNode;
import JSynth.Sources.PhasorNode;
import JSynth.Sources.RandomNode;
import JSynth.Tracker.TrackerNode;
import JSynth.Utility.AmplifierNode;
import JSynth.Utility.AttenuverterNode;
import JSynth.Utility.CeilNode;
import JSynth.Utility.ClampNode;
import JSynth.Utility.FloorNode;
import JSynth.Utility.InvertNode;
import JSynth.Utility.MapRangeNode;
import JSynth.Utility.MultiplierNode;
import JSynth.Utility.PitchToTimeNode;
import JSynth.Utility.QuantizerNode;
import JSynth.Utility.RoundNode;
import JSynth.Utility.SlewNode;
import JSynth.Utility.SplitterNode;
import JSynth.Utility.TimeToPitchNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class NodeFactory {

    private final Map<String, Supplier<Node>> registry = new LinkedHashMap<>();

    public NodeFactory() {
        register("Constant", ConstantNode::new);
        register("Note", NoteNode::new);
        register("MIDI Input", MidiInputNode::new);
        register("Phasor", () -> new PhasorNode("Phasor", false, PhasorNode.TWO_PI, false));
        register("Period Phasor", () -> new PhasorNode("Period Phasor", true, PhasorNode.TWO_PI, false));
        register("Unit Phasor", () -> new PhasorNode("Unit Phasor", false, PhasorNode.UNIT_RANGE, false));
        register("Unit Period Phasor", () -> new PhasorNode("Unit Period Phasor", true, PhasorNode.UNIT_RANGE, false));
        register("Random", RandomNode::new);
        register("Ping Pong", () -> new PhasorNode("Ping Pong", false, PhasorNode.TWO_PI, true));
        register("Period Ping Pong", () -> new PhasorNode("Period Ping Pong", true, PhasorNode.TWO_PI, true));
        register("Unit Ping Pong", () -> new PhasorNode("Unit Ping Pong", false, PhasorNode.UNIT_RANGE, true));
        register("Unit Period Ping Pong", () -> new PhasorNode("Unit Period Ping Pong", true, PhasorNode.UNIT_RANGE, true));
        register("LFO Sine", () -> new LfoNode(LfoNode.Shape.SINE));
        register("LFO Triangle", () -> new LfoNode(LfoNode.Shape.TRIANGLE));
        register("LFO Square", () -> new LfoNode(LfoNode.Shape.SQUARE));
        register("LFO Sawtooth", () -> new LfoNode(LfoNode.Shape.SAWTOOTH));
        register("Audio Out", AudioOutNode::new);
        register("Stereo Audio Out", StereoAudioOutNode::new);

        register("Trigger", TriggerNode::new);
        register("Envelope", EnvelopeNode::new);
        register("ADSR", AdsrNode::new);
        register("Ramp", RampNode::new);
        register("Function Generator", FunctionGeneratorNode::new);
        register("Arpeggio", ArpeggioNode::new);

        register("Select", SelectNode::new);
        register("CV Gate", GateNode::new);
        register("Toggle", ToggleNode::new);
        register("Multiplexer", () -> new MultiplexerNode(4));
        register("Multiplexer 2", () -> new MultiplexerNode(2));
        register("Multiplexer 8", () -> new MultiplexerNode(8));
        register("Demultiplexer", () -> new DemultiplexerNode(4));
        register("Demultiplexer 2", () -> new DemultiplexerNode(2));
        register("Demultiplexer 8", () -> new DemultiplexerNode(8));
        register("Latch", LatchNode::new);
        register("Counter", CounterNode::new);
        register("Rungler", RunglerNode::new);
        register("Clock Divider", ClockDividerNode::new);
        register("Sequencer", () -> new SequencerNode(8));
        register("Sequencer 4", () -> new SequencerNode(4));
        register("Sequencer 16", () -> new SequencerNode(16));
        register("Tracker 2", () -> new TrackerNode(2));
        register("Tracker 4", () -> new TrackerNode(4));
        register("Tracker 8", () -> new TrackerNode(8));
        register("Tracker 16", () -> new TrackerNode(16));

        register("One-Pole Filter", OnePoleFilterNode::new);
        register("One-Pole Highpass", OnePoleHighpassNode::new);
        register("Biquad Lowpass", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.LOWPASS));
        register("Biquad Highpass", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.HIGHPASS));
        register("Biquad Bandpass", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.BANDPASS));
        register("Biquad Notch", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.NOTCH));
        register("Biquad Allpass", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.ALLPASS));
        register("Biquad Peaking", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.PEAKING));
        register("Biquad Low Shelf", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.LOWSHELF));
        register("Biquad High Shelf", () -> new BiquadFilterNode(BiquadFilterNode.FilterType.HIGHSHELF));
        register("State Variable Filter", StateVariableFilterNode::new);
        register("Ladder Filter", LadderFilterNode::new);
        register("Comb Filter", CombFilterNode::new);
        register("Low-Pass Gate", LowPassGateNode::new);

        register("Delay", DelayNode::new);
        register("Chorus", ChorusNode::new);
        register("Flanger", FlangerNode::new);
        register("Tremolo", TremoloNode::new);
        register("Trance Gate", () -> new TranceGateNode(8));
        register("Trance Gate 4", () -> new TranceGateNode(4));
        register("Trance Gate 16", () -> new TranceGateNode(16));
        register("Distortion Soft Clip", () -> new DistortionNode(DistortionNode.DistortionType.SOFT_CLIP));
        register("Distortion Hard Clip", () -> new DistortionNode(DistortionNode.DistortionType.HARD_CLIP));
        register("Distortion Foldback", () -> new DistortionNode(DistortionNode.DistortionType.FOLDBACK));
        register("Distortion Overdrive", () -> new DistortionNode(DistortionNode.DistortionType.OVERDRIVE));
        register("Distortion Rectify", () -> new DistortionNode(DistortionNode.DistortionType.RECTIFY));
        register("Distortion Asymmetric", () -> new DistortionNode(DistortionNode.DistortionType.ASYMMETRIC));
        register("Bitcrusher", BitcrusherNode::new);
        register("Ring Modulator", RingModulatorNode::new);
        register("Frequency Shifter", FrequencyShifterNode::new);
        register("Compressor Peak", () -> new CompressorNode(CompressorNode.CompressorType.PEAK));
        register("Compressor RMS", () -> new CompressorNode(CompressorNode.CompressorType.RMS));
        register("Compressor Feedback", () -> new CompressorNode(CompressorNode.CompressorType.FEEDBACK));
        register("Reverb", ReverbNode::new);
        register("Spring Reverb", SpringReverbNode::new);
        register("Phaser", PhaserNode::new);
        register("Limiter", LimiterNode::new);
        register("Noise Gate", NoiseGateNode::new);
        register("Vibrato", VibratoNode::new);
        register("Waveshaper", () -> new WaveshaperNode("x"));
        register("Level Follower", EnvelopeFollowerNode::new);
        register("Auto Wah", AutoWahNode::new);
        register("Amplitude Modulator", AmplitudeModulatorNode::new);
        register("Frequency Modulator", FrequencyModulatorNode::new);
        register("Tone Stack", ToneStackNode::new);
        register("Parametric EQ", () -> new ParametricEqNode(3));
        register("Parametric EQ 1", () -> new ParametricEqNode(1));
        register("Parametric EQ 5", () -> new ParametricEqNode(5));
        register("Graphic EQ", GraphicEqNode::new);

        register("Granular Freeze", GranularFreezeNode::new);
        register("Granular Delay", GranularDelayNode::new);
        register("Granular Cloud", GranularCloudNode::new);
        register("Resonator", ResonatorNode::new);

        register("Instrument", InstrumentNode::new);

        register("Spectral Freeze", SpectralFreezeNode::new);
        register("Spectral Gate", SpectralGateNode::new);
        register("Phase Vocoder Pitch Shift", PhaseVocoderPitchShiftNode::new);

        register("Multiplier", MultiplierNode::new);
        register("Attenuverter", AttenuverterNode::new);
        register("Invert", InvertNode::new);
        register("Quantizer Chromatic", () -> new QuantizerNode(QuantizerNode.Scale.CHROMATIC));
        register("Quantizer Major", () -> new QuantizerNode(QuantizerNode.Scale.MAJOR));
        register("Quantizer Minor", () -> new QuantizerNode(QuantizerNode.Scale.MINOR));
        register("Quantizer Major Pentatonic", () -> new QuantizerNode(QuantizerNode.Scale.MAJOR_PENTATONIC));
        register("Quantizer Minor Pentatonic", () -> new QuantizerNode(QuantizerNode.Scale.MINOR_PENTATONIC));

        register("Mix", () -> new MixNode(defaultMixWeights(4)));
        register("Mix 2", () -> new MixNode(defaultMixWeights(2)));
        register("Mix 8", () -> new MixNode(defaultMixWeights(8)));
        register("Crossfade", CrossfadeNode::new);
        register("Panner Linear", () -> new PannerNode(PannerNode.PanLaw.LINEAR));
        register("Panner Equal Power", () -> new PannerNode(PannerNode.PanLaw.EQUAL_POWER));
        register("Stereo Mix", StereoMixNode::new);

        register("Splitter", SplitterNode::new);
        register("Splitter 2", () -> new SplitterNode(2));
        register("Splitter 8", () -> new SplitterNode(8));
        register("Amplifier", AmplifierNode::new);
        register("Map Range", MapRangeNode::new);
        register("Clamp", ClampNode::new);
        register("Round", RoundNode::new);
        register("Ceil", CeilNode::new);
        register("Floor", FloorNode::new);
        register("Slew", SlewNode::new);
        register("Pitch to Time", PitchToTimeNode::new);
        register("Time to Pitch", TimeToPitchNode::new);
    }

    private static Map<String, Float> defaultMixWeights(int count) {
        Map<String, Float> weights = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            weights.put("in" + i, 1f);
        }
        return weights;
    }

    public void register(String name, Supplier<Node> supplier) {
        registry.put(name, supplier);
    }

    public Node create(String name) {
        Supplier<Node> supplier = registry.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown node: " + name);
        }
        return supplier.get();
    }

    public List<String> names() {
        return List.copyOf(registry.keySet());
    }

    public String nameOf(Node node) {
        String name = node.getName();
        return registry.containsKey(name) ? name : null;
    }
}

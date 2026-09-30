package Petroleum.Graph;

import Petroleum.Conditionals.ClockDividerNode;
import Petroleum.Conditionals.CounterNode;
import Petroleum.Conditionals.DemultiplexerNode;
import Petroleum.Conditionals.GateNode;
import Petroleum.Conditionals.LatchNode;
import Petroleum.Conditionals.QueueQuantizerNode;
import Petroleum.Conditionals.MultiplexerNode;
import Petroleum.Conditionals.RunglerNode;
import Petroleum.Conditionals.SelectNode;
import Petroleum.Conditionals.SequencerNode;
import Petroleum.Conditionals.ToggleNode;
import Petroleum.Dsp.PitchQuantizer;
import Petroleum.Effects.AmplitudeModulatorNode;
import Petroleum.Effects.AutoWahNode;
import Petroleum.Effects.BitcrusherNode;
import Petroleum.Effects.ChorusNode;
import Petroleum.Effects.CompressorNode;
import Petroleum.Effects.DelayNode;
import Petroleum.Effects.DistortionNode;
import Petroleum.Effects.EnvelopeFollowerNode;
import Petroleum.Effects.FlangerNode;
import Petroleum.Effects.FrequencyModulatorNode;
import Petroleum.Effects.FrequencyShifterNode;
import Petroleum.Effects.GraphicEqNode;
import Petroleum.Effects.LimiterNode;
import Petroleum.Effects.NoiseGateNode;
import Petroleum.Effects.ParametricEqNode;
import Petroleum.Effects.PhaserNode;
import Petroleum.Effects.ReverbNode;
import Petroleum.Effects.RingModulatorNode;
import Petroleum.Effects.SpringReverbNode;
import Petroleum.Effects.ToneStackNode;
import Petroleum.Effects.TranceGateNode;
import Petroleum.Effects.TremoloNode;
import Petroleum.Effects.VibratoNode;
import Petroleum.Effects.WaveshaperNode;
import Petroleum.Envelopes.AdsrNode;
import Petroleum.Envelopes.ArpeggioNode;
import Petroleum.Envelopes.EnvelopeNode;
import Petroleum.Envelopes.FunctionGeneratorNode;
import Petroleum.Envelopes.RampNode;
import Petroleum.Envelopes.TriggerNode;
import Petroleum.Special.TurretNode;
import Petroleum.Filters.BiquadFilterNode;
import Petroleum.Filters.CombFilterNode;
import Petroleum.Filters.LadderFilterNode;
import Petroleum.Filters.LowPassGateNode;
import Petroleum.Filters.OnePoleFilterNode;
import Petroleum.Filters.OnePoleHighpassNode;
import Petroleum.Filters.StateVariableFilterNode;
import Petroleum.Granular.GranularCloudNode;
import Petroleum.Granular.GranularDelayNode;
import Petroleum.Granular.GranularFreezeNode;
import Petroleum.Instruments.InstrumentNode;
import Petroleum.Physical.ResonatorNode;
import Petroleum.Mixers.CrossfadeNode;
import Petroleum.Mixers.MixNode;
import Petroleum.Mixers.PannerNode;
import Petroleum.Mixers.StereoMixNode;
import Petroleum.Sinks.AudioOutNode;
import Petroleum.Sinks.RecordingEndNode;
import Petroleum.Sinks.StereoAudioOutNode;
import Petroleum.Sinks.StereoVirtualLineOutNode;
import Petroleum.Sinks.VirtualLineOutNode;
import Petroleum.Spectral.PhaseVocoderPitchShiftNode;
import Petroleum.Spectral.PitchCorrectionNode;
import Petroleum.Spectral.SpectralFreezeNode;
import Petroleum.Spectral.SpectralGateNode;
import Petroleum.Sources.ConstantNode;
import Petroleum.Sources.LfoNode;
import Petroleum.Sources.MicInputNode;
import Petroleum.Sources.MidiInputNode;
import Petroleum.Sources.NoteNode;
import Petroleum.Sources.PhasorNode;
import Petroleum.Sources.RandomNode;
import Petroleum.Tracker.MidiPlayerNode;
import Petroleum.Tracker.PianoRollNode;
import Petroleum.Tracker.TrackerNode;
import Petroleum.Utility.AmplifierNode;
import Petroleum.Utility.AnnotationNode;
import Petroleum.Utility.AttenuverterNode;
import Petroleum.Utility.CeilNode;
import Petroleum.Utility.ClampNode;
import Petroleum.Utility.DifferenceNode;
import Petroleum.Utility.FloorNode;
import Petroleum.Utility.InvertNode;
import Petroleum.Utility.MapRangeNode;
import Petroleum.Utility.MultiplierNode;
import Petroleum.Utility.PitchToTimeNode;
import Petroleum.Utility.QuantizerNode;
import Petroleum.Utility.RoundNode;
import Petroleum.Utility.SlewNode;
import Petroleum.Utility.SplitterNode;
import Petroleum.Utility.TimeToPitchNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class NodeFactory {

    private final Map<String, Supplier<Node>> registry = new LinkedHashMap<>();

    public NodeFactory() {
        register("Constant", ConstantNode::new);
        register("Note", NoteNode::new);
        register("MIDI Input", MidiInputNode::new);
        register("Mic Input", MicInputNode::new);
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
        register("Recording End", RecordingEndNode::new);
        register("Virtual Line Out", VirtualLineOutNode::new);
        register("Stereo Virtual Line Out", StereoVirtualLineOutNode::new);

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
        register("Queue Quantizer", QueueQuantizerNode::new);
        register("Counter", CounterNode::new);
        register("Rungler", RunglerNode::new);
        register("Clock Divider", ClockDividerNode::new);
        register("Turret", TurretNode::new);
        register("Sequencer", () -> new SequencerNode(8));
        register("Sequencer 4", () -> new SequencerNode(4));
        register("Sequencer 16", () -> new SequencerNode(16));
        register("Tracker 2", () -> new TrackerNode(2));
        register("Tracker 4", () -> new TrackerNode(4));
        register("Tracker 8", () -> new TrackerNode(8));
        register("Tracker 16", () -> new TrackerNode(16));
        register("Piano Roll 4", () -> new PianoRollNode(4));
        register("Piano Roll 8", () -> new PianoRollNode(8));
        register("Piano Roll 16", () -> new PianoRollNode(16));
        register("MIDI Player 2", () -> new MidiPlayerNode(2));
        register("MIDI Player 4", () -> new MidiPlayerNode(4));
        register("MIDI Player 8", () -> new MidiPlayerNode(8));
        register("MIDI Player 16", () -> new MidiPlayerNode(16));

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

        register("Instrument 2", () -> new InstrumentNode(2));
        register("Instrument 4", () -> new InstrumentNode(4));
        register("Instrument 8", () -> new InstrumentNode(8));
        register("Instrument 16", () -> new InstrumentNode(16));

        register("Spectral Freeze", SpectralFreezeNode::new);
        register("Spectral Gate", SpectralGateNode::new);
        register("Phase Vocoder Pitch Shift", PhaseVocoderPitchShiftNode::new);
        register("Pitch Correction Chromatic", () -> new PitchCorrectionNode(PitchQuantizer.Scale.CHROMATIC));
        register("Pitch Correction Major", () -> new PitchCorrectionNode(PitchQuantizer.Scale.MAJOR));
        register("Pitch Correction Minor", () -> new PitchCorrectionNode(PitchQuantizer.Scale.MINOR));
        register("Pitch Correction Major Pentatonic",
                () -> new PitchCorrectionNode(PitchQuantizer.Scale.MAJOR_PENTATONIC));
        register("Pitch Correction Minor Pentatonic",
                () -> new PitchCorrectionNode(PitchQuantizer.Scale.MINOR_PENTATONIC));

        register("Multiplier", MultiplierNode::new);
        register("Difference", DifferenceNode::new);
        register("Attenuverter", AttenuverterNode::new);
        register("Invert", InvertNode::new);
        register("Quantizer Chromatic", () -> new QuantizerNode(PitchQuantizer.Scale.CHROMATIC));
        register("Quantizer Major", () -> new QuantizerNode(PitchQuantizer.Scale.MAJOR));
        register("Quantizer Minor", () -> new QuantizerNode(PitchQuantizer.Scale.MINOR));
        register("Quantizer Major Pentatonic", () -> new QuantizerNode(PitchQuantizer.Scale.MAJOR_PENTATONIC));
        register("Quantizer Minor Pentatonic", () -> new QuantizerNode(PitchQuantizer.Scale.MINOR_PENTATONIC));

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
        register("Annotation", AnnotationNode::new);
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

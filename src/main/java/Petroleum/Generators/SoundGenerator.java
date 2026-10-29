package Petroleum.Generators;

import Petroleum.Envelopes.EnvelopeNode;
import Petroleum.Envelopes.RampNode;
import Petroleum.Envelopes.TriggerNode;
import Petroleum.Filters.BiquadFilterNode;
import Petroleum.Generators.CategorySpec.Envelope;
import Petroleum.Generators.CategorySpec.Pitch;
import Petroleum.Generators.CategorySpec.Range;
import Petroleum.Oscillators.Implementations.NoiseOscillator;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Oscillators.OscillatorNode;
import Petroleum.Patch.Connection;
import Petroleum.Serialization.Macro;
import Petroleum.Serialization.PatchDocument;
import Petroleum.Sinks.AudioOutNode;
import Petroleum.Sources.ConstantNode;
import Petroleum.Sources.PhasorNode;
import Petroleum.Utility.AmplifierNode;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SoundGenerator {
    public static final String MACRO_PITCH = "Pitch";
    public static final String MACRO_SLIDE = "Slide";
    public static final String MACRO_SLIDE_TIME = "Slide Time";
    public static final String MACRO_ATTACK = "Attack";
    public static final String MACRO_SUSTAIN = "Sustain";
    public static final String MACRO_PUNCH = "Punch";
    public static final String MACRO_DECAY = "Decay";
    public static final String MACRO_BRIGHT = "Bright";

    private static final float MIN_FREQUENCY = 20f;
    private static final float MAX_FREQUENCY = 12000f;
    private static final float MAX_STAGE_SECONDS = 2f;

    private static final Map<SoundCategory, CategorySpec> SPECS = buildSpecs();
    private static final SoundCategory[] ROLLABLE = Arrays.stream(SoundCategory.values())
            .filter(category -> category != SoundCategory.RANDOM)
            .toArray(SoundCategory[]::new);

    private final OscillatorFactory oscillatorFactory;

    public SoundGenerator(OscillatorFactory oscillatorFactory) {
        this.oscillatorFactory = oscillatorFactory;
    }

    public PatchDocument generate(SoundCategory category, long seed) {
        Random random = new Random(seed);
        SoundCategory resolved = category;
        if (resolved == SoundCategory.RANDOM) {
            resolved = ROLLABLE[random.nextInt(ROLLABLE.length)];
        }
        CategorySpec spec = SPECS.get(resolved);

        float pitch = between(random, spec.pitch().frequency());
        float slide = between(random, spec.pitch().slide());
        float slideTime = between(random, spec.pitch().slideTime());
        float attack = between(random, spec.envelope().attack());
        float sustain = between(random, spec.envelope().sustain());
        float punch = between(random, spec.envelope().punch());
        float decay = between(random, spec.envelope().decay());
        float cutoff = between(random, spec.cutoff());
        String waveform = spec.waveforms().get(random.nextInt(spec.waveforms().size()));

        PatchDocument document = new PatchDocument();

        TriggerNode trigger = new TriggerNode();
        EnvelopeNode envelope = new EnvelopeNode();
        RampNode ramp = new RampNode();
        PhasorNode phasor = new PhasorNode("Phasor", false, PhasorNode.TWO_PI, false);
        OscillatorNode oscillator = new OscillatorNode(oscillatorFactory.create(waveform));
        BiquadFilterNode filter = new BiquadFilterNode(BiquadFilterNode.FilterType.LOWPASS);
        AmplifierNode amplifier = new AmplifierNode();
        AudioOutNode audioOut = new AudioOutNode();

        ConstantNode pitchNode = new ConstantNode(pitch);
        ConstantNode slideNode = new ConstantNode(pitch * slide);
        ConstantNode slideTimeNode = new ConstantNode(slideTime);
        ConstantNode attackNode = new ConstantNode(attack);
        ConstantNode sustainNode = new ConstantNode(sustain);
        ConstantNode punchNode = new ConstantNode(punch);
        ConstantNode decayNode = new ConstantNode(decay);
        ConstantNode cutoffNode = new ConstantNode(cutoff);

        document.addNode(trigger, 40, 40);
        document.addNode(attackNode, 40, 160);
        document.addNode(sustainNode, 40, 250);
        document.addNode(punchNode, 40, 340);
        document.addNode(decayNode, 40, 430);
        document.addNode(pitchNode, 40, 540);
        document.addNode(slideNode, 40, 630);
        document.addNode(slideTimeNode, 40, 720);
        document.addNode(cutoffNode, 40, 830);
        document.addNode(envelope, 280, 120);
        document.addNode(ramp, 280, 560);
        document.addNode(phasor, 500, 560);
        document.addNode(oscillator, 700, 560);
        document.addNode(filter, 900, 560);
        document.addNode(amplifier, 1100, 300);
        document.addNode(audioOut, 1320, 300);

        document.addConnection(new Connection(trigger, "out", envelope, "trigger"));
        document.addConnection(new Connection(attackNode, "out", envelope, "attack"));
        document.addConnection(new Connection(sustainNode, "out", envelope, "sustain"));
        document.addConnection(new Connection(punchNode, "out", envelope, "punch"));
        document.addConnection(new Connection(decayNode, "out", envelope, "decay"));

        document.addConnection(new Connection(trigger, "out", ramp, "trigger"));
        document.addConnection(new Connection(pitchNode, "out", ramp, "from"));
        document.addConnection(new Connection(slideNode, "out", ramp, "to"));
        document.addConnection(new Connection(slideTimeNode, "out", ramp, "time"));

        document.addConnection(new Connection(ramp, "out", phasor, "frequency"));
        if (!(oscillator.getOscillator() instanceof NoiseOscillator)) {
            document.addConnection(new Connection(phasor, "out", oscillator, "phase"));
        }
        document.addConnection(new Connection(oscillator, "out", filter, "in"));
        document.addConnection(new Connection(cutoffNode, "out", filter, "cutoff"));
        document.addConnection(new Connection(filter, "out", amplifier, "in"));
        document.addConnection(new Connection(envelope, "out", amplifier, "gain"));
        document.addConnection(new Connection(amplifier, "out", audioOut, "in"));

        document.addMacro(new Macro(MACRO_PITCH, pitchNode, MIN_FREQUENCY, MAX_FREQUENCY));
        document.addMacro(new Macro(MACRO_SLIDE, slideNode, MIN_FREQUENCY, MAX_FREQUENCY));
        document.addMacro(new Macro(MACRO_SLIDE_TIME, slideTimeNode, 0f, MAX_STAGE_SECONDS));
        document.addMacro(new Macro(MACRO_ATTACK, attackNode, 0f, MAX_STAGE_SECONDS));
        document.addMacro(new Macro(MACRO_SUSTAIN, sustainNode, 0f, MAX_STAGE_SECONDS));
        document.addMacro(new Macro(MACRO_PUNCH, punchNode, 0f, 1f));
        document.addMacro(new Macro(MACRO_DECAY, decayNode, 0f, MAX_STAGE_SECONDS));
        document.addMacro(new Macro(MACRO_BRIGHT, cutoffNode, 100f, 16000f));

        document.setOrigin(resolved.label(), seed);
        return document;
    }

    public void mutate(PatchDocument document, long seed, float amount) {
        Random random = new Random(seed);
        for (Macro macro : document.getMacros()) {
            float span = macro.max() - macro.min();
            float delta = (float) (random.nextDouble() * 2.0 - 1.0) * amount * span;
            float mutated = macro.target().getValue() + delta;
            macro.target().setValue(Math.max(macro.min(), Math.min(macro.max(), mutated)));
        }
    }

    public double suggestedDuration(PatchDocument document) {
        double longest = 0;
        for (Macro macro : document.getMacros()) {
            switch (macro.name()) {
                case MACRO_ATTACK, MACRO_SUSTAIN, MACRO_DECAY -> longest += macro.target().getValue();
                default -> {
                }
            }
        }
        return Math.max(0.25, longest + 0.1);
    }

    public static String encodeSeed(long seed) {
        return Long.toUnsignedString(seed, 36).toUpperCase();
    }

    public static long decodeSeed(String code) {
        try {
            return Long.parseUnsignedLong(code.trim().toLowerCase(), 36);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'" + code + "' is not a valid sound code");
        }
    }

    private static float between(Random random, Range range) {
        return range.min() + (float) random.nextDouble() * (range.max() - range.min());
    }

    private static Map<SoundCategory, CategorySpec> buildSpecs() {
        Map<SoundCategory, CategorySpec> specs = new EnumMap<>(SoundCategory.class);
        specs.put(SoundCategory.PICKUP, new CategorySpec(
                List.of("Square", "Sine"),
                new Pitch(new Range(600f, 1200f), new Range(1.3f, 2.0f), new Range(0.02f, 0.06f)),
                new Envelope(new Range(0f, 0.01f), new Range(0.02f, 0.06f), new Range(0.2f, 0.5f), new Range(0.1f, 0.25f)),
                new Range(8000f, 16000f)));
        specs.put(SoundCategory.LASER, new CategorySpec(
                List.of("Sawtooth", "Square"),
                new Pitch(new Range(700f, 1800f), new Range(0.15f, 0.4f), new Range(0.05f, 0.2f)),
                new Envelope(new Range(0f, 0.005f), new Range(0.01f, 0.04f), new Range(0.1f, 0.4f), new Range(0.1f, 0.3f)),
                new Range(4000f, 12000f)));
        specs.put(SoundCategory.EXPLOSION, new CategorySpec(
                List.of("White Noise", "Brown Noise", "Pink Noise"),
                new Pitch(new Range(100f, 400f), new Range(0.3f, 0.8f), new Range(0.2f, 0.5f)),
                new Envelope(new Range(0f, 0.01f), new Range(0.05f, 0.2f), new Range(0.4f, 0.8f), new Range(0.3f, 0.8f)),
                new Range(500f, 3000f)));
        specs.put(SoundCategory.POWERUP, new CategorySpec(
                List.of("Sine", "Triangle", "Square"),
                new Pitch(new Range(300f, 700f), new Range(1.8f, 3.5f), new Range(0.15f, 0.4f)),
                new Envelope(new Range(0.01f, 0.05f), new Range(0.05f, 0.15f), new Range(0.1f, 0.3f), new Range(0.2f, 0.5f)),
                new Range(6000f, 14000f)));
        specs.put(SoundCategory.HIT, new CategorySpec(
                List.of("White Noise", "Square"),
                new Pitch(new Range(150f, 500f), new Range(0.3f, 0.7f), new Range(0.02f, 0.08f)),
                new Envelope(new Range(0f, 0.005f), new Range(0.01f, 0.03f), new Range(0.3f, 0.7f), new Range(0.05f, 0.2f)),
                new Range(1500f, 6000f)));
        specs.put(SoundCategory.JUMP, new CategorySpec(
                List.of("Square", "Triangle"),
                new Pitch(new Range(250f, 600f), new Range(1.4f, 2.5f), new Range(0.05f, 0.15f)),
                new Envelope(new Range(0f, 0.01f), new Range(0.02f, 0.06f), new Range(0.1f, 0.4f), new Range(0.1f, 0.25f)),
                new Range(5000f, 12000f)));
        specs.put(SoundCategory.BLIP, new CategorySpec(
                List.of("Square", "Sine"),
                new Pitch(new Range(500f, 1400f), new Range(0.9f, 1.15f), new Range(0.01f, 0.03f)),
                new Envelope(new Range(0f, 0.003f), new Range(0.01f, 0.03f), new Range(0f, 0.2f), new Range(0.03f, 0.1f)),
                new Range(8000f, 16000f)));
        return specs;
    }
}

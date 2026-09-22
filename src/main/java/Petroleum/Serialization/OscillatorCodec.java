package Petroleum.Serialization;

import Petroleum.Oscillators.IOscillator;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Oscillators.Implementations.DrawnWaveform;
import Petroleum.Oscillators.Implementations.DualOscillator;
import Petroleum.Oscillators.Implementations.ExpressionOscillator;
import Petroleum.Oscillators.Implementations.Wavefolder;
import Petroleum.Oscillators.Implementations.Wavetable;

import java.util.Map;

public final class OscillatorCodec {
    private static final String KIND = "kind";
    private static final String KIND_FACTORY = "factory";
    private static final String KIND_WAVETABLE = "wavetable";
    private static final String KIND_DRAWN = "drawn";
    private static final String KIND_EXPRESSION = "expression";
    private static final String KIND_DUAL = "dual";
    private static final String KIND_WAVEFOLDER = "wavefolder";

    private final OscillatorFactory oscillatorFactory;

    public OscillatorCodec(OscillatorFactory oscillatorFactory) {
        this.oscillatorFactory = oscillatorFactory;
    }

    public Map<String, Object> save(IOscillator oscillator, PatchArchive samples) {
        Map<String, Object> json = Json.object();
        if (oscillator instanceof Wavetable wavetable) {
            json.put(KIND, KIND_WAVETABLE);
            json.put("name", wavetable.getName());
            json.put("sample", samples.sampleId(wavetable.getTable()));
        } else if (oscillator instanceof DrawnWaveform drawnWaveform) {
            json.put(KIND, KIND_DRAWN);
            json.put("name", drawnWaveform.getName());
            json.put("sample", samples.sampleId(drawnWaveform.getTable()));
        } else if (oscillator instanceof ExpressionOscillator expression) {
            json.put(KIND, KIND_EXPRESSION);
            json.put("source", expression.getSource());
        } else if (oscillator instanceof DualOscillator dual) {
            json.put(KIND, KIND_DUAL);
            json.put("base", save(dual.getBase(), samples));
        } else if (oscillator instanceof Wavefolder wavefolder) {
            json.put(KIND, KIND_WAVEFOLDER);
            json.put("base", save(wavefolder.getBase(), samples));
        } else {
            json.put(KIND, KIND_FACTORY);
            json.put("name", oscillator.getName());
        }
        return json;
    }

    public IOscillator load(Map<String, Object> json, PatchArchive samples) {
        String kind = Json.string(json, KIND);
        return switch (kind) {
            case KIND_WAVETABLE -> new Wavetable(
                    samples.sample(Json.string(json, "sample")),
                    Json.stringOrDefault(json, "name", "Wavetable"));
            case KIND_DRAWN -> new DrawnWaveform(
                    samples.sample(Json.string(json, "sample")),
                    Json.stringOrDefault(json, "name", "Drawn Waveform"));
            case KIND_EXPRESSION -> new ExpressionOscillator(Json.string(json, "source"));
            case KIND_DUAL -> new DualOscillator(
                    load(Json.asObject(json.get("base")), samples), DualOscillator.DEFAULT_OFFSET);
            case KIND_WAVEFOLDER -> new Wavefolder(
                    load(Json.asObject(json.get("base")), samples), Wavefolder.DEFAULT_GAIN);
            case KIND_FACTORY -> createFromFactory(Json.string(json, "name"));
            default -> throw new SerializationException("Unknown oscillator kind '" + kind + "'");
        };
    }

    private IOscillator createFromFactory(String name) {
        try {
            return oscillatorFactory.create(name);
        } catch (IllegalArgumentException ex) {
            throw new SerializationException("Unknown oscillator '" + name + "'", ex);
        }
    }

}

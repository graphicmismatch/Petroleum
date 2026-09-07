package JSynth.Oscillators;

import JSynth.Oscillators.Implementations.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class OscillatorFactory {

    private final Map<String, Supplier<IOscillator>> registry = new LinkedHashMap<>();

    public OscillatorFactory() {
        register("Sine", Sine::new);
        register("Cosine", Cosine::new);
        register("Sawtooth", Sawtooth::new);
        register("Square", Square::new);
        register("Triangle", Triangle::new);

        register("White Noise", WhiteNoise::new);
        register("Pink Noise", PinkNoise::new);
        register("Brown Noise", BrownNoise::new);
        register("Blue Noise", BlueNoise::new);
        register("Violet Noise", VioletNoise::new);
        register("Grey Noise", GreyNoise::new);

        register("Sample & Hold", SampleAndHold::new);
        register("Chaotic", ChaoticOscillator::new);
        register("SuperSaw", SuperSaw::new);
        register("FM", FMOscillator::new);
    }

    public void register(String name, Supplier<IOscillator> supplier) {
        registry.put(name, supplier);
    }

    public IOscillator create(String name) {
        Supplier<IOscillator> supplier = registry.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown oscillator: " + name);
        }
        return supplier.get();
    }

    public List<String> names() {
        return List.copyOf(registry.keySet());
    }
}

package JSynth.Oscillators;

import JSynth.Graph.Port;

import java.util.List;

public interface ModulatableOscillator extends IOscillator {
    List<Port> getParameterPorts();

    void setParameter(int index, float value);
}

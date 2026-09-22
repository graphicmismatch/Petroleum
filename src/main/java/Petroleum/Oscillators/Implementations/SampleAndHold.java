package Petroleum.Oscillators.Implementations;

import Petroleum.Graph.Port;
import Petroleum.Oscillators.ModulatableOscillator;

import java.util.List;
import java.util.Random;

public class SampleAndHold implements ModulatableOscillator {
    private final Random random = new Random();
    private volatile int stepsPerCycle;
    private int lastStep = -1;
    private double lastT = Double.NaN;
    private float currentValue = 0f;

    public SampleAndHold() {
        this(1);
    }

    public SampleAndHold(int stepsPerCycle) {
        this.stepsPerCycle = Math.max(1, stepsPerCycle);
    }

    public void setStepsPerCycle(int stepsPerCycle) {
        this.stepsPerCycle = Math.max(1, stepsPerCycle);
    }

    @Override
    public List<Port> getParameterPorts() {
        return List.of(new Port("steps", 1f));
    }

    @Override
    public void setParameter(int index, float value) {
        if (index == 0) {
            setStepsPerCycle(Math.round(value));
        }
    }

    @Override
    public float getSample(double phase, double dt) {
        double t = phase / (2 * Math.PI);
        int step = (int) (t * stepsPerCycle);
        if (Double.isNaN(lastT) || t < lastT || step != lastStep) {
            currentValue = (float) (random.nextDouble() * 2.0 - 1.0);
            lastStep = step;
        }
        lastT = t;
        return currentValue;
    }

    @Override
    public String getName() {
        return "Sample & Hold";
    }
}

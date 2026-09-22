package Petroleum.Oscillators;

public interface WrapperOscillator extends ModulatableOscillator {
    IOscillator getBase();

    void setBase(IOscillator base);
}

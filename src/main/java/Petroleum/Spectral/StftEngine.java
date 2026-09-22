package Petroleum.Spectral;

import Petroleum.Dsp.Fft;

public class StftEngine {
    private final int windowSize;
    private final int hopSize;
    private final int latencySamples;
    private final Fft fft;

    private final float[] inputAccum;
    private int inputWriteIndex = 0;
    private int samplesSinceHop = 0;

    private final int outputAccumSize;
    private final float[] outputAccum;
    private int outputWriteIndex = 0;
    private int outputReadIndex;
    private long poppedCount = 0;

    private final float[] hannWindow;
    private final float[] normSumWindow;
    private final float[] analysisBuffer;
    private final float[] fftRe;
    private final float[] fftIm;
    private final float[] magnitude;
    private final float[] phase;

    public StftEngine(int windowSize, int hopSize) {
        if (hopSize <= 0 || hopSize > windowSize || windowSize % hopSize != 0) {
            throw new IllegalArgumentException(
                    "hopSize must evenly divide windowSize (got hopSize=" + hopSize
                            + ", windowSize=" + windowSize + ")");
        }

        this.windowSize = windowSize;
        this.hopSize = hopSize;
        this.latencySamples = windowSize;
        this.fft = new Fft(windowSize);

        inputAccum = new float[windowSize];

        outputAccumSize = windowSize * 2;
        outputAccum = new float[outputAccumSize];
        outputReadIndex = outputAccumSize - latencySamples;

        hannWindow = new float[windowSize];
        for (int i = 0; i < windowSize; i++) {
            hannWindow[i] = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * i / (windowSize - 1));
        }

        analysisBuffer = new float[windowSize];
        fftRe = new float[windowSize];
        fftIm = new float[windowSize];
        magnitude = new float[windowSize];
        phase = new float[windowSize];

        normSumWindow = computeNormSumWindow();
    }

    private float[] computeNormSumWindow() {
        int hopsPerWindow = windowSize / hopSize;
        float[] normSum = new float[windowSize];
        for (int p = 0; p < windowSize; p++) {
            double sum = 0;
            for (int h = 0; h < hopsPerWindow; h++) {
                int idx = p - h * hopSize;
                idx %= windowSize;
                if (idx < 0) {
                    idx += windowSize;
                }
                double w = hannWindow[idx];
                sum += w * w;
            }
            normSum[p] = sum > 0 ? (float) sum : 1f;
        }
        return normSum;
    }

    public void pushSample(float inputSample) {
        inputAccum[inputWriteIndex] = inputSample;
        inputWriteIndex++;
        if (inputWriteIndex >= windowSize) {
            inputWriteIndex = 0;
        }

        outputWriteIndex++;
        if (outputWriteIndex >= outputAccumSize) {
            outputWriteIndex = 0;
        }

        samplesSinceHop++;
        if (samplesSinceHop >= hopSize) {
            samplesSinceHop = 0;
            processHop();
        }
    }

    private void processHop() {
        int idx = inputWriteIndex;
        for (int i = 0; i < windowSize; i++) {
            analysisBuffer[i] = inputAccum[idx] * hannWindow[i];
            idx++;
            if (idx >= windowSize) {
                idx = 0;
            }
        }

        for (int i = 0; i < windowSize; i++) {
            fftRe[i] = analysisBuffer[i];
            fftIm[i] = 0f;
        }

        fft.forward(fftRe, fftIm);

        for (int i = 0; i < windowSize; i++) {
            magnitude[i] = (float) Math.sqrt(fftRe[i] * fftRe[i] + fftIm[i] * fftIm[i]);
            phase[i] = (float) Math.atan2(fftIm[i], fftRe[i]);
        }

        processFrame(magnitude, phase);

        for (int i = 0; i < windowSize; i++) {
            fftRe[i] = magnitude[i] * (float) Math.cos(phase[i]);
            fftIm[i] = magnitude[i] * (float) Math.sin(phase[i]);
        }

        fft.inverse(fftRe, fftIm);

        int outIdx = outputWriteIndex - windowSize;
        if (outIdx < 0) {
            outIdx += outputAccumSize;
        }
        for (int i = 0; i < windowSize; i++) {
            outputAccum[outIdx] += fftRe[i] * hannWindow[i];
            outIdx++;
            if (outIdx >= outputAccumSize) {
                outIdx = 0;
            }
        }
    }

    protected void processFrame(float[] magnitude, float[] phase) {
    }

    public float popSample() {
        float result;
        if (poppedCount < latencySamples) {
            result = 0f;
        } else {
            int normIdx = outputReadIndex;
            if (normIdx >= windowSize) {
                normIdx -= windowSize;
            }
            result = outputAccum[outputReadIndex] / normSumWindow[normIdx];
            outputAccum[outputReadIndex] = 0f;
        }

        outputReadIndex++;
        if (outputReadIndex >= outputAccumSize) {
            outputReadIndex = 0;
        }
        poppedCount++;
        return result;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getHopSize() {
        return hopSize;
    }

    public int getLatencySamples() {
        return latencySamples;
    }
}

package JSynth.Sources;

import JSynth.Audio.AudioInputDevice;
import JSynth.Dsp.LiveGrainRecorder;
import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.List;

public class MicInputNode extends AbstractNode {
    public static final int OUT = 0;

    private static final float CAPTURE_SAMPLE_RATE = 44100f;
    private static final double MAX_RECORD_SECONDS = 2.0;
    private static final double LATENCY_SECONDS = 0.05;

    private final LiveGrainRecorder recorder = new LiveGrainRecorder(MAX_RECORD_SECONDS);

    private volatile String deviceName;
    private TargetDataLine line;
    private Thread captureThread;
    private double readCursor = -1;

    public MicInputNode() {
        super(List.of(), List.of("out"));
    }

    public static List<String> listAvailableDeviceNames() {
        return AudioInputDevice.listAvailableDeviceNames();
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void connect(String name) throws LineUnavailableException {
        disconnect();
        AudioFormat format = new AudioFormat(CAPTURE_SAMPLE_RATE, 16, 1, true, false);
        TargetDataLine opened = AudioInputDevice.open(name, format);
        opened.start();
        this.line = opened;
        this.deviceName = name;
        this.captureThread = new Thread(() -> runCaptureLoop(opened), "MicInput-Capture");
        this.captureThread.setDaemon(true);
        this.captureThread.start();
    }

    public void disconnect() {
        TargetDataLine closing = line;
        line = null;
        deviceName = null;
        if (closing != null) {
            closing.stop();
            closing.close();
        }
        captureThread = null;
    }

    private void runCaptureLoop(TargetDataLine capture) {
        recorder.ensureAllocated(CAPTURE_SAMPLE_RATE);
        int chunkBytes = capture.getBufferSize() / 4;
        chunkBytes -= chunkBytes % 2;
        byte[] bytes = new byte[Math.max(2, chunkBytes)];
        while (line == capture) {
            int read = capture.read(bytes, 0, bytes.length);
            for (int i = 0; i + 1 < read; i += 2) {
                short sample = (short) ((bytes[i] & 0xFF) | (bytes[i + 1] << 8));
                recorder.write(sample / 32768f);
            }
        }
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        recorder.ensureAllocated(context.sampleRate);
        double written = recorder.totalWritten();
        double target = recorder.positionBehind(LATENCY_SECONDS, context.sampleRate);
        if (readCursor < 0 || readCursor > written || readCursor < written - recorder.length() + 1) {
            readCursor = target;
        }
        for (int s = 0; s < frameCount; s++) {
            outputs[OUT][s] = recorder.read(readCursor);
            readCursor += 1;
        }
    }

    @Override
    public String getName() {
        return "Mic Input";
    }
}

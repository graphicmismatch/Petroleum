package Petroleum.Audio;

import Petroleum.Graph.Node;
import Petroleum.Graph.NodeContext;
import Petroleum.Patch.Patch;
import Petroleum.Sinks.VirtualLineOut;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

public final class AudioEngine {
    private static final float SAMPLE_RATE = 44100f;
    private static final int CHANNELS = 2;
    private static final int DEFAULT_BITS_PER_SAMPLE = 16;
    private static final int BLOCK_SIZE = Patch.DEFAULT_BLOCK_CAPACITY;
    private static final int DEFAULT_BUFFER_BLOCKS = 2;

    private volatile Patch currentPatch;
    private volatile boolean running = false;
    private volatile boolean resetRequested = false;
    private volatile String outputDeviceName;
    private volatile int bitsPerSample = DEFAULT_BITS_PER_SAMPLE;
    private volatile int activeBitsPerSample = DEFAULT_BITS_PER_SAMPLE;
    private volatile int bufferBlocks = DEFAULT_BUFFER_BLOCKS;
    private Thread playbackThread;
    private SourceDataLine line;

    public void setOutputDevice(String mixerName) {
        this.outputDeviceName = mixerName;
    }

    public String getOutputDevice() {
        return outputDeviceName;
    }

    public void setPlaybackBitDepth(int bits) {
        if (bits != 8 && bits != 16 && bits != 24) {
            throw new IllegalArgumentException("Playback bit depth must be 8, 16, or 24");
        }
        this.bitsPerSample = bits;
    }

    public int getPlaybackBitDepth() {
        return bitsPerSample;
    }

    public void setBufferBlocks(int blocks) {
        if (blocks < 1 || blocks > 8) {
            throw new IllegalArgumentException("Buffer blocks must be between 1 and 8");
        }
        this.bufferBlocks = blocks;
    }

    public int getBufferBlocks() {
        return bufferBlocks;
    }

    public synchronized void restart(Patch patch) {
        if (!running) {
            play(patch);
            return;
        }
        currentPatch = patch;
        resetRequested = true;
    }

    public synchronized void play(Patch patch) {
        currentPatch = patch;
        if (running) {
            return;
        }

        int requestedBits = bitsPerSample;
        AudioFormat format = new AudioFormat(SAMPLE_RATE, requestedBits, CHANNELS, true, false);
        try {
            line = openOutputLine(format);
            line.open(format, lineBufferBytes(requestedBits));
            activeBitsPerSample = requestedBits;
        } catch (LineUnavailableException e) {
            if (requestedBits == DEFAULT_BITS_PER_SAMPLE) {
                line = null;
                running = false;
                throw new RuntimeException(e);
            }
            AudioFormat fallback = new AudioFormat(SAMPLE_RATE, DEFAULT_BITS_PER_SAMPLE, CHANNELS, true, false);
            try {
                line = openOutputLine(fallback);
                line.open(fallback, lineBufferBytes(DEFAULT_BITS_PER_SAMPLE));
                activeBitsPerSample = DEFAULT_BITS_PER_SAMPLE;
            } catch (LineUnavailableException fallbackFailure) {
                line = null;
                running = false;
                throw new RuntimeException(fallbackFailure);
            }
        }
        line.start();

        running = true;
        resetRequested = false;
        SourceDataLine playbackLine = line;
        int playbackBits = activeBitsPerSample;
        playbackThread = new Thread(() -> runPlaybackLoop(playbackLine, playbackBits), "AudioEngine-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private SourceDataLine openOutputLine(AudioFormat format) throws LineUnavailableException {
        String name = outputDeviceName;
        Mixer.Info info = name == null ? null : findMixerInfo(name);
        return info == null ? AudioSystem.getSourceDataLine(format) : AudioSystem.getSourceDataLine(format, info);
    }

    private int lineBufferBytes(int bits) {
        return BLOCK_SIZE * bufferBlocks * CHANNELS * (bits / 8);
    }

    private void runPlaybackLoop(SourceDataLine playbackLine, int bits) {
        NodeContext context = new NodeContext(SAMPLE_RATE);
        float[] monoBuffer = new float[BLOCK_SIZE];
        float[] leftBuffer = new float[BLOCK_SIZE];
        float[] rightBuffer = new float[BLOCK_SIZE];
        byte[] byteBuffer = new byte[BLOCK_SIZE * CHANNELS * (bits / 8)];
        ExportSettings duplicateSettings = new ExportSettings(SAMPLE_RATE, bits, 2, false, false);
        ExportSettings channelSettings = new ExportSettings(SAMPLE_RATE, bits, 1, false, false);
        Map<Node, VirtualLine> virtualLines = new HashMap<>();

        while (running) {
            if (resetRequested) {
                resetRequested = false;
                context = new NodeContext(SAMPLE_RATE);
                playbackLine.flush();
            }
            Patch patch = currentPatch;
            if (patch == null) {
                LockSupport.parkNanos(1_000_000L);
                continue;
            }
            int byteCount;
            if (patch.isStereo()) {
                patch.renderBlockStereo(leftBuffer, rightBuffer, BLOCK_SIZE, context);
                byteCount = writeStereoPcm(leftBuffer, rightBuffer, BLOCK_SIZE, byteBuffer, channelSettings);
            } else {
                patch.renderBlock(monoBuffer, BLOCK_SIZE, context);
                byteCount = writeStereoPcm(monoBuffer, BLOCK_SIZE, byteBuffer, duplicateSettings);
            }
            playbackLine.write(byteBuffer, 0, byteCount);
            writeVirtualLines(patch, virtualLines, leftBuffer, rightBuffer);
        }
        for (VirtualLine virtualLine : virtualLines.values()) {
            virtualLine.close();
        }
    }

    private void writeVirtualLines(Patch patch, Map<Node, VirtualLine> virtualLines,
            float[] scratchLeft, float[] scratchRight) {
        Set<Node> present = new HashSet<>(patch.virtualLineOutNodes());
        virtualLines.keySet().removeIf(node -> {
            if (!present.contains(node)) {
                virtualLines.get(node).close();
                return true;
            }
            return false;
        });
        for (Node node : patch.virtualLineOutNodes()) {
            try {
                writeVirtualLine(patch, virtualLines, node, scratchLeft, scratchRight);
            } catch (RuntimeException e) {
                VirtualLine broken = virtualLines.remove(node);
                if (broken != null) {
                    broken.close();
                }
            }
        }
    }

    private void writeVirtualLine(Patch patch, Map<Node, VirtualLine> virtualLines, Node node,
            float[] scratchLeft, float[] scratchRight) {
        VirtualLineOut sink = (VirtualLineOut) node;
        VirtualLine virtualLine = virtualLines.get(node);
        String wanted = sink.getMixerName();
        if (virtualLine == null || !Objects.equals(virtualLine.mixerName, wanted)) {
            if (virtualLine != null) {
                virtualLine.close();
            }
            virtualLine = openVirtualLine(wanted, sink.isStereo());
            virtualLines.put(node, virtualLine);
        }
        if (virtualLine.line == null) {
            return;
        }
        int count = patch.copyInputBlock(node, 0, scratchLeft);
        int byteCount;
        if (sink.isStereo()) {
            patch.copyInputBlock(node, 1, scratchRight);
            byteCount = writeStereoPcm(scratchLeft, scratchRight, count, virtualLine.pcm, VIRTUAL_LINE_CHANNEL_SETTINGS);
        } else {
            byteCount = writeMonoPcm(scratchLeft, count, virtualLine.pcm, VIRTUAL_LINE_CHANNEL_SETTINGS);
        }
        if (virtualLine.line.available() >= byteCount) {
            virtualLine.line.write(virtualLine.pcm, 0, byteCount);
        }
    }

    private static VirtualLine openVirtualLine(String mixerName, boolean stereo) {
        if (mixerName == null) {
            return new VirtualLine(null, null, stereo);
        }
        Mixer.Info info = findMixerInfo(mixerName);
        if (info == null) {
            return new VirtualLine(null, mixerName, stereo);
        }
        int channels = stereo ? 2 : 1;
        AudioFormat format = new AudioFormat(SAMPLE_RATE, DEFAULT_BITS_PER_SAMPLE, channels, true, false);
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(format, info);
            line.open(format, BLOCK_SIZE * 2 * channels * (DEFAULT_BITS_PER_SAMPLE / 8));
            line.start();
            return new VirtualLine(line, mixerName, stereo);
        } catch (LineUnavailableException e) {
            return new VirtualLine(null, mixerName, stereo);
        }
    }

    private static Mixer.Info findMixerInfo(String name) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name)) {
                return info;
            }
        }
        return null;
    }

    private static final class VirtualLine {
        final SourceDataLine line;
        final String mixerName;
        final byte[] pcm;

        VirtualLine(SourceDataLine line, String mixerName, boolean stereo) {
            this.line = line;
            this.mixerName = mixerName;
            int channels = stereo ? 2 : 1;
            this.pcm = new byte[BLOCK_SIZE * channels * (DEFAULT_BITS_PER_SAMPLE / 8)];
        }

        void close() {
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            if (playbackThread != null) {
                playbackThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
            line = null;
        }
        playbackThread = null;
        currentPatch = null;
    }

    public void renderToWav(Patch patch, double durationSeconds, File file) throws IOException {
        renderToWav(patch, durationSeconds, file, ExportSettings.defaults());
    }

    public void renderToWav(Patch patch, double durationSeconds, File file, ExportSettings settings)
            throws IOException {
        if (patch.isStereo()) {
            renderToWavStereo(patch, durationSeconds, file, settings);
            return;
        }
        if (settings.needsBuffering()) {
            renderBuffered(patch, durationSeconds, file, settings);
            return;
        }
        long totalFrames = Math.round(durationSeconds * settings.sampleRate());
        NodeContext context = new NodeContext(settings.sampleRate());

        try (AudioInputStream audioInputStream = new AudioInputStream(
                new RenderingStream(patch, context, totalFrames, settings),
                formatFor(settings), totalFrames)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, file);
        }
    }

    private void renderBuffered(Patch patch, double durationSeconds, File file, ExportSettings settings)
            throws IOException {
        float[] mono = renderMono(patch, durationSeconds, settings.sampleRate());

        int start = 0;
        int end = mono.length;
        if (settings.trimSilence()) {
            while (start < end && Math.abs(mono[start]) < ExportSettings.TRIM_THRESHOLD) {
                start++;
            }
            while (end > start && Math.abs(mono[end - 1]) < ExportSettings.TRIM_THRESHOLD) {
                end--;
            }
            if (start == end) {
                start = 0;
                end = Math.min(mono.length, 1);
            }
        }

        float scale = 1f;
        if (settings.normalize()) {
            float peak = 0f;
            for (int i = start; i < end; i++) {
                peak = Math.max(peak, Math.abs(mono[i]));
            }
            if (peak > 0f) {
                scale = ExportSettings.NORMALIZE_TARGET / peak;
            }
        }

        int frames = end - start;
        byte[] pcm = new byte[frames * settings.bytesPerFrame()];
        int offset = 0;
        for (int i = start; i < end; i++) {
            offset = encodeFrame(mono[i] * scale, settings, pcm, offset);
        }

        try (AudioInputStream stream = new AudioInputStream(
                new java.io.ByteArrayInputStream(pcm), formatFor(settings), frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }
    }

    public static float[] renderMono(Patch patch, double durationSeconds, float sampleRate) {
        int totalFrames = (int) Math.round(durationSeconds * sampleRate);
        NodeContext context = new NodeContext(sampleRate);
        float[] block = new float[BLOCK_SIZE];
        float[] all = new float[totalFrames];
        int written = 0;
        while (written < totalFrames) {
            int chunk = Math.min(BLOCK_SIZE, totalFrames - written);
            patch.renderBlock(block, chunk, context);
            System.arraycopy(block, 0, all, written, chunk);
            written += chunk;
        }
        return all;
    }

    public static float[][] renderStereo(Patch patch, double durationSeconds, float sampleRate) {
        int totalFrames = (int) Math.round(durationSeconds * sampleRate);
        NodeContext context = new NodeContext(sampleRate);
        float[] leftBlock = new float[BLOCK_SIZE];
        float[] rightBlock = new float[BLOCK_SIZE];
        float[] left = new float[totalFrames];
        float[] right = new float[totalFrames];
        int written = 0;
        while (written < totalFrames) {
            int chunk = Math.min(BLOCK_SIZE, totalFrames - written);
            patch.renderBlockStereo(leftBlock, rightBlock, chunk, context);
            System.arraycopy(leftBlock, 0, left, written, chunk);
            System.arraycopy(rightBlock, 0, right, written, chunk);
            written += chunk;
        }
        return new float[][] {left, right};
    }

    private void renderToWavStereo(Patch patch, double durationSeconds, File file, ExportSettings settings)
            throws IOException {
        float[][] stereo = renderStereo(patch, durationSeconds, settings.sampleRate());
        float[] left = stereo[0];
        float[] right = stereo[1];

        int start = 0;
        int end = left.length;
        if (settings.trimSilence()) {
            while (start < end && Math.abs(left[start]) < ExportSettings.TRIM_THRESHOLD
                    && Math.abs(right[start]) < ExportSettings.TRIM_THRESHOLD) {
                start++;
            }
            while (end > start && Math.abs(left[end - 1]) < ExportSettings.TRIM_THRESHOLD
                    && Math.abs(right[end - 1]) < ExportSettings.TRIM_THRESHOLD) {
                end--;
            }
            if (start == end) {
                start = 0;
                end = Math.min(left.length, 1);
            }
        }

        float scale = 1f;
        if (settings.normalize()) {
            float peak = 0f;
            for (int i = start; i < end; i++) {
                peak = Math.max(peak, Math.max(Math.abs(left[i]), Math.abs(right[i])));
            }
            if (peak > 0f) {
                scale = ExportSettings.NORMALIZE_TARGET / peak;
            }
        }

        ExportSettings stereoFileSettings = new ExportSettings(
                settings.sampleRate(), settings.bitDepth(), 2, settings.normalize(), settings.trimSilence());
        ExportSettings channelSettings = new ExportSettings(
                settings.sampleRate(), settings.bitDepth(), 1, settings.normalize(), settings.trimSilence());
        int frames = end - start;
        byte[] pcm = new byte[frames * stereoFileSettings.bytesPerFrame()];
        int offset = 0;
        for (int i = start; i < end; i++) {
            offset = encodeFrame(left[i] * scale, channelSettings, pcm, offset);
            offset = encodeFrame(right[i] * scale, channelSettings, pcm, offset);
        }

        try (AudioInputStream stream = new AudioInputStream(
                new java.io.ByteArrayInputStream(pcm), formatFor(stereoFileSettings), frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file);
        }
    }

    private static AudioFormat formatFor(ExportSettings settings) {
        int bits = settings.bitDepth();
        AudioFormat.Encoding encoding = bits == 32 ? AudioFormat.Encoding.PCM_FLOAT
                : bits == 8 ? AudioFormat.Encoding.PCM_UNSIGNED
                : AudioFormat.Encoding.PCM_SIGNED;
        int frameSize = settings.channels() * (bits / 8);
        return new AudioFormat(encoding, settings.sampleRate(), bits, settings.channels(), frameSize,
                settings.sampleRate(), false);
    }

    private static int encodeFrame(float sample, ExportSettings settings, byte[] dest, int offset) {
        int bits = settings.bitDepth();
        float value = bits == 32 ? sample : Math.max(-1f, Math.min(1f, sample));
        for (int c = 0; c < settings.channels(); c++) {
            switch (bits) {
                case 8 -> dest[offset++] = (byte) (Math.round((value + 1f) * 127.5f) & 0xFF);
                case 16 -> {
                    short pcm = (short) Math.round(value * 32767f);
                    dest[offset++] = (byte) (pcm & 0xFF);
                    dest[offset++] = (byte) ((pcm >> 8) & 0xFF);
                }
                case 24 -> {
                    int pcm = Math.round(value * 8388607f);
                    dest[offset++] = (byte) (pcm & 0xFF);
                    dest[offset++] = (byte) ((pcm >> 8) & 0xFF);
                    dest[offset++] = (byte) ((pcm >> 16) & 0xFF);
                }
                case 32 -> {
                    int bitsRepr = Float.floatToIntBits(value);
                    dest[offset++] = (byte) (bitsRepr & 0xFF);
                    dest[offset++] = (byte) ((bitsRepr >> 8) & 0xFF);
                    dest[offset++] = (byte) ((bitsRepr >> 16) & 0xFF);
                    dest[offset++] = (byte) ((bitsRepr >> 24) & 0xFF);
                }
                default -> throw new IllegalStateException("Unsupported bit depth: " + bits);
            }
        }
        return offset;
    }

    private static final class RenderingStream extends InputStream {
        private final Patch patch;
        private final NodeContext context;
        private final ExportSettings settings;
        private final float[] monoBuffer;
        private final byte[] pcmScratch;
        private long framesRemaining;
        private int leftoverOffset;
        private int leftoverLength;

        RenderingStream(Patch patch, NodeContext context, long totalFrames, ExportSettings settings) {
            this.patch = patch;
            this.context = context;
            this.settings = settings;
            this.monoBuffer = new float[BLOCK_SIZE];
            this.pcmScratch = new byte[BLOCK_SIZE * settings.bytesPerFrame()];
            this.framesRemaining = totalFrames;
            this.leftoverOffset = 0;
            this.leftoverLength = 0;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int n = read(single, 0, 1);
            if (n <= 0) {
                return -1;
            }
            return single[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (leftoverLength == 0) {
                if (framesRemaining <= 0) {
                    return -1;
                }
                int chunk = (int) Math.min(BLOCK_SIZE, framesRemaining);
                patch.renderBlock(monoBuffer, chunk, context);
                int offset = 0;
                for (int i = 0; i < chunk; i++) {
                    offset = encodeFrame(monoBuffer[i], settings, pcmScratch, offset);
                }
                leftoverLength = offset;
                leftoverOffset = 0;
                framesRemaining -= chunk;
            }
            int toCopy = Math.min(len, leftoverLength);
            System.arraycopy(pcmScratch, leftoverOffset, b, off, toCopy);
            leftoverOffset += toCopy;
            leftoverLength -= toCopy;
            return toCopy;
        }
    }

    private static final ExportSettings VIRTUAL_LINE_CHANNEL_SETTINGS =
            new ExportSettings(SAMPLE_RATE, DEFAULT_BITS_PER_SAMPLE, 1, false, false);

    private static int writeStereoPcm(float[] monoBuffer, int numSamples, byte[] byteBuffer, ExportSettings duplicateSettings) {
        int offset = 0;
        for (int i = 0; i < numSamples; i++) {
            offset = encodeFrame(monoBuffer[i], duplicateSettings, byteBuffer, offset);
        }
        return offset;
    }

    private static int writeStereoPcm(float[] left, float[] right, int numSamples, byte[] byteBuffer, ExportSettings channelSettings) {
        int offset = 0;
        for (int i = 0; i < numSamples; i++) {
            offset = encodeFrame(left[i], channelSettings, byteBuffer, offset);
            offset = encodeFrame(right[i], channelSettings, byteBuffer, offset);
        }
        return offset;
    }

    private static int writeMonoPcm(float[] mono, int numSamples, byte[] byteBuffer, ExportSettings channelSettings) {
        int offset = 0;
        for (int i = 0; i < numSamples; i++) {
            offset = encodeFrame(mono[i], channelSettings, byteBuffer, offset);
        }
        return offset;
    }
}

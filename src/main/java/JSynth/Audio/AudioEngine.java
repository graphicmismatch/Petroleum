package JSynth.Audio;

import JSynth.Graph.NodeContext;
import JSynth.Patch.Patch;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.LockSupport;

public final class AudioEngine {
    private static final float SAMPLE_RATE = 44100f;
    private static final int CHANNELS = 2;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int BLOCK_SIZE = Patch.DEFAULT_BLOCK_CAPACITY;
    private static final int LINE_BUFFER_BYTES = BLOCK_SIZE * 2 * CHANNELS * (BITS_PER_SAMPLE / 8);

    private volatile Patch currentPatch;
    private volatile boolean running = false;
    private volatile boolean resetRequested = false;
    private Thread playbackThread;
    private SourceDataLine line;

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

        AudioFormat format = new AudioFormat(SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS, true, false);
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, LINE_BUFFER_BYTES);
        } catch (LineUnavailableException e) {
            line = null;
            running = false;
            throw new RuntimeException(e);
        }
        line.start();

        running = true;
        resetRequested = false;
        SourceDataLine playbackLine = line;
        playbackThread = new Thread(() -> runPlaybackLoop(playbackLine), "AudioEngine-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void runPlaybackLoop(SourceDataLine playbackLine) {
        NodeContext context = new NodeContext(SAMPLE_RATE);
        float[] monoBuffer = new float[BLOCK_SIZE];
        float[] leftBuffer = new float[BLOCK_SIZE];
        float[] rightBuffer = new float[BLOCK_SIZE];
        byte[] byteBuffer = new byte[BLOCK_SIZE * CHANNELS * (BITS_PER_SAMPLE / 8)];

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
                byteCount = writeStereoPcm(leftBuffer, rightBuffer, BLOCK_SIZE, byteBuffer);
            } else {
                patch.renderBlock(monoBuffer, BLOCK_SIZE, context);
                byteCount = writeStereoPcm(monoBuffer, BLOCK_SIZE, byteBuffer);
            }
            playbackLine.write(byteBuffer, 0, byteCount);
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
        boolean signed = settings.bitDepth() == 16;
        return new AudioFormat(settings.sampleRate(), settings.bitDepth(), settings.channels(), signed, false);
    }

    private static int encodeFrame(float sample, ExportSettings settings, byte[] dest, int offset) {
        float clamped = Math.max(-1f, Math.min(1f, sample));
        if (settings.bitDepth() == 8) {
            byte value = (byte) (Math.round((clamped + 1f) * 127.5f) & 0xFF);
            for (int c = 0; c < settings.channels(); c++) {
                dest[offset++] = value;
            }
            return offset;
        }
        short pcmSample = (short) Math.round(clamped * 32767f);
        byte low = (byte) (pcmSample & 0xFF);
        byte high = (byte) ((pcmSample >> 8) & 0xFF);
        for (int c = 0; c < settings.channels(); c++) {
            dest[offset++] = low;
            dest[offset++] = high;
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

    private static final ExportSettings PLAYBACK_SETTINGS = ExportSettings.defaults();
    private static final ExportSettings PLAYBACK_CHANNEL_SETTINGS =
            new ExportSettings(SAMPLE_RATE, BITS_PER_SAMPLE, 1, false, false);

    private static int writeStereoPcm(float[] monoBuffer, int numSamples, byte[] byteBuffer) {
        int offset = 0;
        for (int i = 0; i < numSamples; i++) {
            offset = encodeFrame(monoBuffer[i], PLAYBACK_SETTINGS, byteBuffer, offset);
        }
        return offset;
    }

    private static int writeStereoPcm(float[] left, float[] right, int numSamples, byte[] byteBuffer) {
        int offset = 0;
        for (int i = 0; i < numSamples; i++) {
            offset = encodeFrame(left[i], PLAYBACK_CHANNEL_SETTINGS, byteBuffer, offset);
            offset = encodeFrame(right[i], PLAYBACK_CHANNEL_SETTINGS, byteBuffer, offset);
        }
        return offset;
    }
}

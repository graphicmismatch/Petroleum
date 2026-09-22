package Petroleum.Oscillators.Implementations;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

public final class WavetableLoader {

    private WavetableLoader() {
    }

    public static float[] loadMonoSamples(File file) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(file)) {
            AudioFormat sourceFormat = rawStream.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);

            if (!AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                throw new UnsupportedAudioFileException("Unsupported WAV format: " + sourceFormat);
            }

            try (AudioInputStream decodedStream = AudioSystem.getAudioInputStream(targetFormat, rawStream)) {
                return decode(decodedStream);
            }
        }
    }

    private static float[] decode(AudioInputStream stream) throws IOException {
        int channels = stream.getFormat().getChannels();
        byte[] bytes = stream.readAllBytes();
        int frameSize = 2 * channels;
        int frameCount = bytes.length / frameSize;
        if (frameCount == 0) {
            throw new IOException("WAV file contains no samples");
        }

        float[] samples = new float[frameCount];
        for (int frame = 0; frame < frameCount; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                int offset = frame * frameSize + channel * 2;
                short sample = (short) ((bytes[offset] & 0xFF) | (bytes[offset + 1] << 8));
                sum += sample;
            }
            samples[frame] = (sum / (float) channels) / 32768f;
        }
        return samples;
    }
}

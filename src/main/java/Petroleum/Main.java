package Petroleum;

import Petroleum.Audio.AudioEngine;
import Petroleum.Audio.ExportSettings;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.NodeFactory;
import Petroleum.Oscillators.OscillatorFactory;
import Petroleum.Patch.Patch;
import Petroleum.Serialization.PatchCodec;

import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.file.Path;

public final class Main {
    private static final double MAX_ONE_SHOT_SECONDS = 60;
    private static final String USAGE = """
            Usage:
              petroleum                      open the editor
              petroleum render <file.dsl|file.ptr> -o <out.wav> [options]

            Render options:
              -o, --output <file>   WAV file to write (required)
              -d, --duration <sec>  length in seconds; a Recording End node cuts the render
                                    short when it fires (default: until a Recording End node
                                    fires, or a one-shot patch finishes)
              --max-duration <sec>  ceiling for a render with no --duration, so a Recording End
                                    that never fires still stops (default 600)
              --rate <hz>           sample rate (default 44100)
              --bits <8|16|24|32>   bit depth (default 16)
              --channels <1|2>      channel count (default 2)
              --normalize           scale the peak to just under full scale
              --trim                cut leading and trailing silence
            """;

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("render")) {
            try {
                render(args);
            } catch (Exception ex) {
                System.err.println("petroleum: " + ex.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            System.out.print(USAGE);
            return;
        }
        Petroleum.Gui.SynthApp.main(args);
    }

    private static void render(String[] args) throws Exception {
        Path input = null;
        File output = null;
        Double duration = null;
        ExportSettings defaults = ExportSettings.defaults();
        float rate = defaults.sampleRate();
        int bits = defaults.bitDepth();
        int channels = defaults.channels();
        boolean normalize = false;
        boolean trim = false;
        double maxSeconds = AudioEngine.DEFAULT_MAX_UNBOUNDED_SECONDS;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-o", "--output" -> output = new File(value(args, ++i, arg));
                case "-d", "--duration" -> {
                    duration = Double.parseDouble(value(args, ++i, arg));
                    if (duration <= 0) {
                        throw new IllegalArgumentException("--duration must be positive");
                    }
                }
                case "--rate" -> rate = Float.parseFloat(value(args, ++i, arg));
                case "--bits" -> bits = Integer.parseInt(value(args, ++i, arg));
                case "--channels" -> channels = Integer.parseInt(value(args, ++i, arg));
                case "--max-duration" -> {
                    maxSeconds = Double.parseDouble(value(args, ++i, arg));
                    if (maxSeconds <= 0) {
                        throw new IllegalArgumentException("--max-duration must be positive");
                    }
                }
                case "--normalize" -> normalize = true;
                case "--trim" -> trim = true;
                default -> {
                    if (arg.startsWith("-") || input != null) {
                        throw new IllegalArgumentException("Unexpected argument '" + arg + "'\n\n" + USAGE);
                    }
                    input = Path.of(arg);
                }
            }
        }
        if (input == null || output == null) {
            throw new IllegalArgumentException("render needs an input file and -o <out.wav>\n\n" + USAGE);
        }
        ExportSettings settings = new ExportSettings(rate, bits, channels, normalize, trim);

        PatchCodec codec = new PatchCodec(new NodeFactory(), new OscillatorFactory());
        Patch patch = codec.load(input).buildPatch();
        if (duration == null) {
            if (patch.hasRecordingEnd()) {
                duration = AudioEngine.UNBOUNDED;
            } else if (!patch.hasOneShots()) {
                throw new IllegalArgumentException(input.getFileName()
                        + " plays forever (it has no one-shot envelope and no Recording End node),"
                        + " so pass --duration <seconds>");
            } else {
                duration = oneShotSeconds(patch, rate);
            }
        }
        AudioEngine engine = new AudioEngine();
        engine.setMaxUnboundedSeconds(maxSeconds);
        engine.renderToWav(patch, duration, output, settings);
        long frames = AudioSystem.getAudioFileFormat(output).getFrameLength();
        System.out.printf("Wrote %s (%.2f s)%n", output,
                frames > 0 ? frames / (double) rate : duration);
    }

    private static double oneShotSeconds(Patch patch, float rate) {
        int block = Patch.DEFAULT_BLOCK_CAPACITY;
        float[] left = new float[block];
        float[] right = new float[block];
        NodeContext context = new NodeContext(rate);
        long frames = 0;
        long limit = (long) (MAX_ONE_SHOT_SECONDS * rate);
        while (frames < limit) {
            if (patch.isStereo()) {
                patch.renderBlockStereo(left, right, block, context);
            } else {
                patch.renderBlock(left, block, context);
            }
            frames += block;
            if (patch.isFinished() || patch.recordingEndOffset() >= 0) {
                break;
            }
        }
        return frames / (double) rate;
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return args[index];
    }
}

package Petroleum.Dsp;

public final class PitchQuantizer {
    public enum Scale {
        CHROMATIC("Chromatic", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}),
        MAJOR("Major", new int[]{0, 2, 4, 5, 7, 9, 11}),
        MINOR("Minor", new int[]{0, 2, 3, 5, 7, 8, 10}),
        MAJOR_PENTATONIC("Major Pentatonic", new int[]{0, 2, 4, 7, 9}),
        MINOR_PENTATONIC("Minor Pentatonic", new int[]{0, 3, 5, 7, 10});

        private final String label;
        private final int[] intervals;

        Scale(String label, int[] intervals) {
            this.label = label;
            this.intervals = intervals;
        }

        public String label() {
            return label;
        }
    }

    private static final float MIN_HZ = 1e-6f;

    private PitchQuantizer() {
    }

    public static float quantize(Scale scale, float hz, float rootHz) {
        float pitch = hzToSemitone(Math.max(MIN_HZ, hz));
        float rootPitch = hzToSemitone(Math.max(MIN_HZ, rootHz));
        int rootClass = Math.floorMod(Math.round(rootPitch), 12);

        int baseOctave = 12 * ((int) Math.floor(pitch / 12.0) - 1);
        float best = pitch;
        float bestDistance = Float.MAX_VALUE;
        for (int octave = baseOctave; octave <= baseOctave + 24; octave += 12) {
            for (int interval : scale.intervals) {
                float candidate = octave + rootClass + interval;
                float distance = Math.abs(candidate - pitch);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }

        return semitoneToHz(best);
    }

    public static float hzToSemitone(float hz) {
        return (float) (69.0 + 12.0 * (Math.log(hz / 440.0) / Math.log(2.0)));
    }

    public static float semitoneToHz(float semitone) {
        return (float) (440.0 * Math.pow(2.0, (semitone - 69) / 12.0));
    }
}

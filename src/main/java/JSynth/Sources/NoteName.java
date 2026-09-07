package JSynth.Sources;

public final class NoteName {
    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final int[] LETTER_SEMITONES = {0, 2, 4, 5, 7, 9, 11};

    private NoteName() {
    }

    public static float parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter a note name, e.g. \"A4\" or \"C#3\"");
        }

        int i = 0;
        char letter = Character.toUpperCase(trimmed.charAt(i));
        int letterIndex = "CDEFGAB".indexOf(letter);
        if (letterIndex < 0) {
            throw new IllegalArgumentException("Unknown note letter '" + letter + "' - use A-G");
        }
        i++;

        int semitone = LETTER_SEMITONES[letterIndex];
        if (i < trimmed.length() && trimmed.charAt(i) == '#') {
            semitone++;
            i++;
        } else if (i < trimmed.length() && Character.toLowerCase(trimmed.charAt(i)) == 'b') {
            semitone--;
            i++;
        }

        int octaveStart = i;
        if (i < trimmed.length() && (trimmed.charAt(i) == '-' || trimmed.charAt(i) == '+')) {
            i++;
        }
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i == octaveStart) {
            throw new IllegalArgumentException("Missing octave number, e.g. \"A4\"");
        }
        int octave = Integer.parseInt(trimmed.substring(octaveStart, i));

        float pitch = (octave + 1) * 12 + semitone;

        String rest = trimmed.substring(i).trim();
        if (!rest.isEmpty()) {
            if (rest.endsWith("c") || rest.endsWith("C")) {
                rest = rest.substring(0, rest.length() - 1);
            }
            try {
                pitch += Float.parseFloat(rest) / 100f;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Could not read cents offset '" + rest + "'");
            }
        }

        return pitch;
    }

    /**
     * Like {@link #parse(String)}, but accepts a bare semitone number (e.g. "69.5") in addition to
     * note-name notation - for entry points that want to take either a musician's or a synthesist's
     * spelling of the same pitch.
     */
    public static float parseFlexible(String text) {
        String trimmed = text == null ? "" : text.trim();
        try {
            return Float.parseFloat(trimmed);
        } catch (NumberFormatException notANumber) {
            return parse(trimmed);
        }
    }

    public static String format(float pitch) {
        int rounded = Math.round(pitch);
        int cents = Math.round((pitch - rounded) * 100);
        String base = baseName(rounded);
        if (cents == 0) {
            return base;
        }
        return base + (cents > 0 ? "+" : "") + cents + "c";
    }

    private static String baseName(int midiNote) {
        int octave = Math.floorDiv(midiNote, 12) - 1;
        String pitchClass = NAMES[Math.floorMod(midiNote, 12)];
        return pitchClass + octave;
    }
}

package JSynth.Tracker;

import java.util.Locale;

public final class NoteEffectText {
    private NoteEffectText() {
    }

    public static TrackerPattern.NoteEffect parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return TrackerPattern.NoteEffect.NONE;
        }

        TrackerPattern.SlideMode slideMode = TrackerPattern.SlideMode.NONE;
        int slideSteps = 0;
        int arpSemitone1 = 0;
        int arpSemitone2 = 0;
        int arpRateTicks = 0;
        float delayFraction = 0f;
        int retriggerTicks = 0;
        int cutTicks = 0;

        for (String token : trimmed.split("\\s+")) {
            char code = Character.toUpperCase(token.charAt(0));
            String value = token.substring(1);
            try {
                switch (code) {
                    case 'G' -> {
                        slideMode = TrackerPattern.SlideMode.GLIDE;
                        slideSteps = clamp(Integer.parseInt(value), 0, 64, token);
                    }
                    case 'Q' -> {
                        slideMode = TrackerPattern.SlideMode.GLISSANDO;
                        slideSteps = clamp(Integer.parseInt(value), 0, 64, token);
                    }
                    case 'A' -> {
                        String[] parts = value.split(",");
                        if (parts.length < 2 || parts.length > 3) {
                            throw new IllegalArgumentException(
                                    "Arp needs 2 or 3 comma-separated values, e.g. \"A3,7\": " + token);
                        }
                        arpSemitone1 = clamp(Integer.parseInt(parts[0]), -48, 48, token);
                        arpSemitone2 = clamp(Integer.parseInt(parts[1]), -48, 48, token);
                        arpRateTicks = parts.length == 3 ? clamp(Integer.parseInt(parts[2]), 0, 16, token) : 0;
                    }
                    case 'D' -> delayFraction = clamp(Float.parseFloat(value), 0f, 0.95f, token);
                    case 'R' -> retriggerTicks = clamp(Integer.parseInt(value), 0, 16, token);
                    case 'C' -> cutTicks = clamp(Integer.parseInt(value), 0, 64, token);
                    default -> throw new IllegalArgumentException(
                            "Unknown effect code '" + code + "' - use G/Q (slide), A (arp), D (delay), "
                                    + "R (retrigger) or C (cut)");
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad value in \"" + token + "\"");
            }
        }
        return new TrackerPattern.NoteEffect(slideMode, slideSteps,
                arpSemitone1, arpSemitone2, arpRateTicks, delayFraction, retriggerTicks, cutTicks);
    }

    public static String format(TrackerPattern.NoteEffect effect) {
        StringBuilder text = new StringBuilder();
        if (effect.slideMode() == TrackerPattern.SlideMode.GLIDE) {
            appendToken(text, "G" + effect.slideSteps());
        } else if (effect.slideMode() == TrackerPattern.SlideMode.GLISSANDO) {
            appendToken(text, "Q" + effect.slideSteps());
        }
        if (effect.arpSemitone1() != 0 || effect.arpSemitone2() != 0 || effect.arpRateTicks() != 0) {
            String arp = "A" + effect.arpSemitone1() + "," + effect.arpSemitone2();
            if (effect.arpRateTicks() != 0) {
                arp += "," + effect.arpRateTicks();
            }
            appendToken(text, arp);
        }
        if (effect.delayFraction() != 0f) {
            appendToken(text, "D" + trimTrailingZeros(effect.delayFraction()));
        }
        if (effect.retriggerTicks() != 0) {
            appendToken(text, "R" + effect.retriggerTicks());
        }
        if (effect.cutTicks() != 0) {
            appendToken(text, "C" + effect.cutTicks());
        }
        return text.toString();
    }

    private static void appendToken(StringBuilder text, String token) {
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(token);
    }

    private static String trimTrailingZeros(float value) {
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted.endsWith(".") ? formatted.substring(0, formatted.length() - 1) : formatted;
    }

    private static int clamp(int value, int min, int max, String token) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(token + " out of range " + min + ".." + max);
        }
        return value;
    }

    private static float clamp(float value, float min, float max, String token) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(token + " out of range " + min + ".." + max);
        }
        return value;
    }
}

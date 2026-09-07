package JSynth.Tracker;

import JSynth.Sources.NoteName;

public final class NoteEntryText {
    private static final float MIN_PITCH = 0f;
    private static final float MAX_PITCH = 127f;

    public record Parsed(float pitch, TrackerPattern.NoteEffect effect) {
    }

    private NoteEntryText() {
    }

    public static String format(float pitch, TrackerPattern.NoteEffect effect) {
        String pitchText = NoteName.format(pitch);
        if (effect.equals(TrackerPattern.NoteEffect.NONE)) {
            return pitchText;
        }
        return pitchText + "|" + NoteEffectText.format(effect);
    }

    public static Parsed parse(String text, TrackerPattern.NoteEffect currentEffect) {
        String trimmed = text == null ? "" : text.trim();
        int bar = trimmed.indexOf('|');
        String pitchText = bar < 0 ? trimmed : trimmed.substring(0, bar);
        float pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, NoteName.parseFlexible(pitchText)));
        if (bar < 0) {
            return new Parsed(pitch, currentEffect);
        }
        TrackerPattern.NoteEffect effect = NoteEffectText.parse(trimmed.substring(bar + 1));
        return new Parsed(pitch, effect);
    }
}

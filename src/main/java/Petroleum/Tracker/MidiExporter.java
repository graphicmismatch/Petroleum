package Petroleum.Tracker;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import java.io.File;
import java.io.IOException;

public final class MidiExporter {
    private static final int TICKS_PER_BEAT = PianoRollClip.TICKS_PER_BEAT;
    private static final int SET_TEMPO_TYPE = 0x51;
    private static final int END_OF_TRACK_TYPE = 0x2F;

    private MidiExporter() {
    }

    public static void export(PianoRollClip clip, float bpm, File file) throws IOException {
        Sequence sequence = newSequence(bpm);
        Track track = sequence.getTracks()[0];
        for (PianoRollClip.Note note : clip.notes()) {
            addNote(track, note.voice() % 16, note.pitch(), note.velocity(),
                    note.start(), note.start() + note.length());
        }
        finish(sequence, track, Math.max(clip.lengthTicks(), clip.lastNoteEnd()), file);
    }

    public static void export(TrackerSong song, float bpm, File file) throws IOException {
        Sequence sequence = newSequence(bpm);
        Track track = sequence.getTracks()[0];
        long patternStartTick = 0;
        for (int orderIndex = 0; orderIndex < song.orderLength(); orderIndex++) {
            TrackerPattern pattern = song.pattern(song.orderAt(orderIndex));
            double ticksPerStep = TICKS_PER_BEAT / (double) pattern.stepsPerBeat();
            for (int channel = 0; channel < pattern.channelCount(); channel++) {
                for (TrackerPattern.NoteEvent note : pattern.notes(channel)) {
                    int endStep = Math.min(note.endStep(), pattern.totalSteps());
                    addNote(track, channel % 16, note.pitch(), 1f,
                            patternStartTick + Math.round(note.startStep() * ticksPerStep),
                            patternStartTick + Math.round(endStep * ticksPerStep));
                }
            }
            patternStartTick += Math.round(pattern.totalSteps() * ticksPerStep);
        }
        finish(sequence, track, patternStartTick, file);
    }

    private static Sequence newSequence(float bpm) throws IOException {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_BEAT);
            Track track = sequence.createTrack();
            int microsecondsPerQuarter = (int) Math.round(60_000_000.0 / Math.max(1f, bpm));
            byte[] data = {
                    (byte) (microsecondsPerQuarter >> 16),
                    (byte) (microsecondsPerQuarter >> 8),
                    (byte) microsecondsPerQuarter};
            track.add(new MidiEvent(new MetaMessage(SET_TEMPO_TYPE, data, data.length), 0));
            return sequence;
        } catch (InvalidMidiDataException ex) {
            throw new IOException("Could not build MIDI sequence: " + ex.getMessage(), ex);
        }
    }

    private static void addNote(Track track, int channel, float pitch, float velocity,
                                 long startTick, long endTick) throws IOException {
        int note = clamp(Math.round(pitch), 0, 127);
        int level = clamp(Math.round(velocity * 127f), 1, 127);
        try {
            track.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_ON, channel, note, level), Math.max(0, startTick)));
            track.add(new MidiEvent(
                    new ShortMessage(ShortMessage.NOTE_OFF, channel, note, 0),
                    Math.max(1, endTick)));
        } catch (InvalidMidiDataException ex) {
            throw new IOException("Could not write note " + note + ": " + ex.getMessage(), ex);
        }
    }

    private static void finish(Sequence sequence, Track track, long lengthTicks, File file) throws IOException {
        try {
            track.add(new MidiEvent(new MetaMessage(END_OF_TRACK_TYPE, new byte[0], 0),
                    Math.max(track.ticks(), lengthTicks)));
        } catch (InvalidMidiDataException ex) {
            throw new IOException("Could not close MIDI track: " + ex.getMessage(), ex);
        }
        MidiSystem.write(sequence, 1, file);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}

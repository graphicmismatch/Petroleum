package JSynth.Tracker;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class MidiImporter {
    private static final float DEFAULT_BPM = 120f;
    private static final int SET_TEMPO_TYPE = 0x51;

    public record Result(TrackerPattern pattern, float detectedBpm,
                          int mappedChannelCount, int skippedChannelCount) {
    }

    private MidiImporter() {
    }

    public static Result importFile(File file, int stepsPerBeat, int channelCount) throws IOException {
        Sequence sequence;
        try {
            sequence = MidiSystem.getSequence(file);
        } catch (InvalidMidiDataException ex) {
            throw new IllegalArgumentException("Not a valid MIDI file: " + ex.getMessage(), ex);
        }
        if (sequence.getDivisionType() != Sequence.PPQ) {
            throw new IllegalArgumentException(
                    "Only tick-based (PPQ) MIDI files are supported, not SMPTE-timed files");
        }

        float bpm = detectBpm(sequence);
        int ticksPerStep = Math.max(1,
                Math.round(sequence.getResolution() / (float) Math.max(1, stepsPerBeat)));

        List<Integer> mappedChannels = mapChannels(sequence, channelCount);
        int skipped = Math.max(0, countChannelsWithNotes(sequence) - mappedChannels.size());

        TrackerPattern pattern = new TrackerPattern(channelCount, stepsPerBeat * 4, stepsPerBeat);
        int highestStep = 0;
        for (int trackerChannel = 0; trackerChannel < mappedChannels.size(); trackerChannel++) {
            importChannel(sequence, mappedChannels.get(trackerChannel), trackerChannel, ticksPerStep, pattern);
            for (TrackerPattern.NoteEvent note : pattern.notes(trackerChannel)) {
                highestStep = Math.max(highestStep, note.endStep());
            }
        }

        int barSteps = stepsPerBeat * 4;
        int totalSteps = Math.max(barSteps, ((highestStep + barSteps - 1) / barSteps) * barSteps);
        pattern.setTotalSteps(totalSteps);

        return new Result(pattern, bpm, mappedChannels.size(), skipped);
    }

    private static int countChannelsWithNotes(Sequence sequence) {
        return channelsWithNotes(sequence).size();
    }

    private static List<Integer> mapChannels(Sequence sequence, int channelCount) {
        List<Integer> mapped = new ArrayList<>();
        for (int channel : channelsWithNotes(sequence)) {
            if (mapped.size() >= channelCount) {
                break;
            }
            mapped.add(channel);
        }
        return mapped;
    }

    private static Set<Integer> channelsWithNotes(Sequence sequence) {
        Set<Integer> channels = new TreeSet<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiMessage message = track.get(i).getMessage();
                if (message instanceof ShortMessage sm
                        && sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                    channels.add(sm.getChannel());
                }
            }
        }
        return channels;
    }

    private static void importChannel(Sequence sequence, int midiChannel, int trackerChannel,
                                       int ticksPerStep, TrackerPattern pattern) {
        List<long[]> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof ShortMessage sm && sm.getChannel() == midiChannel) {
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        events.add(new long[]{event.getTick(), sm.getData1(), 1});
                    } else if (sm.getCommand() == ShortMessage.NOTE_OFF
                            || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                        events.add(new long[]{event.getTick(), sm.getData1(), 0});
                    }
                }
            }
        }
        events.sort((a, b) -> Long.compare(a[0], b[0]));

        Long openTick = null;
        int openNote = -1;
        for (long[] evt : events) {
            long tick = evt[0];
            int note = (int) evt[1];
            boolean isOn = evt[2] == 1;
            if (isOn) {
                if (openTick != null) {
                    addQuantizedNote(pattern, trackerChannel, openTick, tick, openNote, ticksPerStep);
                }
                openTick = tick;
                openNote = note;
            } else if (openTick != null && note == openNote) {
                addQuantizedNote(pattern, trackerChannel, openTick, tick, openNote, ticksPerStep);
                openTick = null;
            }
        }
    }

    private static void addQuantizedNote(TrackerPattern pattern, int channel, long onTick, long offTick,
                                          int midiNote, int ticksPerStep) {
        int startStep = Math.round(onTick / (float) ticksPerStep);
        int endStep = Math.round(offTick / (float) ticksPerStep);
        int lengthSteps = Math.max(1, endStep - startStep);
        pattern.addNote(channel, new TrackerPattern.NoteEvent(startStep, lengthSteps, midiNote));
    }

    private static float detectBpm(Sequence sequence) {
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiMessage message = track.get(i).getMessage();
                if (message instanceof MetaMessage meta && meta.getType() == SET_TEMPO_TYPE) {
                    byte[] data = meta.getData();
                    if (data.length == 3) {
                        int microsecondsPerQuarter =
                                ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                        if (microsecondsPerQuarter > 0) {
                            return 60_000_000f / microsecondsPerQuarter;
                        }
                    }
                }
            }
        }
        return DEFAULT_BPM;
    }
}

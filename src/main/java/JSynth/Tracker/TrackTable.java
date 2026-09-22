package JSynth.Tracker;

import java.util.List;

final class TrackTable {
    final int channelCount;
    final int totalSteps;
    final int stepsPerBeat;
    final int speed;
    final String[] channelNames;
    final ChannelTrack[] channels;

    private TrackTable(int channelCount, int totalSteps, int stepsPerBeat, int speed, String[] channelNames,
            ChannelTrack[] channels) {
        this.channelCount = channelCount;
        this.totalSteps = totalSteps;
        this.stepsPerBeat = stepsPerBeat;
        this.speed = speed;
        this.channelNames = channelNames;
        this.channels = channels;
    }

    static TrackTable from(TrackerPattern pattern) {
        int channelCount = pattern.channelCount();
        String[] names = new String[channelCount];
        ChannelTrack[] channels = new ChannelTrack[channelCount];
        for (int c = 0; c < channelCount; c++) {
            names[c] = pattern.channelName(c);
            channels[c] = ChannelTrack.from(pattern.notes(c));
        }
        return new TrackTable(channelCount, pattern.totalSteps(), pattern.stepsPerBeat(), pattern.speed(),
                names, channels);
    }

    TrackerPattern toPattern() {
        TrackerPattern pattern = new TrackerPattern(channelCount, totalSteps, stepsPerBeat);
        pattern.setSpeed(speed);
        for (int c = 0; c < channelCount; c++) {
            pattern.setChannelName(c, channelNames[c]);
            ChannelTrack track = channels[c];
            for (int i = 0; i < track.startStep.length; i++) {
                pattern.addNote(c, new TrackerPattern.NoteEvent(
                        track.startStep[i], track.lengthSteps[i], track.pitch[i], track.effect[i]));
            }
        }
        return pattern;
    }

    static final class ChannelTrack {
        final int[] startStep;
        final int[] lengthSteps;
        final float[] pitch;
        final float[] frequencyHz;
        final TrackerPattern.NoteEffect[] effect;

        private ChannelTrack(int[] startStep, int[] lengthSteps, float[] pitch, float[] frequencyHz,
                TrackerPattern.NoteEffect[] effect) {
            this.startStep = startStep;
            this.lengthSteps = lengthSteps;
            this.pitch = pitch;
            this.frequencyHz = frequencyHz;
            this.effect = effect;
        }

        static ChannelTrack from(List<TrackerPattern.NoteEvent> notes) {
            int count = notes.size();
            int[] startStep = new int[count];
            int[] lengthSteps = new int[count];
            float[] pitch = new float[count];
            float[] frequencyHz = new float[count];
            TrackerPattern.NoteEffect[] effect = new TrackerPattern.NoteEffect[count];
            for (int i = 0; i < count; i++) {
                TrackerPattern.NoteEvent note = notes.get(i);
                startStep[i] = note.startStep();
                lengthSteps[i] = note.lengthSteps();
                pitch[i] = note.pitch();
                frequencyHz[i] = (float) (440.0 * Math.pow(2.0, (note.pitch() - 69) / 12.0));
                effect[i] = note.effect();
            }
            return new ChannelTrack(startStep, lengthSteps, pitch, frequencyHz, effect);
        }
    }
}

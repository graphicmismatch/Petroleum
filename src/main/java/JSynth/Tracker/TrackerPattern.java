package JSynth.Tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrackerPattern {
    public static final int DEFAULT_CHANNELS = 8;
    public static final int DEFAULT_TOTAL_STEPS = 64;
    public static final int DEFAULT_STEPS_PER_BEAT = 4;
    public static final int DEFAULT_SPEED = 6;

    public enum SlideMode { NONE, GLIDE, GLISSANDO }

    public record NoteEffect(
            SlideMode slideMode, int slideSteps,
            int arpSemitone1, int arpSemitone2, int arpRateTicks,
            float delayFraction, int retriggerTicks, int cutTicks) {
        public static final NoteEffect NONE =
                new NoteEffect(SlideMode.NONE, 0, 0, 0, 0, 0f, 0, 0);
    }

    public record NoteEvent(int startStep, int lengthSteps, float pitch, NoteEffect effect) {
        public NoteEvent {
            if (lengthSteps < 1) {
                throw new IllegalArgumentException("lengthSteps must be >= 1");
            }
            if (effect == null) {
                effect = NoteEffect.NONE;
            }
        }

        public NoteEvent(int startStep, int lengthSteps, float pitch) {
            this(startStep, lengthSteps, pitch, NoteEffect.NONE);
        }

        public int endStep() {
            return startStep + lengthSteps;
        }
    }

    private final int channelCount;
    private int totalSteps;
    private int stepsPerBeat;
    private int speed;
    private final String[] channelNames;
    private final List<List<NoteEvent>> channels;

    public TrackerPattern() {
        this(DEFAULT_CHANNELS, DEFAULT_TOTAL_STEPS, DEFAULT_STEPS_PER_BEAT);
    }

    public TrackerPattern(int totalSteps, int stepsPerBeat) {
        this(DEFAULT_CHANNELS, totalSteps, stepsPerBeat);
    }

    public TrackerPattern(int channelCount, int totalSteps, int stepsPerBeat) {
        this.channelCount = Math.max(1, channelCount);
        this.totalSteps = Math.max(1, totalSteps);
        this.stepsPerBeat = Math.max(1, stepsPerBeat);
        this.speed = DEFAULT_SPEED;
        this.channelNames = new String[this.channelCount];
        this.channels = new ArrayList<>(this.channelCount);
        for (int c = 0; c < this.channelCount; c++) {
            channelNames[c] = "Channel " + (c + 1);
            channels.add(new ArrayList<>());
        }
    }

    public int channelCount() {
        return channelCount;
    }

    public int totalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int steps) {
        totalSteps = Math.max(1, steps);
        for (List<NoteEvent> channel : channels) {
            channel.removeIf(note -> note.startStep() >= totalSteps);
        }
    }

    public int stepsPerBeat() {
        return stepsPerBeat;
    }

    public void setStepsPerBeat(int stepsPerBeat) {
        this.stepsPerBeat = Math.max(1, stepsPerBeat);
    }

    public int speed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = Math.max(1, speed);
    }

    public String channelName(int channel) {
        return channelNames[channel];
    }

    public void setChannelName(int channel, String name) {
        channelNames[channel] = name;
    }

    public List<NoteEvent> notes(int channel) {
        return Collections.unmodifiableList(channels.get(channel));
    }

    public void addNote(int channel, NoteEvent note) {
        List<NoteEvent> list = channels.get(channel);
        int low = 0;
        int high = list.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (list.get(mid).startStep() < note.startStep()) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        if (low > 0 && list.get(low - 1).endStep() > note.startStep()) {
            list.remove(--low);
        }
        int end = low;
        while (end < list.size() && list.get(end).startStep() < note.endStep()) {
            end++;
        }
        list.subList(low, end).clear();
        list.add(low, note);
    }

    public void removeNote(int channel, int startStep) {
        channels.get(channel).removeIf(note -> note.startStep() == startStep);
    }

    public NoteEvent noteAt(int channel, int step) {
        for (NoteEvent note : channels.get(channel)) {
            if (step >= note.startStep() && step < note.endStep()) {
                return note;
            }
        }
        return null;
    }

    public NoteEvent noteStartingAt(int channel, int startStep) {
        for (NoteEvent note : channels.get(channel)) {
            if (note.startStep() == startStep) {
                return note;
            }
        }
        return null;
    }

    public TrackerPattern copy() {
        TrackerPattern copy = new TrackerPattern(channelCount, totalSteps, stepsPerBeat);
        copy.speed = speed;
        for (int c = 0; c < channelCount; c++) {
            copy.channelNames[c] = channelNames[c];
            copy.channels.get(c).addAll(channels.get(c));
        }
        return copy;
    }

    public TrackerPattern withChannelCount(int newChannelCount) {
        TrackerPattern converted = new TrackerPattern(newChannelCount, totalSteps, stepsPerBeat);
        converted.speed = speed;
        for (int c = 0; c < Math.min(channelCount, newChannelCount); c++) {
            converted.setChannelName(c, channelName(c));
            for (NoteEvent note : notes(c)) {
                converted.addNote(c, note);
            }
        }
        return converted;
    }
}

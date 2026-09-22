package Petroleum.Tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrackerSong {
    private final int channelCount;
    private final List<TrackerPattern> patterns;
    private final List<Integer> order;

    public TrackerSong() {
        this(TrackerPattern.DEFAULT_CHANNELS);
    }

    public TrackerSong(int channelCount) {
        this.channelCount = Math.max(1, channelCount);
        this.patterns = new ArrayList<>();
        this.patterns.add(new TrackerPattern(this.channelCount,
                TrackerPattern.DEFAULT_TOTAL_STEPS, TrackerPattern.DEFAULT_STEPS_PER_BEAT));
        this.order = new ArrayList<>();
        this.order.add(0);
    }

    private TrackerSong(int channelCount, List<TrackerPattern> patterns, List<Integer> order) {
        this.channelCount = channelCount;
        this.patterns = patterns;
        this.order = order;
    }

    static TrackerSong fromParts(int channelCount, List<TrackerPattern> patterns, List<Integer> order) {
        return new TrackerSong(channelCount, new ArrayList<>(patterns), new ArrayList<>(order));
    }

    public int channelCount() {
        return channelCount;
    }

    public int patternCount() {
        return patterns.size();
    }

    public TrackerPattern pattern(int index) {
        return patterns.get(index);
    }

    public void replacePattern(int index, TrackerPattern pattern) {
        patterns.set(index, pattern);
    }

    public void addPattern() {
        patterns.add(new TrackerPattern(channelCount, TrackerPattern.DEFAULT_TOTAL_STEPS,
                TrackerPattern.DEFAULT_STEPS_PER_BEAT));
    }

    public void duplicatePattern(int index) {
        patterns.add(patterns.get(index).copy());
    }

    public void removePattern(int index) {
        if (patterns.size() <= 1) {
            return;
        }
        patterns.remove(index);
        for (int i = 0; i < order.size(); i++) {
            int value = order.get(i);
            if (value == index) {
                order.set(i, Math.min(index, patterns.size() - 1));
            } else if (value > index) {
                order.set(i, value - 1);
            }
        }
    }

    public int orderLength() {
        return order.size();
    }

    public int orderAt(int index) {
        return order.get(index);
    }

    public List<Integer> order() {
        return Collections.unmodifiableList(order);
    }

    public void insertOrderEntry(int atIndex, int patternIndex) {
        order.add(Math.max(0, Math.min(order.size(), atIndex)), patternIndex);
    }

    public void removeOrderEntry(int atIndex) {
        if (order.size() <= 1) {
            return;
        }
        order.remove(atIndex);
    }

    public void moveOrderEntry(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= order.size()) {
            return;
        }
        int value = order.remove(fromIndex);
        order.add(Math.max(0, Math.min(order.size(), toIndex)), value);
    }

    public TrackerSong copy() {
        List<TrackerPattern> copiedPatterns = new ArrayList<>(patterns.size());
        for (TrackerPattern pattern : patterns) {
            copiedPatterns.add(pattern.copy());
        }
        return new TrackerSong(channelCount, copiedPatterns, new ArrayList<>(order));
    }

    public TrackerSong withChannelCount(int newChannelCount) {
        List<TrackerPattern> converted = new ArrayList<>(patterns.size());
        for (TrackerPattern pattern : patterns) {
            converted.add(pattern.withChannelCount(newChannelCount));
        }
        return new TrackerSong(Math.max(1, newChannelCount), converted, new ArrayList<>(order));
    }
}

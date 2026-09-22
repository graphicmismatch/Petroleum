package JSynth.Tracker;

import java.util.ArrayList;
import java.util.List;

final class TrackSongTable {
    final int channelCount;
    final TrackTable[] patterns;
    final int[] order;

    private TrackSongTable(int channelCount, TrackTable[] patterns, int[] order) {
        this.channelCount = channelCount;
        this.patterns = patterns;
        this.order = order;
    }

    static TrackSongTable from(TrackerSong song) {
        TrackTable[] patterns = new TrackTable[song.patternCount()];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = TrackTable.from(song.pattern(i));
        }
        int[] order = new int[song.orderLength()];
        for (int i = 0; i < order.length; i++) {
            order[i] = song.orderAt(i);
        }
        return new TrackSongTable(song.channelCount(), patterns, order);
    }

    TrackTable patternAt(int orderIndex) {
        return patterns[order[orderIndex]];
    }

    TrackerSong toSong() {
        List<TrackerPattern> patternList = new ArrayList<>(patterns.length);
        for (TrackTable table : patterns) {
            patternList.add(table.toPattern());
        }
        List<Integer> orderList = new ArrayList<>(order.length);
        for (int index : order) {
            orderList.add(index);
        }
        return TrackerSong.fromParts(channelCount, patternList, orderList);
    }
}

package Petroleum.Gui;

import java.util.List;

final class SoundClipEditCommands {
    private SoundClipEditCommands() {
    }

    record TrackSnapshot(SoundClipTrack track, float[] samples) {
    }

    static EditCommand<SoundClipEditorView> trackEdit(
            List<TrackSnapshot> before, List<TrackSnapshot> after, String label) {
        return new EditCommand<>() {
            @Override
            public void undo(SoundClipEditorView editor) {
                for (TrackSnapshot snapshot : before) {
                    snapshot.track().setSamples(snapshot.samples());
                    editor.refreshLane(snapshot.track());
                }
                editor.remix();
            }

            @Override
            public void redo(SoundClipEditorView editor) {
                for (TrackSnapshot snapshot : after) {
                    snapshot.track().setSamples(snapshot.samples());
                    editor.refreshLane(snapshot.track());
                }
                editor.remix();
            }

            @Override
            public String label() {
                return label;
            }
        };
    }

    static EditCommand<SoundClipEditorView> trackListEdit(
            SoundClipTrack track, int index, boolean added, String label) {
        return new EditCommand<>() {
            @Override
            public void undo(SoundClipEditorView editor) {
                if (added) {
                    editor.removeTrackInternal(track);
                } else {
                    editor.insertTrackInternal(track, index);
                }
            }

            @Override
            public void redo(SoundClipEditorView editor) {
                if (added) {
                    editor.insertTrackInternal(track, index);
                } else {
                    editor.removeTrackInternal(track);
                }
            }

            @Override
            public String label() {
                return label;
            }
        };
    }
}

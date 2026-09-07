package JSynth.Sources;

import JSynth.Graph.AbstractNode;
import JSynth.Graph.NodeContext;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class MidiInputNode extends AbstractNode {
    public static final int PITCH = 0;
    public static final int GATE = 1;
    public static final int VELOCITY = 2;

    private record NoteState(float pitch, float gate, float velocity) {
        static final NoteState SILENT = new NoteState(0f, 0f, 0f);
    }

    private record HeldNote(int note, int velocity) {
    }

    private final Deque<HeldNote> heldNotes = new ArrayDeque<>();
    private volatile NoteState state = NoteState.SILENT;
    private volatile String deviceName;
    private MidiDevice device;
    private Transmitter transmitter;

    public MidiInputNode() {
        super(List.of(), List.of("pitch", "gate", "velocity"));
    }

    public static List<String> listAvailableDeviceNames() {
        List<String> names = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice candidate = MidiSystem.getMidiDevice(info);
                if (candidate.getMaxTransmitters() != 0) {
                    names.add(info.getName());
                }
            } catch (MidiUnavailableException ignored) {
            }
        }
        return names;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void connect(String name) throws MidiUnavailableException {
        disconnect();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            if (!info.getName().equals(name)) {
                continue;
            }
            MidiDevice candidate = MidiSystem.getMidiDevice(info);
            if (candidate.getMaxTransmitters() == 0) {
                continue;
            }
            candidate.open();
            Transmitter t = candidate.getTransmitter();
            t.setReceiver(new NoteReceiver());
            this.device = candidate;
            this.transmitter = t;
            this.deviceName = name;
            return;
        }
        throw new MidiUnavailableException("No such MIDI input device: " + name);
    }

    public void disconnect() {
        if (transmitter != null) {
            transmitter.close();
            transmitter = null;
        }
        if (device != null) {
            device.close();
            device = null;
        }
        deviceName = null;
        heldNotes.clear();
        state = NoteState.SILENT;
    }

    @Override
    public void process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context) {
        NoteState s = state;
        Arrays.fill(outputs[PITCH], 0, frameCount, s.pitch());
        Arrays.fill(outputs[GATE], 0, frameCount, s.gate());
        Arrays.fill(outputs[VELOCITY], 0, frameCount, s.velocity());
    }

    @Override
    public String getName() {
        return "MIDI Input";
    }

    private void publish(int note, int velocity) {
        float pitch = (float) (440.0 * Math.pow(2.0, (note - 69) / 12.0));
        state = new NoteState(pitch, 1f, velocity / 127f);
    }

    private final class NoteReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!(message instanceof ShortMessage sm)) {
                return;
            }
            int command = sm.getCommand();
            int note = sm.getData1();
            int velocity = sm.getData2();
            if (command == ShortMessage.NOTE_ON && velocity > 0) {
                heldNotes.removeIf(h -> h.note() == note);
                heldNotes.push(new HeldNote(note, velocity));
                publish(note, velocity);
            } else if (command == ShortMessage.NOTE_OFF
                    || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                heldNotes.removeIf(h -> h.note() == note);
                if (heldNotes.isEmpty()) {
                    NoteState previous = state;
                    state = new NoteState(previous.pitch(), 0f, previous.velocity());
                } else {
                    HeldNote top = heldNotes.peek();
                    publish(top.note(), top.velocity());
                }
            }
        }

        @Override
        public void close() {
        }
    }
}

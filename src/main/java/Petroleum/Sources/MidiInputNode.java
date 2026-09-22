package Petroleum.Sources;

import Petroleum.Graph.AbstractNode;
import Petroleum.Graph.NodeContext;
import Petroleum.Graph.Stateful;

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
import java.util.Map;

public class MidiInputNode extends AbstractNode implements Stateful {
    public static final int PITCH = 0;
    public static final int GATE = 1;
    public static final int VELOCITY = 2;
    public static final int OMNI = -1;
    private static final float BEND_RANGE_SEMITONES = 2f;

    private record NoteState(float pitch, float gate, float velocity) {
        static final NoteState SILENT = new NoteState(0f, 0f, 0f);
    }

    private record HeldNote(int note, int velocity) {
    }

    private final Deque<HeldNote> heldNotes = new ArrayDeque<>();
    private volatile NoteState state = NoteState.SILENT;
    private volatile float bend = 0f;
    private volatile int channel = OMNI;
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

    public static List<String> channelNames() {
        List<String> names = new ArrayList<>();
        names.add("Omni");
        for (int i = 1; i <= 16; i++) {
            names.add(Integer.toString(i));
        }
        return names;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getChannel() {
        return channel;
    }

    public String getChannelName() {
        return channel == OMNI ? "Omni" : Integer.toString(channel + 1);
    }

    public void setChannelName(String name) {
        this.channel = "Omni".equals(name) ? OMNI : Integer.parseInt(name) - 1;
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
        bend = 0f;
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

    @Override
    public void saveState(Map<String, String> state) {
        state.put("channel", Integer.toString(channel));
    }

    @Override
    public void loadState(Map<String, String> state) {
        String saved = state.get("channel");
        if (saved != null) {
            channel = Integer.parseInt(saved);
        }
    }

    private static String commandName(int command) {
        return switch (command) {
            case ShortMessage.NOTE_ON -> "NOTE_ON";
            case ShortMessage.NOTE_OFF -> "NOTE_OFF";
            case ShortMessage.PITCH_BEND -> "PITCH_BEND";
            case ShortMessage.CONTROL_CHANGE -> "CONTROL_CHANGE";
            case ShortMessage.PROGRAM_CHANGE -> "PROGRAM_CHANGE";
            case ShortMessage.CHANNEL_PRESSURE -> "CHANNEL_PRESSURE";
            case ShortMessage.POLY_PRESSURE -> "POLY_PRESSURE";
            default -> "0x" + Integer.toHexString(command);
        };
    }

    private static float noteFrequency(int note) {
        return (float) (440.0 * Math.pow(2.0, (note - 69) / 12.0));
    }

    private void publish(int note, int velocity) {
        float bentFrequency = (float) (noteFrequency(note) * Math.pow(2.0, bend * BEND_RANGE_SEMITONES / 12.0));
        state = new NoteState(bentFrequency, 1f, velocity / 127f);
    }

    private final class NoteReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!(message instanceof ShortMessage sm)) {
                return;
            }
            int ch = channel;
            boolean filtered = ch != OMNI && sm.getChannel() != ch;
            MidiDebugLog.log(String.format("%-24s ch%-3d %-14s d1=%-3d d2=%-3d %s",
                    deviceName == null ? "?" : deviceName, sm.getChannel() + 1, commandName(sm.getCommand()),
                    sm.getData1(), sm.getData2(), filtered ? "filtered" : "accepted"));
            if (filtered) {
                return;
            }
            int command = sm.getCommand();
            if (command == ShortMessage.PITCH_BEND) {
                int value = (sm.getData2() << 7) | sm.getData1();
                bend = (value - 8192) / 8192f;
                MidiDebugLog.log(String.format("  -> pitch bend value=%-6d bend=%+.3f", value, bend));
                if (!heldNotes.isEmpty()) {
                    HeldNote top = heldNotes.peek();
                    publish(top.note(), top.velocity());
                }
                return;
            }
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

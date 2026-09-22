package JSynth.Audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

public final class AudioInputDevice {
    private AudioInputDevice() {
    }

    public static List<String> listAvailableDeviceNames() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            for (Line.Info lineInfo : mixer.getTargetLineInfo()) {
                if (TargetDataLine.class.isAssignableFrom(lineInfo.getLineClass())) {
                    names.add(displayName(info));
                    break;
                }
            }
        }
        return names;
    }

    public static TargetDataLine open(String displayName, AudioFormat format) throws LineUnavailableException {
        Mixer.Info info = displayName == null ? null : findMixerInfo(displayName);
        TargetDataLine line = info == null
                ? AudioSystem.getTargetDataLine(format)
                : (TargetDataLine) AudioSystem.getMixer(info).getLine(new DataLine.Info(TargetDataLine.class, format));
        line.open(format);
        return line;
    }

    private static Mixer.Info findMixerInfo(String displayName) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (displayName(info).equals(displayName)) {
                return info;
            }
        }
        return null;
    }

    private static String displayName(Mixer.Info info) {
        String detail = jackDetail(info.getDescription());
        return detail == null ? info.getName() : info.getName() + " — " + detail;
    }

    private static String jackDetail(String description) {
        int colon = description.indexOf(": ");
        if (colon < 0) {
            return null;
        }
        String[] parts = description.substring(colon + 2).split(",", -1);
        if (parts.length < 2) {
            return null;
        }
        String cardName = parts[0].trim();
        String detail = parts[1].trim();
        return detail.isEmpty() || detail.equalsIgnoreCase(cardName) ? null : detail;
    }
}

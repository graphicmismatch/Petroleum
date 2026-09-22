package JSynth.Sinks;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;

public interface VirtualLineOut {
    String getMixerName();

    void setMixerName(String mixerName);

    boolean isStereo();

    static List<String> listAvailableMixerNames() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            for (Line.Info lineInfo : mixer.getSourceLineInfo()) {
                if (SourceDataLine.class.isAssignableFrom(lineInfo.getLineClass())) {
                    names.add(info.getName());
                    break;
                }
            }
        }
        return names;
    }
}

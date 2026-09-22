package Petroleum.Generators;

import java.util.List;

record CategorySpec(List<String> waveforms, Pitch pitch, Envelope envelope, Range cutoff) {
}

record Range(float min, float max) {
}

record Pitch(Range frequency, Range slide, Range slideTime) {
}

record Envelope(Range attack, Range sustain, Range punch, Range decay) {
}

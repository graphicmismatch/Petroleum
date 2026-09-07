package JSynth.Serialization;

import JSynth.Sources.ConstantNode;

public record Macro(String name, ConstantNode target, float min, float max) {
}

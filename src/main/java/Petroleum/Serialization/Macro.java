package Petroleum.Serialization;

import Petroleum.Sources.ConstantNode;

public record Macro(String name, ConstantNode target, float min, float max) {
}

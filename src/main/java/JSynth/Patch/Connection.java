package JSynth.Patch;

import JSynth.Graph.Node;

public record Connection(Node source, String sourceOutput, Node destination, String destinationInput) {
}

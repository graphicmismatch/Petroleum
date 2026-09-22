package Petroleum.Patch;

import Petroleum.Graph.Node;

public record Connection(Node source, String sourceOutput, Node destination, String destinationInput) {
}

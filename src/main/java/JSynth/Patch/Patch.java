package JSynth.Patch;

import JSynth.Graph.Node;
import JSynth.Graph.NodeContext;
import JSynth.Graph.OneShot;
import JSynth.Sinks.AudioOutNode;
import JSynth.Sinks.AudioSink;
import JSynth.Sinks.StereoAudioOutNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Patch {
    public static final int DEFAULT_BLOCK_CAPACITY = 1024;

    private final Node[] order;
    private final Map<Node, Integer> nodeIndex;
    private final float[][][] inputBlocks;
    private final float[][][] outputBlocks;
    private final int audioOutIndex;
    private final boolean stereoSink;
    private final int blockCapacity;
    private final OneShot[] oneShots;
    private final FeedbackTap[] feedbackTaps;
    private boolean rendered;

    public Patch(List<Node> nodes, List<Connection> connections) {
        this(nodes, connections, DEFAULT_BLOCK_CAPACITY);
    }

    private Patch(List<Node> nodes, List<Connection> connections, int blockCapacity) {
        this.blockCapacity = blockCapacity;
        SortResult sorted = topologicalSort(nodes, connections);
        this.order = sorted.order().toArray(new Node[0]);

        Map<Node, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < order.length; i++) {
            orderIndex.put(order[i], i);
        }
        this.nodeIndex = orderIndex;

        List<OneShot> collectedOneShots = new ArrayList<>();
        for (Node node : order) {
            if (node instanceof OneShot oneShot) {
                collectedOneShots.add(oneShot);
            }
        }
        this.oneShots = collectedOneShots.toArray(new OneShot[0]);

        int audioOut = -1;
        boolean stereo = false;
        this.inputBlocks = new float[order.length][][];
        this.outputBlocks = new float[order.length][][];
        boolean[][] connected = new boolean[order.length][];
        for (int i = 0; i < order.length; i++) {
            Node node = order[i];
            if (node instanceof AudioSink) {
                if (audioOut != -1) {
                    throw new IllegalStateException("Patch must contain exactly one AudioOutNode/StereoAudioOutNode");
                }
                audioOut = i;
                stereo = node instanceof StereoAudioOutNode;
            }

            int inputCount = node.getInputPorts().size();
            float[][] inputs = new float[inputCount][];
            for (int j = 0; j < inputCount; j++) {
                float[] block = new float[blockCapacity];
                Arrays.fill(block, node.getInputPorts().get(j).defaultValue());
                inputs[j] = block;
            }
            this.inputBlocks[i] = inputs;

            int outputCount = node.getOutputPorts().size();
            float[][] outputs = new float[outputCount][];
            for (int j = 0; j < outputCount; j++) {
                outputs[j] = new float[blockCapacity];
            }
            this.outputBlocks[i] = outputs;

            connected[i] = new boolean[inputCount];
        }

        if (audioOut == -1) {
            throw new IllegalStateException("Patch must contain exactly one AudioOutNode/StereoAudioOutNode");
        }
        this.audioOutIndex = audioOut;
        this.stereoSink = stereo;

        List<FeedbackTap> taps = new ArrayList<>();
        for (Connection connection : connections) {
            int destIndex = orderIndex.get(connection.destination());
            int sourceIndex = orderIndex.get(connection.source());
            int destInputIndex = connection.destination().inputIndexOf(connection.destinationInput());
            int sourceOutputIndex = connection.source().outputIndexOf(connection.sourceOutput());
            if (connected[destIndex][destInputIndex]) {
                throw new IllegalStateException(
                        "Input '" + connection.destinationInput() + "' on " + connection.destination().getName()
                                + " is already driven by another connection");
            }
            connected[destIndex][destInputIndex] = true;
            if (sorted.feedbackConnections().contains(connection)) {
                float[] feedbackBlock = new float[blockCapacity];
                Arrays.fill(feedbackBlock, connection.destination().getInputPorts().get(destInputIndex).defaultValue());
                this.inputBlocks[destIndex][destInputIndex] = feedbackBlock;
                taps.add(new FeedbackTap(sourceIndex, sourceOutputIndex, feedbackBlock));
            } else {
                this.inputBlocks[destIndex][destInputIndex] = this.outputBlocks[sourceIndex][sourceOutputIndex];
            }
        }
        this.feedbackTaps = taps.toArray(new FeedbackTap[0]);
    }

    private record SortResult(List<Node> order, Set<Connection> feedbackConnections) {
    }

    private record FeedbackTap(int sourceIndex, int sourceOutputIndex, float[] buffer) {
    }

    private static SortResult topologicalSort(List<Node> nodes, List<Connection> connections) {
        Map<Node, List<Connection>> outgoing = new HashMap<>();
        for (Node node : nodes) {
            outgoing.put(node, new ArrayList<>());
        }
        for (Connection connection : connections) {
            requireInPatch(outgoing, connection.source(), "source");
            requireInPatch(outgoing, connection.destination(), "destination");
            outgoing.get(connection.source()).add(connection);
        }

        Set<Node> onStack = new HashSet<>();
        Set<Node> visited = new HashSet<>();
        Set<Connection> feedback = new LinkedHashSet<>();
        Deque<Node> finished = new ArrayDeque<>();
        for (Node node : nodes) {
            if (!visited.contains(node)) {
                visit(node, outgoing, onStack, visited, feedback, finished);
            }
        }
        return new SortResult(new ArrayList<>(finished), feedback);
    }

    private static void visit(Node node, Map<Node, List<Connection>> outgoing, Set<Node> onStack,
            Set<Node> visited, Set<Connection> feedback, Deque<Node> finished) {
        onStack.add(node);
        for (Connection connection : outgoing.get(node)) {
            Node next = connection.destination();
            if (onStack.contains(next)) {
                feedback.add(connection);
            } else if (!visited.contains(next)) {
                visit(next, outgoing, onStack, visited, feedback, finished);
            }
        }
        onStack.remove(node);
        visited.add(node);
        finished.push(node);
    }

    private static void requireInPatch(Map<Node, ?> known, Node node, String role) {
        if (!known.containsKey(node)) {
            throw new IllegalArgumentException(
                    "Connection " + role + " '" + node.getName() + "' is not in the patch's node list");
        }
    }

    public int copyOutputBlock(Node node, int outputIndex, float[] dest) {
        Integer index = nodeIndex.get(node);
        if (index == null || outputIndex < 0 || outputIndex >= outputBlocks[index].length) {
            return 0;
        }
        int count = Math.min(dest.length, blockCapacity);
        System.arraycopy(outputBlocks[index][outputIndex], 0, dest, 0, count);
        return count;
    }

    public int copyInputBlock(Node node, int inputIndex, float[] dest) {
        Integer index = nodeIndex.get(node);
        if (index == null || inputIndex < 0 || inputIndex >= inputBlocks[index].length) {
            return 0;
        }
        int count = Math.min(dest.length, blockCapacity);
        System.arraycopy(inputBlocks[index][inputIndex], 0, dest, 0, count);
        return count;
    }

    public boolean hasOneShots() {
        return oneShots.length > 0;
    }

    public boolean isFinished() {
        if (!rendered || oneShots.length == 0) {
            return false;
        }
        for (OneShot oneShot : oneShots) {
            if (!oneShot.isIdle()) {
                return false;
            }
        }
        return true;
    }

    public boolean isStereo() {
        return stereoSink;
    }

    public void renderBlock(float[] outBuffer, int numSamples, NodeContext context) {
        requireMonoSink();
        process(numSamples, context);
        System.arraycopy(inputBlocks[audioOutIndex][AudioOutNode.IN], 0, outBuffer, 0, numSamples);
        context.advance(numSamples);
    }

    public void renderNested(float[] outBuffer, int outOffset, int numSamples, NodeContext context) {
        requireMonoSink();
        process(numSamples, context);
        System.arraycopy(inputBlocks[audioOutIndex][AudioOutNode.IN], 0, outBuffer, outOffset, numSamples);
    }

    public void renderBlockStereo(float[] left, float[] right, int numSamples, NodeContext context) {
        requireStereoSink();
        process(numSamples, context);
        System.arraycopy(inputBlocks[audioOutIndex][StereoAudioOutNode.IN_L], 0, left, 0, numSamples);
        System.arraycopy(inputBlocks[audioOutIndex][StereoAudioOutNode.IN_R], 0, right, 0, numSamples);
        context.advance(numSamples);
    }

    public void renderNestedStereo(float[] left, float[] right, int outOffset, int numSamples, NodeContext context) {
        requireStereoSink();
        process(numSamples, context);
        System.arraycopy(inputBlocks[audioOutIndex][StereoAudioOutNode.IN_L], 0, left, outOffset, numSamples);
        System.arraycopy(inputBlocks[audioOutIndex][StereoAudioOutNode.IN_R], 0, right, outOffset, numSamples);
    }

    private void requireMonoSink() {
        if (stereoSink) {
            throw new IllegalStateException("This patch's sink is a StereoAudioOutNode - call the stereo render methods instead");
        }
    }

    private void requireStereoSink() {
        if (!stereoSink) {
            throw new IllegalStateException("This patch's sink is a mono AudioOutNode - call the mono render methods instead");
        }
    }

    private void process(int numSamples, NodeContext context) {
        if (numSamples > blockCapacity) {
            throw new IllegalArgumentException(
                    "numSamples (" + numSamples + ") exceeds this patch's block capacity (" + blockCapacity + ")");
        }
        for (int i = 0; i < order.length; i++) {
            order[i].process(inputBlocks[i], outputBlocks[i], numSamples, context);
        }
        for (FeedbackTap tap : feedbackTaps) {
            System.arraycopy(outputBlocks[tap.sourceIndex()][tap.sourceOutputIndex()], 0, tap.buffer(), 0, numSamples);
        }
        if (!rendered) {
            rendered = true;
        }
    }
}

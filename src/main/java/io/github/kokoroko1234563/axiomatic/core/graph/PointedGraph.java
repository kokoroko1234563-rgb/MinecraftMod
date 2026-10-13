package io.github.kokoroko1234563.axiomatic.core.graph;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's one data type: a directed graph of labelled nodes with ordered edges and one
 * distinguished node, the point. Numerals, statements, circuits and proofs are all stored this
 * way, so there is one save format and one renderer (the Graph Lens).
 *
 * <p>Nodes are listed children-first, so every edge points to an earlier node and the graph is
 * acyclic. Identical subgraphs are stored once ({@link Builder} shares them), which is the
 * "sharing" mechanic: x⁸ as ((x²)²)² has three multiplication nodes, not seven.
 */
public record PointedGraph(List<Node> nodes, int point) {
    /** A node: a label such as "S", "0", "app:add" or "eq", and its children in port order. */
    public record Node(String label, List<Integer> children) {
        public Node {
            children = List.copyOf(children);
        }
    }

    public PointedGraph {
        nodes = List.copyOf(nodes);
        for (int i = 0; i < nodes.size(); i++) {
            for (int child : nodes.get(i).children()) {
                if (child < 0 || child >= i) {
                    throw new IllegalArgumentException("Node " + i + " has an edge to " + child
                            + "; edges must point to earlier nodes");
                }
            }
        }
        if (point < 0 || point >= nodes.size()) {
            throw new IllegalArgumentException("Point " + point + " is not a node");
        }
    }

    public Node pointNode() {
        return nodes.get(point);
    }

    /** Builds a graph bottom-up, sharing identical nodes. */
    public static final class Builder {
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Node, Integer> index = new HashMap<>();

        /** Adds a node (or finds the identical one already added) and returns its index. */
        public int add(String label, List<Integer> children) {
            Node node = new Node(label, children);
            return index.computeIfAbsent(node, n -> {
                nodes.add(n);
                return nodes.size() - 1;
            });
        }

        public PointedGraph build(int point) {
            return new PointedGraph(nodes, point);
        }
    }

    // ---- Binary format, used for saves and network packets --------------------------------

    private static final int FORMAT_VERSION = 1;

    public void write(DataOutput out) throws IOException {
        out.writeByte(FORMAT_VERSION);
        out.writeInt(nodes.size());
        for (Node node : nodes) {
            out.writeUTF(node.label());
            out.writeInt(node.children().size());
            for (int child : node.children()) {
                out.writeInt(child);
            }
        }
        out.writeInt(point);
    }

    public static PointedGraph read(DataInput in) throws IOException {
        int version = in.readByte();
        if (version != FORMAT_VERSION) {
            throw new IOException("Unknown pointed-graph format version " + version);
        }
        int count = in.readInt();
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String label = in.readUTF();
            int arity = in.readInt();
            List<Integer> children = new ArrayList<>(arity);
            for (int j = 0; j < arity; j++) {
                children.add(in.readInt());
            }
            nodes.add(new Node(label, children));
        }
        try {
            return new PointedGraph(nodes, in.readInt());
        } catch (IllegalArgumentException e) {
            throw new IOException("Corrupt pointed graph: " + e.getMessage(), e);
        }
    }
}

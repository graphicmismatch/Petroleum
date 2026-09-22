package JSynth.Gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GraphLayout {
    static final double COLUMN_GAP = 140;
    static final double ROW_GAP = 40;
    private static final int RELAX_PASSES = 12;

    private GraphLayout() {
    }

    static double[][] arrange(int[][] edges, double[] sourceOffset, double[] targetOffset,
                              double[] width, double[] height, double[] oldY,
                              double originX, double originY) {
        int n = width.length;
        List<List<Integer>> succs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            succs.add(new ArrayList<>());
        }
        int[] indegree = new int[n];
        for (int[] edge : edges) {
            succs.get(edge[0]).add(edge[1]);
            indegree[edge[1]]++;
        }

        int[] rank = new int[n];
        List<Integer> topo = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int v = queue.poll();
            topo.add(v);
            for (int s : succs.get(v)) {
                rank[s] = Math.max(rank[s], rank[v] + 1);
                if (--indegree[s] == 0) {
                    queue.add(s);
                }
            }
        }

        boolean[] acyclic = new boolean[n];
        for (int v : topo) {
            acyclic[v] = true;
        }
        for (int t = topo.size() - 1; t >= 0; t--) {
            int v = topo.get(t);
            int nearest = Integer.MAX_VALUE;
            for (int s : succs.get(v)) {
                if (acyclic[s]) {
                    nearest = Math.min(nearest, rank[s] - 1);
                }
            }
            if (nearest != Integer.MAX_VALUE && nearest > rank[v]) {
                rank[v] = nearest;
            }
        }

        int maxRank = 0;
        for (int v : topo) {
            maxRank = Math.max(maxRank, rank[v]);
        }
        if (topo.size() < n) {
            maxRank++;
            for (int i = 0; i < n; i++) {
                if (!acyclic[i]) {
                    rank[i] = maxRank;
                }
            }
        }

        List<List<Integer>> columns = new ArrayList<>();
        for (int c = 0; c <= maxRank; c++) {
            columns.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            columns.get(rank[i]).add(i);
        }

        double[] x = new double[n];
        double[] y = new double[n];
        double columnX = originX;
        for (List<Integer> column : columns) {
            column.sort(Comparator.comparingDouble(v -> oldY[v]));
            double rowY = originY;
            double columnWidth = 0;
            for (int v : column) {
                x[v] = columnX;
                y[v] = rowY;
                rowY += height[v] + ROW_GAP;
                columnWidth = Math.max(columnWidth, width[v]);
            }
            if (!column.isEmpty()) {
                columnX += columnWidth + COLUMN_GAP;
            }
        }

        double[] ideal = new double[n];
        for (int pass = 0; pass < RELAX_PASSES; pass++) {
            boolean forward = pass % 2 == 0;
            for (int c = 0; c < columns.size(); c++) {
                List<Integer> column = columns.get(forward ? c : columns.size() - 1 - c);
                relaxColumn(column, edges, sourceOffset, targetOffset, height, y, ideal);
            }
        }

        double minY = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, y[i]);
        }
        for (int i = 0; i < n; i++) {
            y[i] += originY - minY;
        }
        return new double[][] {x, y};
    }

    private static void relaxColumn(List<Integer> column, int[][] edges, double[] sourceOffset,
                                    double[] targetOffset, double[] height, double[] y, double[] ideal) {
        if (column.isEmpty()) {
            return;
        }
        for (int v : column) {
            double sum = 0;
            int count = 0;
            for (int e = 0; e < edges.length; e++) {
                if (edges[e][1] == v) {
                    sum += y[edges[e][0]] + sourceOffset[e] - targetOffset[e];
                    count++;
                } else if (edges[e][0] == v) {
                    sum += y[edges[e][1]] + targetOffset[e] - sourceOffset[e];
                    count++;
                }
            }
            ideal[v] = count > 0 ? sum / count : y[v];
        }
        column.sort(Comparator.comparingDouble(v -> ideal[v]));

        double next = -Double.MAX_VALUE;
        double drift = 0;
        for (int v : column) {
            y[v] = Math.max(ideal[v], next);
            next = y[v] + height[v] + ROW_GAP;
            drift += ideal[v] - y[v];
        }
        drift /= column.size();
        for (int v : column) {
            y[v] += drift;
        }
    }
}

package org.lwjgl.demo.util;

import static java.lang.Math.*;

import java.util.List;

/**
 * Greedy meshing based on the JavaScript code from
 * https://0fps.net/2012/07/07/meshing-minecraft-part-2/
 * 
 * Instances of this class are <i>not</i> thread-safe, so calls to
 * {@link #mesh(byte[], List)} on the same instance must be externally
 * synchronized.
 * 
 * @author Kai Burjack
 */
public class GreedyMeshing {
    public static class Face {
        public static final byte SIDE_NX = 0;
        public static final byte SIDE_PX = 1;
        public static final byte SIDE_NY = 2;
        public static final byte SIDE_PY = 3;
        public static final byte SIDE_NZ = 4;
        public static final byte SIDE_PZ = 5;

        public byte u0, v0, u1, v1, p, s;

        public Face(int u0, int v0, int u1, int v1, int p, int s) {
            this.u0 = (byte) u0;
            this.v0 = (byte) v0;
            this.u1 = (byte) u1;
            this.v1 = (byte) v1;
            this.p = (byte) p;
            this.s = (byte) s;
        }
    }

    private final int[] du = new int[3], dv = new int[3], q = new int[3], x = new int[3];
    private final int[] m;
    private final int dx, dy;
    private final int[] dims;
    private boolean singleOpaque;

    public GreedyMeshing(int dx, int dy, int dz) {
        if (dx < 1 || dx > 256)
            throw new IllegalArgumentException("dx");
        if (dy < 1 || dy > 256)
            throw new IllegalArgumentException("dy");
        if (dz < 1 || dz > 256)
            throw new IllegalArgumentException("dz");
        this.dx = dx;
        this.dy = dy;
        this.m = new int[max(dx, dy) * max(dy, dz)];
        this.dims = new int[] { dx, dy, dz };
    }

    public void setSingleOpaque(boolean singleOpaque) {
        this.singleOpaque = singleOpaque;
    }

    public boolean isSingleOpaque() {
        return singleOpaque;
    }

    private byte at(byte[] vs, int x, int y, int z) {
        return vs[x + 1 + (dx + 2) * (y + 1 + (dy + 2) * (z + 1))];
    }

    public void mesh(byte[] vs, List<Face> faces) {
        for (byte d = 0; d < 3; d++) {
            int u = ((d + 1) % 3), v = ((d + 2) % 3);
            q[0] = 0;
            q[1] = 0;
            q[2] = 0;
            q[d] = 1;
            for (x[d] = -1; x[d] < dims[d];) {
                generateMask(vs, d, u, v);
                x[d]++;
                mergeAndGenerateFaces(faces, u, v, d);
            }
        }
    }

    private void generateMask(byte[] vs, int d, int u, int v) {
        int n = 0;
        for (x[v] = 0; x[v] < dims[v]; x[v]++)
            for (x[u] = 0; x[u] < dims[u]; x[u]++, n++)
                generateMask(vs, d, n);
    }

    private void generateMask(byte[] vs, int d, int n) {
        int a = at(vs, x[0], x[1], x[2]);
        int b = at(vs, x[0] + q[0], x[1] + q[1], x[2] + q[2]);
        if (((a == 0) == (b == 0)))
            m[n] = 0;
        else if (a != 0)
            m[n] = singleOpaque ? 1 : a;
        else
            m[n] = -(singleOpaque ? 1 : b);
    }

    private void mergeAndGenerateFaces(List<Face> faces, int u, int v, int d) {
        for (int j = 0, n = 0; j < dims[v]; ++j)
            for (int i = 0, incr; i < dims[u]; i += incr, n += incr)
                incr = mergeAndGenerateFace(faces, u, v, n, j, i, d);
    }

    private int mergeAndGenerateFace(List<Face> faces, int u, int v, int n, int j, int i, int d) {
        if (m[n] == 0)
            return 1;
        int w = determineWidth(u, n, i, m[n]);
        int h = determineHeight(u, v, n, j, m[n], w);
        int s = determineFaceRegion(u, v, n, j, i, d, w, h);
        addFace(faces, u, v, d, s);
        eraseMask(u, n, w, h);
        return w;
    }

    private void eraseMask(int u, int n, int w, int h) {
        for (int l = 0; l < h; l++)
            for (int k = 0; k < w; k++)
                m[n + k + l * dims[u]] = 0;
    }

    private int determineFaceRegion(int u, int v, int n, int j, int i, int d, int w, int h) {
        x[u] = i;
        x[v] = j;
        if (m[n] > 0) {
            du[d] = dv[d] = du[v] = dv[u] = 0;
            dv[v] = h;
            du[u] = w;
            return 1;
        } else {
            du[d] = dv[d] = du[u] = dv[v] = 0;
            du[v] = h;
            dv[u] = w;
            return 0;
        }
    }

    private int determineWidth(int u, int n, int i, int c) {
        int w = 1;
        while (n + w < m.length && i + w < dims[u] && c == m[n + w])
            w++;
        return w;
    }

    private int determineHeight(int u, int v, int n, int j, int c, int w) {
        int h;
        boolean done = false;
        for (h = 1; j + h < dims[v]; h++) {
            for (int k = 0; k < w; k++)
                if (c != m[n + k + h * dims[u]]) {
                    done = true;
                    break;
                }
            if (done)
                break;
        }
        return h;
    }

    private void addFace(List<Face> faces, int u, int v, int d, int s) {
        faces.add(new Face(x[u], x[v], x[u] + du[u] + dv[u], x[v] + du[v] + dv[v], x[d], (d << 1) + s));
    }
}

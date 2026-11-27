package client_GUI;

import javafx.scene.shape.TriangleMesh;

import java.util.Arrays;

/** Small growable helper for building JavaFX {@link TriangleMesh}es (positions, optional UVs, faces). */
final class MeshBuilder {
    private float[] pts = new float[3 * 256];
    private float[] uvs = new float[2 * 256];
    private int[] faces = new int[6 * 256];
    private int np, nf;

    int points() { return np; }

    int point(float x, float y, float z) {
        return point(x, y, z, 0, 0);
    }

    int point(float x, float y, float z, float u, float v) {
        if (3 * (np + 1) > pts.length) {
            pts = Arrays.copyOf(pts, pts.length * 2);
            uvs = Arrays.copyOf(uvs, uvs.length * 2);
        }
        pts[3 * np] = x; pts[3 * np + 1] = y; pts[3 * np + 2] = z;
        uvs[2 * np] = u; uvs[2 * np + 1] = v;
        return np++;
    }

    void face(int a, int b, int c) {
        if (6 * (nf + 1) > faces.length) faces = Arrays.copyOf(faces, faces.length * 2);
        int t = 6 * nf;
        // Texture coordinates are stored per point, so the UV index equals the point index.
        faces[t] = a; faces[t + 1] = a;
        faces[t + 2] = b; faces[t + 3] = b;
        faces[t + 4] = c; faces[t + 5] = c;
        nf++;
    }

    TriangleMesh build() {
        TriangleMesh m = new TriangleMesh();
        m.getPoints().setAll(pts, 0, 3 * np);
        m.getTexCoords().setAll(uvs, 0, 2 * np);
        m.getFaces().setAll(faces, 0, 6 * nf);
        return m;
    }
}

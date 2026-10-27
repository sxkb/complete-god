package com.divinering.client.render;

import java.util.Arrays;

/**
 * Tiny growable interleaved vertex buffer.
 *
 * <p>Layout is deliberately fixed and compact:</p>
 * <pre>
 *   vec3 position | vec2 localUV | vec4 (part, edge, arc, seed)
 * </pre>
 *
 * <p>One vertex is nine floats. No vanilla vertex format, BufferBuilder or
 * RenderType is involved.</p>
 */
final class MeshData {

    static final int FLOATS_PER_VERTEX = 9;
    private static final int FLOATS_PER_TRIANGLE = FLOATS_PER_VERTEX * 3;

    private float[] data = new float[FLOATS_PER_TRIANGLE * 512];
    private int size;

    int vertexCount() {
        return size / FLOATS_PER_VERTEX;
    }

    float[] raw() {
        return data;
    }

    void addTriangle(Vertex a, Vertex b, Vertex c) {
        ensure(FLOATS_PER_TRIANGLE);
        append(a);
        append(b);
        append(c);
    }

    void addQuad(Vertex a, Vertex b, Vertex c, Vertex d) {
        addTriangle(a, b, c);
        addTriangle(a, c, d);
    }

    private void append(Vertex v) {
        data[size++] = v.x;
        data[size++] = v.y;
        data[size++] = v.z;
        data[size++] = v.x;
        data[size++] = v.y;
        data[size++] = v.part;
        data[size++] = v.edge;
        data[size++] = v.arc;
        data[size++] = v.seed;
    }

    private void ensure(int extra) {
        if (size + extra > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length << 1, size + extra));
        }
    }

    /**
     * A single vertex. {@code edge} is -1 on one side of a ribbon and +1 on the other,
     * {@code arc} runs 0..1 along a shape, and {@code seed} is a stable per-element
     * random value used by the shader for flow and erosion.
     */
    record Vertex(float x, float y, float z, float part, float edge, float arc, float seed) {
    }
}

package com.divinering.client.render;

import com.divinering.client.render.MeshData.Vertex;

import java.util.Random;

/**
 * Procedural geometry for the divine ring.
 *
 * <p>All coordinates are normalised to the outer ring radius (1.0). The layout was
 * traced from the reference emblem: eight outer arcs with breaks on every 45 degree
 * axis, two solid upper middle arcs, eroded/dashed lower middle arcs, a complete inner
 * circle, four swept pinwheel blades, two horizontal wings and a split vertical spear.</p>
 *
 * <p>The mesh is built once and uploaded to a private VBO. Nothing here depends on
 * Minecraft's BufferBuilder, vertex formats, RenderType or particle engine.</p>
 */
final class DivineRingGeometry {

    static final float OUTER_RADIUS = 1.000f;
    static final float OUTER_HALF_WIDTH = 0.0185f;
    static final float MIDDLE_RADIUS = 0.439f;
    static final float MIDDLE_HALF_WIDTH = 0.0185f;
    static final float INNER_RADIUS = 0.186f;
    static final float INNER_HALF_WIDTH = 0.0072f;

    static final float PART_OUTER = 1.0f;
    static final float PART_MIDDLE = 2.0f;
    static final float PART_INNER = 3.0f;
    static final float PART_BLADE = 4.0f;
    static final float PART_WING = 5.0f;
    static final float PART_SPEAR = 6.0f;
    static final float PART_SHARD = 7.0f;
    static final float PART_EMBER = 8.0f;
    static final float PART_AURA = 100.0f;

    private DivineRingGeometry() {
    }

    static MeshData buildEmblem() {
        MeshData mesh = new MeshData();

        // 1. Eight outer arcs: 40 degrees of arc and a 5 degree break on each 45 degree axis.
        for (int i = 0; i < 8; i++) {
            float centre = i * 45.0f;
            addArc(mesh, OUTER_RADIUS, OUTER_HALF_WIDTH,
                    centre + 2.6f, centre + 42.4f, 42,
                    PART_OUTER, seed(100 + i));
        }

        // 2. Middle ring: clean upper half, fractured lower half.
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 4.0f, 86.0f, 46, PART_MIDDLE, seed(200));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 94.0f, 176.0f, 46, PART_MIDDLE, seed(201));

        // Lower left: one long segment followed by three broken ones.
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 184.0f, 220.0f, 24, PART_MIDDLE, seed(202));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 224.0f, 242.0f, 12, PART_MIDDLE, seed(203));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 246.0f, 258.0f, 10, PART_MIDDLE, seed(204));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 262.0f, 270.0f, 7, PART_MIDDLE, seed(205));

        // Lower right: more heavily eroded, matching the reference's chipped gap.
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 274.0f, 296.0f, 15, PART_MIDDLE, seed(206));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 300.0f, 314.0f, 10, PART_MIDDLE, seed(207));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 318.0f, 334.0f, 11, PART_MIDDLE, seed(208));
        addArc(mesh, MIDDLE_RADIUS, MIDDLE_HALF_WIDTH, 338.0f, 356.0f, 12, PART_MIDDLE, seed(209));

        // 3. Complete thin inner circle.
        addArc(mesh, INNER_RADIUS, INNER_HALF_WIDTH, 0.0f, 360.0f, 160, PART_INNER, seed(300));

        // 4. Four swept blades. Their inner ends sit just outside the middle ring and
        // their tips reach 1.35x the outer radius; each blade is rotated 6.7 degrees
        // relative to the radius at its inner end, producing the reference pinwheel.
        for (int i = 0; i < 4; i++) {
            float innerAngle = 40.0f + i * 90.0f;
            float tipAngle = 46.7f + i * 90.0f;
            addSweptBlade(mesh,
                    polarX(0.685f, innerAngle), polarY(0.685f, innerAngle),
                    polarX(1.355f, tipAngle), polarY(1.355f, tipAngle),
                    0.0108f, 0.00035f, PART_BLADE, seed(400 + i));
        }

        // 5. Horizontal side wings.
        addSweptBlade(mesh, 0.420f, 0.0f, 0.790f, 0.0f,
                0.0140f, 0.00040f, PART_WING, seed(500));
        addSweptBlade(mesh, -0.420f, 0.0f, -0.790f, 0.0f,
                0.0140f, 0.00040f, PART_WING, seed(501));

        // 6. Split vertical spear: a short inner spindle and two detached outer spears.
        addVerticalSpindle(mesh, PART_SPEAR, seed(600));
        addSpearSegment(mesh, 0.465f, 0.958f, 0.0232f, 0.00070f, 14, PART_SPEAR, seed(601));
        addSpearSegment(mesh, -0.465f, -0.958f, 0.0232f, 0.00070f, 14, PART_SPEAR, seed(602));

        // 7. Floating debris around the broken lower ring. These are real triangles in
        // the same custom mesh, not particles.
        Random random = new Random(0xC0FFEEL);
        for (int i = 0; i < 14; i++) {
            float angle = 188.0f + random.nextFloat() * 158.0f;
            float radius = 0.325f + random.nextFloat() * 0.280f;
            float halfLength = 0.012f + random.nextFloat() * 0.028f;
            float halfWidth = 0.0030f + random.nextFloat() * 0.0090f;
            addDiamond(mesh, polarX(radius, angle), polarY(radius, angle),
                    halfLength, halfWidth, PART_SHARD, random.nextFloat());
        }
        // A few chips in the outer bottom gap.
        for (int i = 0; i < 4; i++) {
            float angle = 252.0f + random.nextFloat() * 38.0f;
            float radius = 0.935f + random.nextFloat() * 0.120f;
            float halfLength = 0.006f + random.nextFloat() * 0.015f;
            float halfWidth = 0.0020f + random.nextFloat() * 0.0050f;
            addDiamond(mesh, polarX(radius, angle), polarY(radius, angle),
                    halfLength, halfWidth, PART_SHARD, random.nextFloat());
        }

        return mesh;
    }

    static MeshData buildAura() {
        MeshData mesh = new MeshData();

        // A single camera-facing-ish local quad. The fragment shader turns it into a
        // soft ring halo with eight restrained spokes; there is no texture involved.
        float r = 1.58f;
        Vertex a = new Vertex(-r, -r, -0.015f, PART_AURA, 0.0f, 0.0f, 0.0f);
        Vertex b = new Vertex(r, -r, -0.015f, PART_AURA, 0.0f, 1.0f, 0.0f);
        Vertex c = new Vertex(r, r, -0.015f, PART_AURA, 0.0f, 1.0f, 1.0f);
        Vertex d = new Vertex(-r, r, -0.015f, PART_AURA, 0.0f, 0.0f, 1.0f);
        mesh.addQuad(a, b, c, d);

        // Shader-animated embers: small diamond quads distributed around the glyph.
        Random random = new Random(0x51EEDL);
        for (int i = 0; i < 30; i++) {
            float angle = random.nextFloat() * 360.0f;
            float radius = 0.50f + random.nextFloat() * 0.95f;
            float halfLength = 0.0050f + random.nextFloat() * 0.0090f;
            float halfWidth = 0.0025f + random.nextFloat() * 0.0055f;
            addDiamond(mesh, polarX(radius, angle), polarY(radius, angle),
                    halfLength, halfWidth, PART_EMBER, random.nextFloat());
        }
        return mesh;
    }

    private static void addArc(MeshData mesh, float radius, float halfWidth,
                               float startDeg, float endDeg, int segments,
                               float part, float seed) {
        float previousInX = 0, previousInY = 0, previousOutX = 0, previousOutY = 0, previousArc = 0;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float inner = radius - halfWidth;
            float outer = radius + halfWidth;
            float inX = cos * inner;
            float inY = sin * inner;
            float outX = cos * outer;
            float outY = sin * outer;

            if (i > 0) {
                Vertex in0 = new Vertex(previousInX, previousInY, 0.0f, part, -1.0f, previousArc, seed);
                Vertex out0 = new Vertex(previousOutX, previousOutY, 0.0f, part, 1.0f, previousArc, seed);
                Vertex out1 = new Vertex(outX, outY, 0.0f, part, 1.0f, t, seed);
                Vertex in1 = new Vertex(inX, inY, 0.0f, part, -1.0f, t, seed);
                mesh.addQuad(in0, out0, out1, in1);
            }
            previousInX = inX;
            previousInY = inY;
            previousOutX = outX;
            previousOutY = outY;
            previousArc = t;
        }
    }

    private static void addSweptBlade(MeshData mesh, float x0, float y0, float x1, float y1,
                                      float baseHalfWidth, float tipHalfWidth,
                                      float part, float seed) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0e-5f) {
            return;
        }
        float nx = -dy / length;
        float ny = dx / length;
        int segments = 12;

        float prevLX = 0, prevLY = 0, prevRX = 0, prevRY = 0, prevArc = 0;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float cx = x0 + dx * t;
            float cy = y0 + dy * t;
            float width = baseHalfWidth * (float) Math.pow(1.0f - t, 0.60f) + tipHalfWidth * t;
            float lx = cx + nx * width;
            float ly = cy + ny * width;
            float rx = cx - nx * width;
            float ry = cy - ny * width;

            if (i > 0) {
                Vertex l0 = new Vertex(prevLX, prevLY, 0.0f, part, -1.0f, prevArc, seed);
                Vertex r0 = new Vertex(prevRX, prevRY, 0.0f, part, 1.0f, prevArc, seed);
                Vertex r1 = new Vertex(rx, ry, 0.0f, part, 1.0f, t, seed);
                Vertex l1 = new Vertex(lx, ly, 0.0f, part, -1.0f, t, seed);
                mesh.addQuad(l0, r0, r1, l1);
            }
            prevLX = lx;
            prevLY = ly;
            prevRX = rx;
            prevRY = ry;
            prevArc = t;
        }
    }

    private static void addVerticalSpindle(MeshData mesh, float part, float seed) {
        // A slightly baroque spear head: broadest just above/below the inner circle and
        // pinched exactly at the centre so it reads as two opposed blades from behind.
        float[] ys = {-0.370f, -0.340f, -0.285f, -0.220f, -0.155f, -0.080f, 0.0f,
                0.080f, 0.155f, 0.220f, 0.285f, 0.340f, 0.370f};
        float[] ws = {0.0000f, 0.0040f, 0.0100f, 0.0200f, 0.0260f, 0.0230f, 0.0210f,
                0.0230f, 0.0260f, 0.0200f, 0.0100f, 0.0040f, 0.0000f};

        float prevLX = 0, prevLY = 0, prevRX = 0, prevRY = 0, prevArc = 0;
        for (int i = 0; i < ys.length; i++) {
            float t = i / (float) (ys.length - 1);
            float lx = -ws[i];
            float rx = ws[i];
            if (i > 0) {
                Vertex l0 = new Vertex(prevLX, prevLY, 0.0f, part, -1.0f, prevArc, seed);
                Vertex r0 = new Vertex(prevRX, prevRY, 0.0f, part, 1.0f, prevArc, seed);
                Vertex r1 = new Vertex(rx, ys[i], 0.0f, part, 1.0f, t, seed);
                Vertex l1 = new Vertex(lx, ys[i], 0.0f, part, -1.0f, t, seed);
                mesh.addQuad(l0, r0, r1, l1);
            }
            prevLX = lx;
            prevLY = ys[i];
            prevRX = rx;
            prevRY = ys[i];
            prevArc = t;
        }
    }

    private static void addSpearSegment(MeshData mesh, float y0, float y1,
                                        float baseHalfWidth, float tipHalfWidth,
                                        int segments, float part, float seed) {
        float prevLX = 0, prevLY = 0, prevRX = 0, prevRY = 0, prevArc = 0;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float y = y0 + (y1 - y0) * t;
            float width = baseHalfWidth * (float) Math.pow(1.0f - t, 0.58f) + tipHalfWidth * t;
            float lx = -width;
            float rx = width;
            if (i > 0) {
                Vertex l0 = new Vertex(prevLX, prevLY, 0.0f, part, -1.0f, prevArc, seed);
                Vertex r0 = new Vertex(prevRX, prevRY, 0.0f, part, 1.0f, prevArc, seed);
                Vertex r1 = new Vertex(rx, y, 0.0f, part, 1.0f, t, seed);
                Vertex l1 = new Vertex(lx, y, 0.0f, part, -1.0f, t, seed);
                mesh.addQuad(l0, r0, r1, l1);
            }
            prevLX = lx;
            prevLY = y;
            prevRX = rx;
            prevRY = y;
            prevArc = t;
        }
    }

    private static void addDiamond(MeshData mesh, float cx, float cy,
                                   float halfLength, float halfWidth,
                                   float part, float seed) {
        // Orient chips tangentially: tangent = (-sin, cos), normal = (-cos, -sin).
        float angle = (float) Math.atan2(cy, cx);
        float tx = -(float) Math.sin(angle);
        float ty = (float) Math.cos(angle);
        float nx = -(float) Math.cos(angle);
        float ny = -(float) Math.sin(angle);

        Vertex tipA = new Vertex(cx + tx * halfLength, cy + ty * halfLength, 0.0f, part, 0.0f, 1.0f, seed);
        Vertex sideB = new Vertex(cx + nx * halfWidth, cy + ny * halfWidth, 0.0f, part, 0.0f, 0.5f, seed);
        Vertex tipC = new Vertex(cx - tx * halfLength, cy - ty * halfLength, 0.0f, part, 0.0f, 0.0f, seed);
        Vertex sideD = new Vertex(cx - nx * halfWidth, cy - ny * halfWidth, 0.0f, part, 0.0f, 0.5f, seed);
        mesh.addQuad(tipA, sideB, tipC, sideD);
    }

    private static float polarX(float radius, float degrees) {
        return radius * (float) Math.cos(Math.toRadians(degrees));
    }

    private static float polarY(float radius, float degrees) {
        return radius * (float) Math.sin(Math.toRadians(degrees));
    }

    private static float seed(int value) {
        // Stable pseudo-random 0..1, avoids depending on Random's implementation details.
        double x = Math.sin(value * 12.9898 + 78.233) * 43758.5453;
        return (float) (x - Math.floor(x));
    }
}

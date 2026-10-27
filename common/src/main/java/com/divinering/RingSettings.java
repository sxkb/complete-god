package com.divinering;

import java.util.Objects;

/**
 * Immutable visual and anchoring settings for one divine ring.
 *
 * <p>Colours are packed 0xRRGGBB values. All sizes are in blocks (one block = one
 * Minecraft block), angles are in degrees and speeds are in degrees per second.
 * The defaults are a gold divine ring: warm gold face, dark bronze shadow and
 * amber glow.</p>
 *
 * <p>This class contains no client-only types, so it is safe to reference from a
 * common-side mod class. Rendering itself is client-only.</p>
 */
public final class RingSettings {

    public static final RingSettings DEFAULT = builder().build();

    private final int hotColor;
    private final int darkColor;
    private final int glowColor;

    private final float scale;
    private final float height;
    private final float distance;

    private final float spinSpeed;
    private final float bob;
    private final float sway;
    private final float tilt;

    private final float opacity;
    private final float aura;
    private final boolean embers;
    private final boolean faceCamera;

    private RingSettings(Builder b) {
        this.hotColor = b.hotColor;
        this.darkColor = b.darkColor;
        this.glowColor = b.glowColor;
        this.scale = b.scale;
        this.height = b.height;
        this.distance = b.distance;
        this.spinSpeed = b.spinSpeed;
        this.bob = b.bob;
        this.sway = b.sway;
        this.tilt = b.tilt;
        this.opacity = b.opacity;
        this.aura = b.aura;
        this.embers = b.embers;
        this.faceCamera = b.faceCamera;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .hotColor(hotColor)
                .darkColor(darkColor)
                .glowColor(glowColor)
                .scale(scale)
                .height(height)
                .distance(distance)
                .spinSpeed(spinSpeed)
                .bob(bob)
                .sway(sway)
                .tilt(tilt)
                .opacity(opacity)
                .aura(aura)
                .embers(embers)
                .faceCamera(faceCamera);
    }

    /** Bright inner/upper-left colour, 0xRRGGBB. */
    public int hotColor() {
        return hotColor;
    }

    /** Dark lower-right colour, 0xRRGGBB. */
    public int darkColor() {
        return darkColor;
    }

    /** Emissive rim and aura colour, 0xRRGGBB. */
    public int glowColor() {
        return glowColor;
    }

    /** Uniform scale of the whole emblem. 0.92 gives a roughly 2.5 block tall glyph. */
    public float scale() {
        return scale;
    }

    /** Centre height above the player's feet, in blocks. */
    public float height() {
        return height;
    }

    /** How far behind the player's body centre the ring floats, in blocks. */
    public float distance() {
        return distance;
    }

    /** In-plane rotation speed magnitude, degrees per second. The outer ring uses
     *  this value counter-clockwise, the middle ring clockwise, and the inner ring is
     *  stationary. A negative value reverses both rotating rings. */
    public float spinSpeed() {
        return spinSpeed;
    }

    /** Vertical float amplitude in blocks. */
    public float bob() {
        return bob;
    }

    /** Horizontal sway amplitude in blocks. */
    public float sway() {
        return sway;
    }

    /** Base tilt around the local X axis, degrees. */
    public float tilt() {
        return tilt;
    }

    /** Overall opacity multiplier, clamped to 0..1. */
    public float opacity() {
        return opacity;
    }

    /** Additive aura strength. 0 disables the aura, 1 is the reference look. */
    public float aura() {
        return aura;
    }

    /** Whether the shader-driven ember geometry is visible. */
    public boolean embers() {
        return embers;
    }

    /**
     * When true the ring always turns its face toward the camera. When false it is
     * physically attached to the player's back and can be seen edge-on if the camera
     * orbits far around the player.
     */
    public boolean faceCamera() {
        return faceCamera;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RingSettings other)) {
            return false;
        }
        return hotColor == other.hotColor
                && darkColor == other.darkColor
                && glowColor == other.glowColor
                && Float.compare(scale, other.scale) == 0
                && Float.compare(height, other.height) == 0
                && Float.compare(distance, other.distance) == 0
                && Float.compare(spinSpeed, other.spinSpeed) == 0
                && Float.compare(bob, other.bob) == 0
                && Float.compare(sway, other.sway) == 0
                && Float.compare(tilt, other.tilt) == 0
                && Float.compare(opacity, other.opacity) == 0
                && Float.compare(aura, other.aura) == 0
                && embers == other.embers
                && faceCamera == other.faceCamera;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hotColor, darkColor, glowColor, scale, height, distance,
                spinSpeed, bob, sway, tilt, opacity, aura, embers, faceCamera);
    }

    @Override
    public String toString() {
        return "RingSettings{" +
                "hot=#" + String.format("%06X", hotColor) +
                ", dark=#" + String.format("%06X", darkColor) +
                ", glow=#" + String.format("%06X", glowColor) +
                ", scale=" + scale +
                ", height=" + height +
                ", distance=" + distance +
                ", spin=" + spinSpeed +
                ", bob=" + bob +
                ", sway=" + sway +
                ", tilt=" + tilt +
                ", opacity=" + opacity +
                ", aura=" + aura +
                ", embers=" + embers +
                ", faceCamera=" + faceCamera +
                '}';
    }

    /**
     * Derives a coherent dark/bronze companion colour from the primary colour.
     * Used by the in-game colour editor so one RGB choice still produces a readable
     * two-tone emblem.
     */
    public static int deriveDark(int hotColor) {
        return adjustHsv(hotColor, 0.94f, 0.30f);
    }

    /** Derives a brighter, slightly more saturated glow companion colour. */
    public static int deriveGlow(int hotColor) {
        return adjustHsv(hotColor, 1.25f, 1.00f);
    }

    private static int adjustHsv(int rgb, float saturationMul, float valueMul) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float hue;
        if (delta < 1.0e-6f) {
            hue = 0.0f;
        } else if (max == rf) {
            hue = ((gf - bf) / delta) % 6.0f;
        } else if (max == gf) {
            hue = (bf - rf) / delta + 2.0f;
        } else {
            hue = (rf - gf) / delta + 4.0f;
        }
        hue /= 6.0f;
        if (hue < 0.0f) {
            hue += 1.0f;
        }

        float saturation = max <= 1.0e-6f ? 0.0f : delta / max;
        saturation = clamp(saturation * saturationMul, 0.0f, 1.0f);
        float value = clamp(max * valueMul, 0.0f, 1.0f);
        return hsvToRgb(hue, saturation, value);
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h6 = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) Math.floor(h6);
        float f = h6 - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }
        return (Math.round(r * 255.0f) << 16)
                | (Math.round(g * 255.0f) << 8)
                | Math.round(b * 255.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Builder {
        private int hotColor = 0xFFCC3A;
        private int darkColor = 0x4A2B08;
        private int glowColor = 0xFFB000;

        private float scale = 0.92f;
        private float height = 1.20f;
        private float distance = 0.68f;

        private float spinSpeed = 7.0f;
        private float bob = 0.035f;
        private float sway = 0.018f;
        private float tilt = 0.0f;

        private float opacity = 1.0f;
        private float aura = 0.90f;
        private boolean embers = true;
        private boolean faceCamera = false;

        private Builder() {
        }

        public Builder hotColor(int rgb) {
            this.hotColor = rgb & 0xFFFFFF;
            return this;
        }

        public Builder darkColor(int rgb) {
            this.darkColor = rgb & 0xFFFFFF;
            return this;
        }

        public Builder glowColor(int rgb) {
            this.glowColor = rgb & 0xFFFFFF;
            return this;
        }

        public Builder scale(float value) {
            this.scale = clamp(value, 0.20f, 3.0f);
            return this;
        }

        public Builder height(float value) {
            this.height = clamp(value, 0.0f, 4.0f);
            return this;
        }

        public Builder distance(float value) {
            this.distance = clamp(value, 0.0f, 3.0f);
            return this;
        }

        public Builder spinSpeed(float value) {
            this.spinSpeed = clamp(value, -180.0f, 180.0f);
            return this;
        }

        public Builder bob(float value) {
            this.bob = clamp(value, 0.0f, 0.5f);
            return this;
        }

        public Builder sway(float value) {
            this.sway = clamp(value, 0.0f, 0.5f);
            return this;
        }

        public Builder tilt(float value) {
            this.tilt = clamp(value, -45.0f, 45.0f);
            return this;
        }

        public Builder opacity(float value) {
            this.opacity = clamp(value, 0.0f, 1.0f);
            return this;
        }

        public Builder aura(float value) {
            this.aura = clamp(value, 0.0f, 2.0f);
            return this;
        }

        public Builder embers(boolean value) {
            this.embers = value;
            return this;
        }

        public Builder faceCamera(boolean value) {
            this.faceCamera = value;
            return this;
        }

        public RingSettings build() {
            return new RingSettings(this);
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}

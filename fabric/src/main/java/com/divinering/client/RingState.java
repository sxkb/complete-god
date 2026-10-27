package com.divinering.client;

import com.divinering.RingSettings;

/**
 * Mutable per-player animation state. Rendering is single-threaded, but the map is
 * concurrent because API calls can arrive from the client thread between frames.
 */
public final class RingState {

    public volatile RingSettings settings;
    public volatile boolean visible;

    /** Smoothed body yaw, in degrees. */
    public float smoothYaw;
    public boolean initialized;
    public long lastRenderNanos;

    public RingState(RingSettings settings, boolean visible) {
        this.settings = settings;
        this.visible = visible;
    }
}

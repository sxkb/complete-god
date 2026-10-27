package com.divinering.client;

import com.divinering.RingSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.bai.god.platform.Platforms;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tiny JSON client config. Deliberately independent of ForgeConfigSpec so a host mod
 * can copy the renderer without also copying/registering a config spec. The file is
 * named uniquely ({@code divinering-client.json}) and can be edited by hand.
 *
 */
public final class DivineRingConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platforms.get().configDir().resolve("divinering-client.json");

    private static boolean enabled = true;
    private static RingSettings settings = RingSettings.DEFAULT;
    private static boolean loaded;

    private DivineRingConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (Files.notExists(FILE)) {
            save();
            return;
        }
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            Data data = GSON.fromJson(json, Data.class);
            if (data == null) {
                return;
            }
            enabled = data.enabled;
            settings = RingSettings.builder()
                    .hotColor(data.hotColor)
                    .darkColor(data.darkColor)
                    .glowColor(data.glowColor)
                    .scale(data.scale)
                    .height(data.height)
                    .distance(data.distance)
                    .spinSpeed(data.rotationSpeed)
                    .bob(data.bob)
                    .sway(data.sway)
                    .tilt(data.tilt)
                    .opacity(data.opacity)
                    .aura(data.aura)
                    .embers(data.embers)
                    .faceCamera(data.faceCamera)
                    .build();
        } catch (Exception exception) {
            LOGGER.warn("[DivineRing] Could not read {}; using gold defaults.", FILE, exception);
        }
    }

    public static synchronized void save() {
        Data data = new Data();
        data.enabled = enabled;
        data.hotColor = settings.hotColor();
        data.darkColor = settings.darkColor();
        data.glowColor = settings.glowColor();
        data.scale = settings.scale();
        data.height = settings.height();
        data.distance = settings.distance();
        data.rotationSpeed = settings.spinSpeed();
        data.bob = settings.bob();
        data.sway = settings.sway();
        data.tilt = settings.tilt();
        data.opacity = settings.opacity();
        data.aura = settings.aura();
        data.embers = settings.embers();
        data.faceCamera = settings.faceCamera();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("[DivineRing] Could not save {}.", FILE, exception);
        }
    }

    public static synchronized boolean isEnabled() {
        load();
        return enabled;
    }

    public static synchronized RingSettings settings() {
        load();
        return settings;
    }

    public static synchronized void setEnabled(boolean value) {
        load();
        enabled = value;
        save();
    }

    public static synchronized void set(boolean value, RingSettings newSettings) {
        load();
        enabled = value;
        if (newSettings != null) {
            settings = newSettings;
        }
        save();
    }

    /**
     * JSON shape. {@code rotationSpeed} is the user-facing name for
     * {@link RingSettings#spinSpeed()}.
     */
    private static final class Data {
        boolean enabled = true;
        int hotColor = 0xFFCC3A;
        int darkColor = 0x4A2B08;
        int glowColor = 0xFFB000;
        float scale = 0.92f;
        float height = 1.20f;
        float distance = 0.68f;
        float rotationSpeed = 7.0f;
        float bob = 0.035f;
        float sway = 0.018f;
        float tilt = 0.0f;
        float opacity = 1.0f;
        float aura = 0.90f;
        boolean embers = true;
        boolean faceCamera = false;
    }
}

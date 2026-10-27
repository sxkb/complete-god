package com.divinering.client;

import com.divinering.DivineRing;
import com.divinering.RingSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * In-game gold-ring editor. Uses a two-column layout on normal GUI scales and a
 * three-page single-column layout when the logical screen is narrow (large GUI scale).
 */
public final class DivineRingConfigScreen extends Screen {

    private static final int WIDE_THRESHOLD = 420;
    private static final int PAGE_COUNT = 3;

    private final Screen parent;
    private int page;

    private boolean enabled;
    private float rotationSpeed;
    private float scale;
    private float ringHeight;
    private float distance;
    private float aura;
    private float opacity;
    private int hotColor;
    private boolean embers;
    private boolean faceCamera;
    private boolean draftLoaded;

    public DivineRingConfigScreen(Screen parent) {
        this(parent, 0);
    }

    private DivineRingConfigScreen(Screen parent, int page) {
        super(Component.translatable("divinering.config.title"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        if (!draftLoaded) {
            loadDraft();
            draftLoaded = true;
        }
        if (width >= WIDE_THRESHOLD) {
            initWide();
        } else {
            initNarrow();
        }
        preview();
    }

    private void loadDraft() {
        DivineRingConfig.load();
        enabled = DivineRingConfig.isEnabled();
        RingSettings settings = DivineRingConfig.settings();
        rotationSpeed = settings.spinSpeed();
        scale = settings.scale();
        ringHeight = settings.height();
        distance = settings.distance();
        aura = settings.aura();
        opacity = settings.opacity();
        hotColor = settings.hotColor();
        embers = settings.embers();
        faceCamera = settings.faceCamera();
    }

    private void initWide() {
        int center = width / 2;
        int columnWidth = 190;
        int left = center - columnWidth - 8;
        int right = center + 8;
        int row = 24;
        int top = 42;

        addRenderableWidget(rotationSlider(left, top, columnWidth));
        addRenderableWidget(scaleSlider(left, top + row, columnWidth));
        addRenderableWidget(heightSlider(left, top + row * 2, columnWidth));
        addRenderableWidget(distanceSlider(left, top + row * 3, columnWidth));
        addRenderableWidget(auraSlider(left, top + row * 4, columnWidth));

        addRenderableWidget(redSlider(right, top, columnWidth));
        addRenderableWidget(greenSlider(right, top + row, columnWidth));
        addRenderableWidget(blueSlider(right, top + row * 2, columnWidth));
        addRenderableWidget(opacitySlider(right, top + row * 3, columnWidth));

        addRenderableWidget(embersButton(right, top + row * 4, columnWidth));
        addRenderableWidget(faceCameraButton(right, top + row * 5, columnWidth));
        addRenderableWidget(enabledButton(12, height - 30, 112));

        addRenderableWidget(applyButton(center - 38, height - 30, 70));
        addRenderableWidget(doneButton(center + 36, height - 30, 70));
        addRenderableWidget(resetButton(center - 112, height - 30, 70));
    }

    private void initNarrow() {
        int x = 12;
        int controlWidth = width - 24;
        int top = 30;
        int row = 21;

        if (page == 0) {
            addRenderableWidget(rotationSlider(x, top, controlWidth));
            addRenderableWidget(scaleSlider(x, top + row, controlWidth));
            addRenderableWidget(heightSlider(x, top + row * 2, controlWidth));
            addRenderableWidget(distanceSlider(x, top + row * 3, controlWidth));
            addRenderableWidget(auraSlider(x, top + row * 4, controlWidth));
        } else if (page == 1) {
            addRenderableWidget(redSlider(x, top, controlWidth));
            addRenderableWidget(greenSlider(x, top + row, controlWidth));
            addRenderableWidget(blueSlider(x, top + row * 2, controlWidth));
            addRenderableWidget(opacitySlider(x, top + row * 3, controlWidth));
        } else {
            addRenderableWidget(enabledButton(x, top, controlWidth));
            addRenderableWidget(embersButton(x, top + row, controlWidth));
            addRenderableWidget(faceCameraButton(x, top + row * 2, controlWidth));
            addRenderableWidget(resetButton(x, top + row * 3, controlWidth));
        }

        int buttonWidth = Math.max(42, (width - 40) / 4);
        int gap = 4;
        int total = buttonWidth * 4 + gap * 3;
        int startX = (width - total) / 2;
        int y = height - 25;

        Button previous = Button.builder(Component.translatable("divinering.config.prev"),
                        button -> changePage(-1))
                .bounds(startX, y, buttonWidth, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.translatable("divinering.config.next"),
                        button -> changePage(1))
                .bounds(startX + buttonWidth + gap, y, buttonWidth, 20).build();
        next.active = page < PAGE_COUNT - 1;
        addRenderableWidget(next);

        addRenderableWidget(applyButton(startX + (buttonWidth + gap) * 2, y, buttonWidth));
        addRenderableWidget(doneButton(startX + (buttonWidth + gap) * 3, y, buttonWidth));
    }

    private void changePage(int delta) {
        int next = Mth.clamp(page + delta, 0, PAGE_COUNT - 1);
        if (next != page) {
            page = next;
            if (minecraft != null) {
                minecraft.execute(this::rebuildWidgets);
            }
        }
    }

    private AbstractSliderButton rotationSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.rotation",
                0.0f, 30.0f, rotationSpeed, 0.5f, value -> {
            rotationSpeed = (float) value;
            preview();
        });
    }

    private AbstractSliderButton scaleSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.scale",
                0.4f, 1.4f, scale, 0.01f, value -> {
            scale = (float) value;
            preview();
        });
    }

    private AbstractSliderButton heightSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.height",
                0.6f, 2.0f, ringHeight, 0.01f, value -> {
            ringHeight = (float) value;
            preview();
        });
    }

    private AbstractSliderButton distanceSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.distance",
                0.2f, 1.5f, distance, 0.01f, value -> {
            distance = (float) value;
            preview();
        });
    }

    private AbstractSliderButton auraSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.aura",
                0.0f, 1.5f, aura, 0.01f, value -> {
            aura = (float) value;
            preview();
        });
    }

    private AbstractSliderButton opacitySlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.opacity",
                0.2f, 1.0f, opacity, 0.01f, value -> {
            opacity = (float) value;
            preview();
        });
    }

    private AbstractSliderButton redSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.red",
                0.0f, 255.0f, red(), 1.0f, value -> setRed((int) Math.round(value)));
    }

    private AbstractSliderButton greenSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.green",
                0.0f, 255.0f, green(), 1.0f, value -> setGreen((int) Math.round(value)));
    }

    private AbstractSliderButton blueSlider(int x, int y, int width) {
        return new ValueSlider(x, y, width, 20, "divinering.config.blue",
                0.0f, 255.0f, blue(), 1.0f, value -> setBlue((int) Math.round(value)));
    }

    private Button enabledButton(int x, int y, int width) {
        return Button.builder(toggleLabel("divinering.config.enabled", enabled), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel("divinering.config.enabled", enabled));
            preview();
        }).bounds(x, y, width, 20).build();
    }

    private Button embersButton(int x, int y, int width) {
        return Button.builder(toggleLabel("divinering.config.embers", embers), button -> {
            embers = !embers;
            button.setMessage(toggleLabel("divinering.config.embers", embers));
            preview();
        }).bounds(x, y, width, 20).build();
    }

    private Button faceCameraButton(int x, int y, int width) {
        return Button.builder(toggleLabel("divinering.config.face_camera", faceCamera), button -> {
            faceCamera = !faceCamera;
            button.setMessage(toggleLabel("divinering.config.face_camera", faceCamera));
            preview();
        }).bounds(x, y, width, 20).build();
    }

    private Button applyButton(int x, int y, int width) {
        return Button.builder(Component.translatable("divinering.config.apply"), button -> {
            apply();
            preview();
        }).bounds(x, y, width, 20).build();
    }

    private Button doneButton(int x, int y, int width) {
        return Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x, y, width, 20).build();
    }

    private Button resetButton(int x, int y, int width) {
        return Button.builder(Component.translatable("divinering.config.reset"), button -> {
            resetDraft();
            apply();
            page = 0;
            if (minecraft != null) {
                minecraft.execute(this::rebuildWidgets);
            }
        }).bounds(x, y, width, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int center = width / 2;
        graphics.drawCenteredString(font, title, center, 9, 0xFFD36A);

        int swatchWidth = 96;
        int swatchX = center - swatchWidth / 2;
        int swatchY = 24;
        graphics.fill(swatchX, swatchY, swatchX + 32, swatchY + 5, 0xFF000000 | hotColor);
        graphics.fill(swatchX + 32, swatchY, swatchX + 64, swatchY + 5,
                0xFF000000 | RingSettings.deriveDark(hotColor));
        graphics.fill(swatchX + 64, swatchY, swatchX + swatchWidth, swatchY + 5,
                0xFF000000 | RingSettings.deriveGlow(hotColor));

        super.render(graphics, mouseX, mouseY, partialTick);

        if (width < WIDE_THRESHOLD) {
            Component pageText = Component.translatable("divinering.config.page",
                    page + 1, PAGE_COUNT);
            graphics.drawString(font, pageText, width - 8 - font.width(pageText), 10,
                    0x9A9A9A, false);
        } else {
            graphics.drawCenteredString(font, Component.translatable("divinering.config.hint"),
                    center, height - 46, 0x9A9A9A);
        }
    }

    @Override
    public void onClose() {
        apply();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void apply() {
        RingSettings built = buildSettings();
        DivineRingConfig.set(enabled, built);
        DivineRing.setDefaultSettings(built);
        DivineRingClient.applyLocal(enabled, built, true);
    }

    private void preview() {
        DivineRingClient.previewLocal(enabled, buildSettings());
    }

    private RingSettings buildSettings() {
        return RingSettings.builder()
                .hotColor(hotColor)
                .darkColor(RingSettings.deriveDark(hotColor))
                .glowColor(RingSettings.deriveGlow(hotColor))
                .scale(scale)
                .height(ringHeight)
                .distance(distance)
                .spinSpeed(rotationSpeed)
                .opacity(opacity)
                .aura(aura)
                .embers(embers)
                .faceCamera(faceCamera)
                .build();
    }

    private void resetDraft() {
        enabled = true;
        rotationSpeed = 7.0f;
        scale = 0.92f;
        ringHeight = 1.20f;
        distance = 0.68f;
        aura = 0.90f;
        opacity = 1.0f;
        hotColor = 0xFFCC3A;
        embers = true;
        faceCamera = false;
    }

    private int red() {
        return (hotColor >> 16) & 0xFF;
    }

    private int green() {
        return (hotColor >> 8) & 0xFF;
    }

    private int blue() {
        return hotColor & 0xFF;
    }

    private void setRed(int value) {
        hotColor = (hotColor & 0x00FFFF) | (Mth.clamp(value, 0, 255) << 16);
        preview();
    }

    private void setGreen(int value) {
        hotColor = (hotColor & 0xFF00FF) | (Mth.clamp(value, 0, 255) << 8);
        preview();
    }

    private void setBlue(int value) {
        hotColor = (hotColor & 0xFFFF00) | Mth.clamp(value, 0, 255);
        preview();
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key).append(": ").append(
                Component.translatable(value ? "divinering.config.on" : "divinering.config.off"));
    }

    private final class ValueSlider extends AbstractSliderButton {

        private final String translationKey;
        private final float min;
        private final float max;
        private final float step;
        private final DoubleConsumer setter;
        private boolean ready;

        ValueSlider(int x, int y, int width, int height, String translationKey,
                    float min, float max, float value, float step, DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(),
                    (Mth.clamp(value, min, max) - min) / (max - min));
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            this.ready = true;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (!ready) {
                return;
            }
            setMessage(Component.translatable(translationKey).append(": ")
                    .append(Component.literal(formatValue(currentValue()))));
        }

        @Override
        protected void applyValue() {
            float snapped = Math.round(currentValue() / step) * step;
            snapped = Mth.clamp(snapped, min, max);
            setter.accept(snapped);
            updateMessage();
        }

        private float currentValue() {
            return min + (float) value * (max - min);
        }

        private String formatValue(float value) {
            if (step >= 1.0f) {
                return Integer.toString(Math.round(value));
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }
}

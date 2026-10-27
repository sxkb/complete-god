package com.bai.god.client.render;

import net.minecraft.client.gui.GuiGraphics;
import com.bai.god.feature.GodConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GodConfigScreen extends Screen {

    /**
     * 神环（Divine Ring）配置界面打开器，由 Forge 客户端初始化时注册。
     * 为 null（如 Fabric 端）时"神环设置"行不渲染。
     */
    public static java.util.function.Consumer<Screen> ringConfigOpener;

    private static int PANEL_BG() { return GodUiConfig.getPanelBg(); }
    private static int SIDEBAR_BG() { return GodUiConfig.getSidebarBg(); }
    private static int CATEGORY_ACTIVE() { return GodUiConfig.getCategoryActive(); }
    private static int CATEGORY_HOVER() { return GodUiConfig.getCategoryHover(); }
    private static int TEXT_PRIMARY() { return GodUiConfig.getTextPrimary(); }
    private static int TEXT_SECONDARY() { return GodUiConfig.getTextSecondary(); }
    private static int CONTENT_BG() { return GodUiConfig.getContentBg(); }
    private static int ROW_BG() { return GodUiConfig.getRowBg(); }
    private static int ROW_BG_HOVER() { return GodUiConfig.getRowBgHover(); }
    private static int ACCENT() { return GodUiConfig.getAccent(); }
    private static int BTN_SAVE_BG() { return GodUiConfig.getBtnSaveBg(); }
    private static int BTN_SAVE_HOVER() { return GodUiConfig.getBtnSaveHover(); }
    private static int BTN_TEXT() { return GodUiConfig.getBtnText(); }
    private static int CARD_BG() { return GodUiConfig.getCardBg(); }
    private static int CARD_BORDER() { return GodUiConfig.getCardBorder(); }

    private static int scrollBarColor() {
        int accent = ACCENT();
        return (accent & 0x00FFFFFF) | 0x80000000;
    }

    private static final int CATEGORY_NAME = 0;

    private static final int MODE_BTN_MIN_W = 80;
    private static final int MODE_BTN_H = 16;

    private final Screen parent;

    private final List<String> categories = new ArrayList<>();
    private int selectedCategory = CATEGORY_NAME;

    private int panelMargin;
    private int titleHeight;
    private int sidebarWidth;
    private int rowHeight;
    private int rowSpacing;

    private int panelX, panelY, panelW, panelH;
    private int sidebarX, sidebarY, sidebarH;
    private int contentX, contentY, contentW, contentH;

    private int configScrollOffset = 0;
    private int maxConfigScroll = 0;
    private int sidebarScrollOffset = 0;
    private int hoveredTooltipRow = -1;
    private int contentBottomMargin = 0;
    private boolean customizeHovered = false;
    private boolean openCustomizeHovered = false;
    private GodUiConfig.UIStyle lastStyle;

    private int customizeBtnY = -1;
    private int customizeBtnH = 22;

    private final List<Row> rows = new ArrayList<>();

    private boolean rebuildRequested = false;

    private boolean draggingScrollbar = false;
    private boolean draggingSidebar = false;

    private enum WidgetType {
        TOGGLE,
        FLOAT_INPUT,
        MODE_SELECTOR
    }

    private static final class Row {
        final String label;
        final String tooltip;
        final WidgetType type;

        BooleanSupplier boolGetter;
        Consumer<Boolean> boolSetter;
        boolean boolValue;

        float floatValue;
        Consumer<Float> floatCallback;

        Supplier<String> modeName;
        Runnable modeCallback;

        GodToggleSwitchWidget toggleWidget;
        GodRoundedTextFieldWidget textField;
        int y;

        private Row(String label, String tooltip, WidgetType type) {
            this.label = label;
            this.tooltip = tooltip;
            this.type = type;
            this.boolGetter = null;
            this.boolSetter = null;
            this.modeName = null;
            this.modeCallback = null;
        }

        static Row toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
            return toggle(label, null, getter, setter);
        }

        static Row toggle(String label, String tooltip, BooleanSupplier getter, Consumer<Boolean> setter) {
            Row row = new Row(label, tooltip, WidgetType.TOGGLE);
            row.boolGetter = getter;
            row.boolSetter = setter;
            row.boolValue = getter.getAsBoolean();
            return row;
        }

        static Row floatInput(String label, String tooltip, float initial, Consumer<Float> callback) {
            Row row = new Row(label, tooltip, WidgetType.FLOAT_INPUT);
            row.floatValue = initial;
            row.floatCallback = callback;
            return row;
        }

        static Row modeSelector(String label, String tooltip, Supplier<String> modeName, Runnable callback) {
            Row row = new Row(label, tooltip, WidgetType.MODE_SELECTOR);
            row.modeName = modeName;
            row.modeCallback = callback;
            return row;
        }
    }

    public GodConfigScreen(Screen parent) {
        super(Component.literal(GodUiConfig.getTitleText()));
        this.parent = parent;

        categories.add("成神配置");
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        rows.clear();
        draggingScrollbar = false;
        draggingSidebar = false;

        panelMargin = Math.max(6, Math.min(25, this.width / 30));
        titleHeight = Math.max(16, Math.min(30, this.height / 20));
        sidebarWidth = Math.max(70, Math.min(110, this.width / 10));

        GodUiConfig.UIStyle style = GodUiConfig.getUIStyle();
        if (style == GodUiConfig.UIStyle.WINDOWS) {
            titleHeight = 0;
        } else if (style == GodUiConfig.UIStyle.MACOS) {
            sidebarWidth = Math.max(90, Math.min(140, this.width / 8));
        } else if (style == GodUiConfig.UIStyle.LINUX) {
            titleHeight = 0;
            sidebarWidth = 0;
        }

        configScrollOffset = 0;
        sidebarScrollOffset = 0;

        panelX = panelMargin;
        panelY = panelMargin + titleHeight;
        panelW = this.width - panelMargin * 2;
        panelH = this.height - panelY - panelMargin;

        int titleBarH = 24;

        sidebarX = panelX + 8;
        sidebarY = panelY + 8 + titleBarH;
        sidebarH = panelH - 16 - titleBarH;

        contentX = sidebarX + sidebarWidth + 12;
        contentY = sidebarY;
        contentW = panelX + panelW - contentX - 8;
        contentH = sidebarH;

        if (style == GodUiConfig.UIStyle.LINUX) {
            int tabBarSpace = 36;
            contentY += tabBarSpace;
            contentH -= tabBarSpace;
        }

        rowHeight = Math.max(22, Math.min(36, (contentH - 36) / 8));
        rowSpacing = Math.max(2, Math.min(4, rowHeight / 9));

        contentBottomMargin = 0;

        buildRows();
        buildWidgets();

        lastStyle = style;
    }

    private void buildRows() {
        rows.clear();

        buildGodConfigRows();
    }

    private void buildGodConfigRows() {
        rows.add(Row.toggle("秒挖方块", GodConfig::isInstantMiningEnabled, GodConfig::setInstantMiningEnabled));

        rows.add(Row.toggle("进服自动成神", GodConfig::isGodModeAutoOnJoinEnabled, GodConfig::setGodModeAutoOnJoinEnabled));
        rows.add(Row.toggle("允许飞行", GodConfig::isAllowFlightEnabled, GodConfig::setAllowFlightEnabled));
        rows.add(Row.toggle("无敌", GodConfig::isInvulnerableEnabled, GodConfig::setInvulnerableEnabled));
        rows.add(Row.toggle("防饥饿", GodConfig::isAntiHungerEnabled, GodConfig::setAntiHungerEnabled));
        rows.add(Row.toggle("防负面效果", GodConfig::isAntiNegativeEffectsEnabled, GodConfig::setAntiNegativeEffectsEnabled));
        rows.add(Row.toggle("减伤", GodConfig::isDamageReductionEnabled, GodConfig::setDamageReductionEnabled));
        rows.add(Row.toggle("攻击加成", GodConfig::isAttackBoostEnabled, GodConfig::setAttackBoostEnabled));
        rows.add(Row.toggle("自动回血", GodConfig::isAutoHealEnabled, GodConfig::setAutoHealEnabled));
        rows.add(Row.toggle("挖掘加速", GodConfig::isMiningSpeedBoostEnabled, GodConfig::setMiningSpeedBoostEnabled));
        rows.add(Row.toggle("幸运掉落", GodConfig::isTripleDropEnabled, GodConfig::setTripleDropEnabled));
        rows.add(Row.toggle("默认4级权限", GodConfig::isAlwaysPermissionLevel4Enabled, GodConfig::setAlwaysPermissionLevel4Enabled));

        // 神环（Divine Ring）：平时显隐由本界面开关决定；/god on 强制显现，/god off 恢复平时选择。
        if (ringConfigOpener != null) {
            rows.add(Row.modeSelector("神环设置", "打开神环外观配置（颜色/旋转/大小/显隐）。平时是否显现由本界面开关决定；/god on 开启超越一切状态后强制显现，/god off 恢复平时选择。",
                    () -> "打开配置", () -> ringConfigOpener.accept(this)));
        }
    }

    private void buildWidgets() {
        int rowStartY = contentY + 36;
        int totalRowsHeight = rows.size() * (rowHeight + rowSpacing);
        int visibleHeight = contentH - 36 - contentBottomMargin;
        maxConfigScroll = Math.max(0, totalRowsHeight - visibleHeight);
        if (configScrollOffset > maxConfigScroll) configScrollOffset = maxConfigScroll;
        if (configScrollOffset < 0) configScrollOffset = 0;

        int rowTop = rowStartY - configScrollOffset;
        for (Row row : rows) {
            row.y = rowTop;
            boolean visible = rowTop + rowHeight > contentY + 36
                    && rowTop < contentY + contentH - contentBottomMargin;
            if (visible) {
                int centerY = rowTop + rowHeight / 2;
                if (row.type == WidgetType.TOGGLE) {
                    row.toggleWidget = new GodToggleSwitchWidget(
                            contentX + contentW - 50, centerY - 7, row.boolGetter.getAsBoolean(),
                            v -> {
                                row.boolValue = v;
                                row.boolSetter.accept(v);
                                GodConfig.save();
                            });
                    this.addRenderableWidget(row.toggleWidget);
                } else if (row.type == WidgetType.FLOAT_INPUT) {
                    row.textField = new GodRoundedTextFieldWidget(
                            this.font, contentX + contentW - 92, centerY - 11, 76, 22,
                            Component.literal(row.label));
                    row.textField.setValue(String.valueOf(row.floatValue));
                    row.textField.setResponder(text -> {
                        try {
                            row.floatValue = Float.parseFloat(text.trim());
                            if (row.floatCallback != null) {
                                row.floatCallback.accept(row.floatValue);
                            }
                        } catch (NumberFormatException ignored) {

                        }
                    });
                    this.addRenderableWidget(row.textField);
                }

            }
            rowTop += rowHeight + rowSpacing;
        }
    }

    private void deferredRebuild() {
        if (!rebuildRequested) {
            return;
        }
        rebuildRequested = false;
        this.clearWidgets();
        for (Row row : rows) {
            row.toggleWidget = null;
            row.textField = null;
        }
        buildWidgets();
    }

    private void requestRebuild() {
        rebuildRequested = true;
    }

    private void openCustomize() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new GodCustomizeScreen(this));
        }
    }

    @Override
    public void render(GuiGraphics pose, int mouseX, int mouseY, float delta) {
        this.renderBackground(pose);

        GodUiConfig.UIStyle style = GodUiConfig.getUIStyle();
        if (lastStyle != null && lastStyle != style) {

            init(this.minecraft, this.width, this.height);
        }
        lastStyle = style;

        deferredRebuild();

        String titleText = GodUiConfig.getTitleText();

        int panelRadius = style == GodUiConfig.UIStyle.WINDOWS ? 20 : 16;
        GodRoundedRender.drawRoundedRect(pose, panelX, panelY, panelW, panelH, panelRadius, PANEL_BG());

        int titleBarH = 24;
        int titleTextY = panelY + (titleBarH - GodDraw.lineHeight(this.font)) / 2;
        if (style == GodUiConfig.UIStyle.MACOS) {
            GodDraw.text(pose, this.font, titleText,
                    panelX + (panelW - GodDraw.textWidth(this.font, titleText)) / 2, titleTextY, TEXT_PRIMARY());
        } else {
            GodDraw.text(pose, this.font, titleText, panelX + 12, titleTextY, TEXT_PRIMARY());
        }

        GodDraw.fill(pose, panelX + 12, panelY + titleBarH, panelX + panelW - 12, panelY + titleBarH + 1, 0x40FFFFFF);

        if (style == GodUiConfig.UIStyle.LINUX) {
            renderLinuxTopBar(pose, mouseX, mouseY);
        } else {
            int sidebarRadius = style == GodUiConfig.UIStyle.MACOS ? 14 : 12;
            GodRoundedRender.drawRoundedRect(pose, sidebarX, sidebarY, sidebarWidth, sidebarH,
                    sidebarRadius, SIDEBAR_BG());
            renderSidebar(pose, mouseX, mouseY);
        }

        renderContent(pose, mouseX, mouseY);

        renderRows(pose, mouseX, mouseY);

        int clipBottom = contentY + contentH - contentBottomMargin;
        GodDraw.enableScissor(pose, contentX, contentY + 36, contentX + contentW, clipBottom);
        super.render(pose, mouseX, mouseY, delta);
        GodDraw.disableScissor(pose);

        renderTooltip(pose, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics pose, int mouseX, int mouseY) {
        int catHeight = 26;
        int sidebarTop = sidebarY + 6;
        int sepY = sidebarY + sidebarH - 34;
        int availableH = sepY - sidebarTop;
        int totalH = categories.size() * catHeight;
        int maxSidebarScroll = Math.max(0, totalH - availableH + 4);
        if (sidebarScrollOffset > maxSidebarScroll) sidebarScrollOffset = maxSidebarScroll;
        if (sidebarScrollOffset < 0) sidebarScrollOffset = 0;

        GodDraw.enableScissor(pose, sidebarX, sidebarTop, sidebarX + sidebarWidth, sidebarTop + availableH);
        int y = sidebarTop - sidebarScrollOffset;
        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            boolean active = i == selectedCategory;

            boolean hovered = mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
                    && mouseY >= y && mouseY <= y + 22;

            int bgColor = active ? CATEGORY_ACTIVE() : (hovered ? CATEGORY_HOVER() : 0x00000000);
            if (bgColor != 0) {
                GodRoundedRender.drawRoundedRect(pose, sidebarX + 6, y + 1, sidebarWidth - 12, 20, 8, bgColor);
            }

            if (active) {
                GodDraw.fill(pose, sidebarX + 6, y + 1, sidebarX + 9, y + 21, ACCENT());
            }

            int textColor = active ? BTN_TEXT() : (hovered ? TEXT_PRIMARY() : TEXT_SECONDARY());
            GodDraw.text(pose, this.font, cat, sidebarX + 16, y + 6, textColor);

            y += catHeight;
        }
        GodDraw.disableScissor(pose);

        GodDraw.fill(pose, sidebarX + 12, sepY, sidebarX + sidebarWidth - 12, sepY + 1, 0x80D0D0D0);

        int btnY = sepY + 8;
        customizeBtnY = btnY;
        customizeHovered = mouseX >= sidebarX + 8 && mouseX <= sidebarX + sidebarWidth - 8
                && mouseY >= btnY && mouseY <= btnY + customizeBtnH;
        int btnBg = customizeHovered ? CATEGORY_HOVER() : 0x00000000;
        if (btnBg != 0) {
            GodRoundedRender.drawRoundedRect(pose, sidebarX + 6, btnY, sidebarWidth - 12, customizeBtnH, 8, btnBg);
        }
        GodDraw.text(pose, this.font, "自定义",
                sidebarX + (sidebarWidth - GodDraw.textWidth(this.font, "自定义")) / 2, btnY + 6,
                customizeHovered ? TEXT_PRIMARY() : TEXT_SECONDARY());
    }

    private void renderLinuxTopBar(GuiGraphics pose, int mouseX, int mouseY) {
        int tabBarH = 32;
        int tabBarY = panelY + 8 + 24;
        int tabBarX = panelX + 8;
        int tabBarW = panelW - 16;

        GodRoundedRender.drawRoundedRect(pose, tabBarX, tabBarY, tabBarW, tabBarH, 10, SIDEBAR_BG());

        int tabCount = categories.size();
        int tabW = Math.min(140, (tabBarW - 16) / tabCount);
        int tabX = tabBarX + 8;

        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            boolean active = i == selectedCategory;
            boolean hovered = mouseX >= tabX && mouseX <= tabX + tabW
                    && mouseY >= tabBarY + 2 && mouseY <= tabBarY + tabBarH - 2;

            int bgColor = active ? CATEGORY_ACTIVE() : (hovered ? CATEGORY_HOVER() : 0x00000000);
            if (bgColor != 0) {
                GodRoundedRender.drawRoundedRect(pose, tabX + 2, tabBarY + 4, tabW - 4, tabBarH - 8, 8, bgColor);
            }

            if (active) {
                GodDraw.fill(pose, tabX + 4, tabBarY + tabBarH - 5, tabX + tabW - 4, tabBarY + tabBarH - 3, ACCENT());
            }

            int textColor = active ? BTN_TEXT() : (hovered ? TEXT_PRIMARY() : TEXT_SECONDARY());
            int catW = GodDraw.textWidth(this.font, cat);
            GodDraw.text(pose, this.font, cat, tabX + (tabW - catW) / 2, tabBarY + 9, textColor);

            tabX += tabW;
        }

        int customBtnX = tabBarX + tabBarW - 60;
        int customBtnW = 48;
        boolean customHovered = mouseX >= customBtnX && mouseX <= customBtnX + customBtnW
                && mouseY >= tabBarY + 4 && mouseY <= tabBarY + tabBarH - 4;
        int customBg = customHovered ? CATEGORY_HOVER() : 0x00000000;
        if (customBg != 0) {
            GodRoundedRender.drawRoundedRect(pose, customBtnX, tabBarY + 4, customBtnW, tabBarH - 8, 8, customBg);
        }
        GodDraw.text(pose, this.font, "自定义", customBtnX + 6, tabBarY + 9,
                customHovered ? TEXT_PRIMARY() : TEXT_SECONDARY());

        customizeHovered = customHovered;
        customizeBtnY = -1;
    }

    private void renderContent(GuiGraphics pose, int mouseX, int mouseY) {

        GodRoundedRender.drawRoundedRect(pose, contentX, contentY, contentW, contentH, 12, CONTENT_BG());

        String header = categories.get(selectedCategory);
        GodDraw.text(pose, this.font, header, contentX + 16, contentY + 14, TEXT_SECONDARY());
    }

    private void renderRows(GuiGraphics pose, int mouseX, int mouseY) {
        GodDraw.enableScissor(pose, contentX, contentY + 36, contentX + contentW, contentY + contentH);

        int rowStartY = contentY + 36;
        int rowTop = rowStartY - configScrollOffset;
        hoveredTooltipRow = -1;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (rowTop + rowHeight < contentY + 36 || rowTop > contentY + contentH - contentBottomMargin) {
                rowTop += rowHeight + rowSpacing;
                continue;
            }

            boolean hovered = mouseX >= contentX + 12 && mouseX <= contentX + contentW - 12
                    && mouseY >= rowTop && mouseY <= rowTop + rowHeight;

            int rowBg = hovered ? ROW_BG_HOVER() : ROW_BG();
            GodRoundedRender.drawRoundedRect(pose, contentX + 12, rowTop, contentW - 24, rowHeight, 8, rowBg);

            int centerY = rowTop + rowHeight / 2;
            int maxLabelW = contentW - 86;
            String label = GodDraw.textWidth(this.font, row.label) > maxLabelW
                    ? GodDraw.trim(this.font, row.label, maxLabelW) : row.label;
            GodDraw.text(pose, this.font, label, contentX + 24, centerY - 4, TEXT_PRIMARY());

            if (row.tooltip != null) {
                int labelW = GodDraw.textWidth(this.font, label);
                int helpX = contentX + 24 + labelW + 4;
                int helpY = centerY - 6;
                int helpSize = 12;
                boolean helpHovered = mouseX >= helpX && mouseX <= helpX + helpSize
                        && mouseY >= helpY && mouseY <= helpY + helpSize;
                if (helpHovered) {
                    hoveredTooltipRow = i;
                }
                int helpColor = helpHovered ? ACCENT() : TEXT_SECONDARY();
                GodRoundedRender.drawRoundedRect(pose, helpX, helpY, helpSize, helpSize, helpSize / 2, helpColor);
                GodDraw.text(pose, this.font, "?", helpX + 3, helpY + 2, BTN_TEXT());
            }

            if (row.type == WidgetType.MODE_SELECTOR && row.modeName != null) {
                String modeText = row.modeName.get();
                int modeBtnW = modeBtnW(modeText);
                int modeBtnX = modeBtnX(modeBtnW);
                int modeBtnY = modeBtnY(rowTop);
                boolean modeHovered = mouseX >= modeBtnX && mouseX <= modeBtnX + modeBtnW
                        && mouseY >= modeBtnY && mouseY <= modeBtnY + MODE_BTN_H;
                GodRoundedRender.drawRoundedRect(pose, modeBtnX, modeBtnY, modeBtnW, MODE_BTN_H, 6,
                        modeHovered ? BTN_SAVE_HOVER() : BTN_SAVE_BG());
                GodDraw.text(pose, this.font, modeText,
                        modeBtnX + (modeBtnW - GodDraw.textWidth(this.font, modeText)) / 2, modeBtnY + 4, BTN_TEXT());
            }

            rowTop += rowHeight + rowSpacing;
        }

        GodDraw.disableScissor(pose);

        if (maxConfigScroll > 0) {
            int scrollBarX = contentX + contentW - 6;
            int scrollBarH = Math.max(20,
                    (int) ((float) contentH / (contentH + maxConfigScroll) * contentH));
            int scrollBarY = contentY + 36
                    + (int) ((float) configScrollOffset / maxConfigScroll * (contentH - 36 - scrollBarH));
            GodRoundedRender.drawRoundedRect(pose, scrollBarX, scrollBarY, 4, scrollBarH, 2, scrollBarColor());
        }
    }

    private void renderTooltip(GuiGraphics pose, int mouseX, int mouseY) {
        if (hoveredTooltipRow < 0 || hoveredTooltipRow >= rows.size()) return;
        String tooltip = rows.get(hoveredTooltipRow).tooltip;
        if (tooltip == null) return;

        String[] lines = tooltipSplit(tooltip);
        int padding = 6;
        int lineHeight = 10;
        int maxLineW = 0;
        for (String line : lines) {
            int w = GodDraw.textWidth(this.font, line);
            if (w > maxLineW) maxLineW = w;
        }
        int boxW = maxLineW + padding * 2;
        int boxH = lines.length * lineHeight + padding * 2;

        int boxX = mouseX + 12;
        int boxY = mouseY - boxH - 4;
        if (boxX + boxW > this.width) boxX = mouseX - boxW - 12;
        if (boxY < 0) boxY = mouseY + 12;

        GodRoundedRender.drawRoundedRect(pose, boxX, boxY, boxW, boxH, 6, CARD_BG());
        GodRoundedRender.drawRoundedRectOutline(pose, boxX, boxY, boxW, boxH, 6, CARD_BORDER(), 1);

        for (int i = 0; i < lines.length; i++) {
            GodDraw.text(pose, this.font, lines[i], boxX + padding, boxY + padding + i * lineHeight, TEXT_PRIMARY());
        }
    }

    private String[] tooltipSplit(String text) {
        int maxChars = 28;
        if (GodDraw.textWidth(this.font, text) <= maxChars * 8) return new String[]{text};
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                while (end > start && text.charAt(end) != '，' && text.charAt(end) != '。'
                        && text.charAt(end) != '、' && text.charAt(end) != ' ') {
                    end--;
                }
                if (end == start) end = Math.min(start + maxChars, text.length());
            }
            result.add(text.substring(start, end));
            start = end;
            while (start < text.length() && (text.charAt(start) == '，' || text.charAt(start) == '。'
                    || text.charAt(start) == '、' || text.charAt(start) == ' ')) {
                start++;
            }
        }
        return result.toArray(new String[0]);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GodUiConfig.UIStyle style = GodUiConfig.getUIStyle();

            if (style == GodUiConfig.UIStyle.LINUX) {
                if (handleLinuxTopBarClick(mouseX, mouseY)) return true;
            } else {

                if (customizeBtnY >= 0
                        && mouseX >= sidebarX + 8 && mouseX <= sidebarX + sidebarWidth - 8
                        && mouseY >= customizeBtnY && mouseY <= customizeBtnY + customizeBtnH) {
                    openCustomize();
                    return true;
                }

                int y = sidebarY + 6 - sidebarScrollOffset;
                for (int i = 0; i < categories.size(); i++) {
                    if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
                            && mouseY >= y && mouseY <= y + 22) {
                        selectCategory(i);
                        return true;
                    }
                    y += 26;
                }
            }

            if (handleRowClick(mouseX, mouseY)) return true;
            if (beginScrollbarDrag(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectCategory(int index) {
        if (index == selectedCategory) return;
        selectedCategory = index;
        configScrollOffset = 0;
        buildRows();
        requestRebuild();
    }

    private boolean handleLinuxTopBarClick(double mouseX, double mouseY) {
        int tabBarH = 32;
        int tabBarY = panelY + 8 + 24;
        int tabBarX = panelX + 8;
        int tabBarW = panelW - 16;

        int customBtnX = tabBarX + tabBarW - 60;
        if (mouseX >= customBtnX && mouseX <= customBtnX + 48
                && mouseY >= tabBarY + 4 && mouseY <= tabBarY + tabBarH - 4) {
            openCustomize();
            return true;
        }

        int tabCount = categories.size();
        int tabW = Math.min(140, (tabBarW - 16) / tabCount);
        int tabX = tabBarX + 8;
        for (int i = 0; i < categories.size(); i++) {
            if (mouseX >= tabX && mouseX <= tabX + tabW
                    && mouseY >= tabBarY + 2 && mouseY <= tabBarY + tabBarH - 2) {
                selectCategory(i);
                return true;
            }
            tabX += tabW;
        }
        return false;
    }

    private boolean handleRowClick(double mouseX, double mouseY) {
        if (mouseX < contentX || mouseX > contentX + contentW) return false;
        if (mouseY < contentY + 36 || mouseY > contentY + contentH - contentBottomMargin) return false;

        int rowTop = contentY + 36 - configScrollOffset;
        for (Row row : rows) {
            if (rowTop + rowHeight < contentY + 36 || rowTop > contentY + contentH - contentBottomMargin) {
                rowTop += rowHeight + rowSpacing;
                continue;
            }
            if (row.type == WidgetType.MODE_SELECTOR) {
                String modeText = row.modeName != null ? row.modeName.get() : "";
                int modeBtnW = modeBtnW(modeText);
                int modeBtnX = modeBtnX(modeBtnW);
                int modeBtnY = modeBtnY(rowTop);
                if (mouseX >= modeBtnX && mouseX <= modeBtnX + modeBtnW
                        && mouseY >= modeBtnY && mouseY <= modeBtnY + MODE_BTN_H) {
                    if (row.modeCallback != null) {
                        row.modeCallback.run();
                    }
                    return true;
                }
            }
            rowTop += rowHeight + rowSpacing;
        }
        return false;
    }

    private int modeBtnW(String modeText) {
        return Math.max(MODE_BTN_MIN_W, GodDraw.textWidth(this.font, modeText) + 20);
    }

    private int modeBtnX(int modeBtnW) {
        return contentX + contentW - modeBtnW - 20;
    }

    private int modeBtnY(int rowTop) {
        return rowTop + rowHeight / 2 - MODE_BTN_H / 2;
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        if (maxConfigScroll <= 0) return false;
        if (mouseX >= contentX + contentW - 10 && mouseX <= contentX + contentW
                && mouseY >= contentY + 36 && mouseY <= contentY + contentH - contentBottomMargin) {
            draggingScrollbar = true;
            applyScrollbarDrag(mouseY);
            return true;
        }
        return false;
    }

    private void applyScrollbarDrag(double mouseY) {
        int trackTop = contentY + 36;
        int trackHeight = contentY + contentH - contentBottomMargin - trackTop;
        if (trackHeight <= 0) return;
        float ratio = (float) ((mouseY - trackTop) / trackHeight);
        configScrollOffset = (int) (Math.max(0.0f, Math.min(1.0f, ratio)) * maxConfigScroll);
        requestRebuild();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            applyScrollbarDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        draggingSidebar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        GodUiConfig.UIStyle style = GodUiConfig.getUIStyle();

        if (style != GodUiConfig.UIStyle.LINUX) {
            if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarWidth
                    && mouseY >= sidebarY + 6 && mouseY <= sidebarY + sidebarH - 34) {
                int catHeight = 26;
                int sidebarTop = sidebarY + 6;
                int sepY = sidebarY + sidebarH - 34;
                int availableH = sepY - sidebarTop;
                int totalH = categories.size() * catHeight;
                int maxSidebarScroll = Math.max(0, totalH - availableH + 4);
                sidebarScrollOffset -= (int) amount * 20;
                if (sidebarScrollOffset > maxSidebarScroll) sidebarScrollOffset = maxSidebarScroll;
                if (sidebarScrollOffset < 0) sidebarScrollOffset = 0;
                return true;
            }
        }

        if (mouseX >= contentX && mouseX <= contentX + contentW
                && mouseY >= contentY + 36 && mouseY <= contentY + contentH - contentBottomMargin) {
            int next = configScrollOffset - (int) amount * 20;
            if (next > maxConfigScroll) next = maxConfigScroll;
            if (next < 0) next = 0;
            if (next != configScrollOffset) {
                configScrollOffset = next;
                requestRebuild();
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        GodConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    public String currentCategory() {
        return categories.get(selectedCategory);
    }

    public int rowCount() {
        return rows.size();
    }

    public boolean isRowVisible(int index) {
        if (index < 0 || index >= rows.size()) return false;
        int y = rows.get(index).y;
        return y + rowHeight > contentY + 36 && y < contentY + contentH - contentBottomMargin;
    }
}

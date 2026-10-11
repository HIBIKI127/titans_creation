package com.example.titans_creation.client;

import java.util.List;

import com.example.titans_creation.giantmonster.GiantMonsterDefinition;
import com.example.titans_creation.giantmonster.GiantMonsterEntry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 三级菜单基类：词条列表编辑（弱点配置 / 抗性配置）。
 *
 * <p>以 JSON 数组文本编辑，格式与配置文件完全一致，便于与直接编辑文件互通：
 * {@code [{"entry":"slash","damage_multiplier":1.5,"part":"head","description":"..."}]}
 */
public abstract class GiantMonsterEntryListScreen extends GiantMonsterScreen {

    /** 配置字段名（用于错误提示）。 */
    private final String fieldName;

    private EditBox editor;

    protected GiantMonsterEntryListScreen(String monsterId, String titleKey, String fieldName) {
        super(Component.translatable(titleKey), monsterId);
        this.fieldName = fieldName;
    }

    /** 读取该菜单负责的词条列表。 */
    protected abstract List<GiantMonsterEntry> readEntries(GiantMonsterDefinition definition);

    /** 写回该菜单负责的词条列表。 */
    protected abstract void writeEntries(GiantMonsterDefinition definition, List<GiantMonsterEntry> entries);

    @Override
    protected void init() {
        final int centerX = this.centerX();

        this.editor = this.addScrollable(new EditBox(this.font, centerX - 200, 50, 400, 18,
                Component.translatable(fieldKey(this.fieldName))));
        this.editor.setMaxLength(4096);

        // ===== 固定在底部的操作按钮 =====
        final int buttonY = this.height - 64;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.save"), button -> this.save())
                .bounds(centerX - 150, buttonY, 145, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.reset"), button -> this.resetToDefault())
                .bounds(centerX + 5, buttonY, 145, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("titans_creation.giantmonster.button.back"),
                        button -> this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId)))
                .bounds(centerX - 150, buttonY + 22, 300, 18)
                .build());

        this.contentBottom = this.height - 94;
        this.finishScrollLayout(34, this.height - 94);
        this.loadFromDefinition();
    }

    private void loadFromDefinition() {
        GiantMonsterDefinition definition = this.definition();
        if (definition == null || this.editor == null) {
            return;
        }
        this.editor.setValue(entriesToJson(this.readEntries(definition)));
    }

    private void save() {
        GiantMonsterDefinition current = this.definition();
        if (current == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.empty"), COLOR_ERROR);
            return;
        }
        GiantMonsterDefinition definition = current.copy();
        try {
            this.writeEntries(definition, parseEntries(this.editor == null ? "" : this.editor.getValue(),
                    this.fieldName));
        } catch (RuntimeException e) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.invalid",
                    e.getMessage() == null ? "?" : e.getMessage()), COLOR_ERROR);
            return;
        }
        if (!this.saveDefinition(definition)) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.saved"), COLOR_OK);
    }

    private void resetToDefault() {
        if (this.monsterId == null) {
            return;
        }
        if (com.example.titans_creation.giantmonster.GiantMonsterConfigManager.resetToDefault(this.monsterId) == null) {
            this.setStatus(Component.translatable("titans_creation.giantmonster.status.save_failed"), COLOR_ERROR);
            return;
        }
        this.loadFromDefinition();
        this.applyToLoadedEntities();
        this.setStatus(Component.translatable("titans_creation.giantmonster.status.reset"), COLOR_OK);
    }

    @Override
    public void onClose() {
        this.openScreen(new GiantMonsterBaseAttributesScreen(this.monsterId));
    }

    /** 随内容一起滚动的标签与说明。 */
    @Override
    protected void renderScrollContent(GuiGraphics guiGraphics) {
        if (this.editor != null) {
            guiGraphics.drawString(this.font, Component.translatable(fieldKey(this.fieldName)),
                    this.editor.getX(), this.editor.getY() - 11, COLOR_LABEL, false);
        }
        int y = 90 - this.scrollOffset();
        for (Component hint : List.of(
                Component.translatable("titans_creation.giantmonster.hint.entries"),
                Component.translatable("titans_creation.giantmonster.hint.format"),
                Component.translatable("titans_creation.giantmonster.hint.file"))) {
            guiGraphics.drawCenteredString(this.font, hint, this.centerX(), y, COLOR_HINT);
            y += 11;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        GiantMonsterDefinition definition = this.definition();
        this.renderTitle(guiGraphics, definition == null ? null
                : Component.literal(definition.getDisplayName() + "  (" + definition.getId() + ")"));
        this.renderWidgets(guiGraphics, mouseX, mouseY, partialTick);
        this.renderStatus(guiGraphics);
    }
}
